// Data service: owns config, active storage backend, and in-memory state.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { LocalStore, SEED_BODYWEIGHTS } = require('./stores/local-store');
const { SheetsStore } = require('./stores/sheets-store');
const { parseHistory } = require('./history-parser');
const DEFAULT_EXERCISES = require('../data/exercises.json');

class DataService {
  constructor(userDataDir) {
    this.dir = userDataDir;
    this.configFile = path.join(userDataDir, 'config.json');
    this.local = new LocalStore(userDataDir);
    this.store = this.local;
    this.cache = {
      workouts: [],
      sets: [],
      exercises: [],
      bodyWeights: SEED_BODYWEIGHTS,
      goals: [],
      programs: [],
      activeProgramId: null,
      draft: null,
    };
    this.lastError = null;
  }

  readConfig() {
    try { return JSON.parse(fs.readFileSync(this.configFile, 'utf8')); } catch { return {}; }
  }

  writeConfig(cfg) {
    fs.mkdirSync(this.dir, { recursive: true });
    fs.writeFileSync(this.configFile, JSON.stringify(cfg, null, 2));
  }

  async init() {
    await this.local.init();
    const cfg = this.readConfig();
    if (cfg.spreadsheetId && cfg.keyFile) {
      try {
        await this._connectSheets(cfg);
      } catch (err) {
        this.lastError = `Could not connect to Google Sheets: ${err.message}. Using local storage.`;
        this.store = this.local;
      }
    }
    await this.reload();
  }

  async _connectSheets(cfg) {
    const store = new SheetsStore(cfg);
    await store.init();
    this.store = store;
  }

  async reload() {
    const storeData = await this.store.loadAll();
    const localData = await this.local.loadAll();
    this.cache = {
      workouts: storeData.workouts || [],
      sets: storeData.sets || [],
      exercises: storeData.exercises || [],
      bodyWeights: localData.bodyWeights || SEED_BODYWEIGHTS,
      goals: localData.goals || [],
      programs: localData.programs || [],
      activeProgramId: localData.activeProgramId || (localData.programs && localData.programs[0] ? localData.programs[0].id : null),
      draft: localData.draft || null,
    };
  }

  serviceAccountEmail(keyFile) {
    try { return JSON.parse(fs.readFileSync(keyFile, 'utf8')).client_email || null; } catch { return null; }
  }

  exercises() {
    const map = new Map();
    for (const e of DEFAULT_EXERCISES) map.set(e.name.toLowerCase(), { ...e, bodyweight: !!e.bodyweight, description: e.description || '' });
    for (const e of this.cache.exercises) map.set(e.name.toLowerCase(), { ...e, custom: true, description: e.description || '' });
    for (const s of this.cache.sets) {
      if (!map.has(s.exercise.toLowerCase())) {
        map.set(s.exercise.toLowerCase(), { name: s.exercise, category: 'Other', bodyweight: false, description: '' });
      }
    }
    return [...map.values()].sort((a, b) => a.name.localeCompare(b.name));
  }

  state() {
    const cfg = this.readConfig();
    return {
      storage: {
        kind: this.store.kind,
        title: this.store.title || null,
        spreadsheetId: cfg.spreadsheetId || '',
        keyFile: cfg.keyFile || '',
        serviceEmail: cfg.keyFile ? this.serviceAccountEmail(cfg.keyFile) : null,
        error: this.lastError,
      },
      workouts: this.cache.workouts,
      sets: this.cache.sets,
      exercises: this.exercises(),
      bodyWeights: this.cache.bodyWeights || [],
      goals: this.cache.goals || [],
      programs: this.cache.programs || [],
      activeProgramId: this.cache.activeProgramId,
      draft: this.cache.draft,
    };
  }

  // ---------------- Workouts & Sets ----------------

  async saveWorkout({ workout, sets, replaceId }) {
    if (!workout.date) throw new Error('Workout needs a date');
    if (!sets.length) throw new Error('Add at least one set');
    if (replaceId) await this.store.deleteWorkout(replaceId);
    const w = {
      id: replaceId || newId('w'),
      date: workout.date,
      title: workout.title || '',
      notes: workout.notes || '',
    };
    const rows = sets.map((s) => ({
      id: newId('s'),
      workoutId: w.id,
      date: w.date,
      exercise: s.exercise,
      setNo: s.setNo,
      weight: s.weight !== null && s.weight !== undefined ? Number(s.weight) : null,
      reps: s.reps !== null && s.reps !== undefined ? Number(s.reps) : null,
      notes: s.notes || '',
    }));

    // auto-register brand new exercises
    const known = new Set(this.exercises().map((e) => e.name.toLowerCase()));
    const fresh = [...new Set(rows.map((r) => r.exercise))].filter((n) => !known.has(n.toLowerCase()));
    if (fresh.length) await this.store.addExercises(fresh.map((name) => ({ name, category: 'Other' })));

    await this.store.addWorkouts([w], rows);

    // Update goals matching any logged exercise
    const updatedGoals = this._updateGoalsOnWorkoutSave(w, rows);

    await this.reload();
    return { state: this.state(), updatedGoals };
  }

  async deleteWorkout(id) {
    await this.store.deleteWorkout(id);
    await this.reload();
    return this.state();
  }

  async addExercise({ name, category, bodyweight, description }) {
    name = (name || '').trim();
    if (!name) throw new Error('Exercise name required');
    if (this.exercises().some((e) => e.name.toLowerCase() === name.toLowerCase())) return this.state();
    await this.store.addExercises([{
      name,
      category: category || 'Other',
      bodyweight: !!bodyweight,
      description: description || '',
    }]);
    await this.reload();
    return this.state();
  }

  async updateExercise({ oldName, updated }) {
    await this.local.updateExercise(oldName, updated);
    if (this.store.kind === 'sheets') {
      await this.store.addExercises([updated]);
    }
    await this.reload();
    return this.state();
  }

  // ---------------- Programs (Multi-Program) ----------------

  async saveProgram(program) {
    const programs = [...(this.cache.programs || [])];
    const idx = programs.findIndex((p) => p.id === program.id);
    if (idx >= 0) {
      programs[idx] = program;
    } else {
      programs.push(program);
    }
    let activeId = this.cache.activeProgramId;
    if (!activeId || activeId === program.id) activeId = program.id;
    await this.local.savePrograms(programs, activeId);
    await this.reload();
    return this.state();
  }

  async createProgram(name) {
    const cleanName = (name || '').trim() || 'Custom Program';
    const newProg = {
      id: newId('prog'),
      programName: cleanName,
      days: [],
      lastLoadedDayIndex: -1,
      createdAt: new Date().toISOString().slice(0, 10),
    };
    const programs = [...(this.cache.programs || []), newProg];
    await this.local.savePrograms(programs, newProg.id);
    await this.reload();
    return this.state();
  }

  async deleteProgram(id) {
    let programs = (this.cache.programs || []).filter((p) => p.id !== id);
    let activeId = this.cache.activeProgramId;
    if (activeId === id) {
      activeId = programs.length > 0 ? programs[0].id : null;
    }
    await this.local.savePrograms(programs, activeId);
    await this.reload();
    return this.state();
  }

  async duplicateProgram(id) {
    const orig = (this.cache.programs || []).find((p) => p.id === id);
    if (!orig) return this.state();
    const copy = {
      ...orig,
      id: newId('prog'),
      programName: `${orig.programName || 'Custom Program'} (Copy)`,
      createdAt: new Date().toISOString().slice(0, 10),
    };
    const programs = [...(this.cache.programs || []), copy];
    await this.local.savePrograms(programs, copy.id);
    await this.reload();
    return this.state();
  }

  async setActiveProgramId(id) {
    await this.local.savePrograms(this.cache.programs || [], id);
    await this.reload();
    return this.state();
  }

  // ---------------- Goals ----------------

  async saveGoal(goal) {
    const goals = [...(this.cache.goals || [])];
    const clean = {
      ...goal,
      id: goal.id || newId('goal'),
      exerciseName: (goal.exerciseName || goal.exercise || '').trim(),
      createdDate: goal.createdDate || new Date().toISOString().slice(0, 10),
      sessionReports: goal.sessionReports || [],
    };
    const idx = goals.findIndex((g) => g.id === clean.id || g.exerciseName.toLowerCase() === clean.exerciseName.toLowerCase());
    if (idx >= 0) {
      goals[idx] = clean;
    } else {
      goals.unshift(clean);
    }
    await this.local.saveGoals(goals);
    await this.reload();
    return this.state();
  }

  async deleteGoal(id) {
    const goals = (this.cache.goals || []).filter((g) => g.id !== id);
    await this.local.saveGoals(goals);
    await this.reload();
    return this.state();
  }

  async toggleGoalEnabled(id) {
    const goals = (this.cache.goals || []).map((g) => (g.id === id ? { ...g, enabled: !g.enabled } : g));
    await this.local.saveGoals(goals);
    await this.reload();
    return this.state();
  }

  // ---------------- Body Weights ----------------

  async addBodyWeight({ date, weightKg, source }) {
    if (!date || !weightKg) throw new Error('Date and weight required');
    const list = [...(this.cache.bodyWeights || [])];
    const cleanKg = Number(weightKg);
    const existingIdx = list.findIndex((e) => e.date === date);
    const entry = { date, weightKg: cleanKg, source: source || 'manual' };
    if (existingIdx >= 0) {
      list[existingIdx] = entry;
    } else {
      list.push(entry);
    }
    await this.local.saveBodyWeights(list);
    await this.reload();
    return this.state();
  }

  async deleteBodyWeight(date) {
    const list = (this.cache.bodyWeights || []).filter((e) => e.date !== date);
    await this.local.saveBodyWeights(list);
    await this.reload();
    return this.state();
  }

  // ---------------- Draft ----------------

  async saveDraft(draft) {
    await this.local.saveDraft(draft);
    this.cache.draft = draft;
    return { ok: true };
  }

  async clearDraft() {
    await this.local.clearDraft();
    this.cache.draft = null;
    return { ok: true };
  }

  // ---------------- Internal Goal Updater ----------------

  _updateGoalsOnWorkoutSave(workout, sets) {
    const goals = [...(this.cache.goals || [])];
    if (!goals.length) return [];

    const setsByEx = new Map();
    for (const s of sets) {
      if ((s.reps ?? 0) <= 0) continue;
      const k = s.exercise.toLowerCase();
      if (!setsByEx.has(k)) setsByEx.set(k, []);
      setsByEx.get(k).push(s);
    }

    const updatedGoals = [];
    for (let i = 0; i < goals.length; i++) {
      const g = goals[i];
      if (g.enabled === false) continue;
      const logged = setsByEx.get(g.exerciseName.toLowerCase());
      if (!logged || !logged.length) continue;

      let topW = 0, topR = 0, topE1rm = 0, vol = 0;
      for (const s of logged) {
        const w = Number(s.weight || 0);
        const r = Number(s.reps || 0);
        vol += w * r;
        const e = w * (1 + r / 30);
        if (e > topE1rm) {
          topE1rm = e;
          topW = w;
          topR = r;
        }
      }

      const isAchieved = g.goalType === 'SETS_REPS'
        ? logged.filter((s) => Number(s.weight || 0) >= (g.targetWeightKg || 0) && Number(s.reps || 0) >= (g.targetReps || 0)).length >= (g.targetSets || 3)
        : topE1rm >= (g.targetE1rmKg || 0);

      const targetE1rm = g.goalType === 'SETS_REPS'
        ? (g.targetWeightKg || 0) * (1 + (g.targetReps || 8) / 30)
        : (g.targetE1rmKg || 0);

      const progressPct = targetE1rm > 0 ? Math.min(100, Math.round((topE1rm / targetE1rm) * 100)) : 0;

      const report = {
        workoutId: workout.id,
        date: workout.date,
        sessionBestE1rm: Math.round(topE1rm * 10) / 10,
        sessionTopWeight: topW,
        sessionTopReps: topR,
        workingSetsCount: logged.length,
        totalVolumeKg: Math.round(vol),
        progressPct,
        deltaE1rmKg: 0,
        achievedGoal: isAchieved,
        summaryNote: `${topW}kg × ${topR} reps (E1RM: ${Math.round(topE1rm * 10) / 10}kg)`,
      };

      const reports = [...(g.sessionReports || []), report];
      const updatedGoal = {
        ...g,
        sessionReports: reports,
        achievedDate: isAchieved && !g.achievedDate ? workout.date : g.achievedDate,
        lastUpdatedDate: workout.date,
      };
      goals[i] = updatedGoal;
      updatedGoals.push(updatedGoal);
    }

    if (updatedGoals.length) {
      this.local.saveGoals(goals);
    }
    return updatedGoals;
  }

  // ---------------- Cloud & Import ----------------

  async connect({ spreadsheetId, keyFile }) {
    spreadsheetId = extractSheetId(spreadsheetId);
    if (!spreadsheetId) throw new Error('Enter a spreadsheet ID or URL');
    if (!keyFile || !fs.existsSync(keyFile)) throw new Error('Select a valid service account key file');
    await this._connectSheets({ spreadsheetId, keyFile });
    this.writeConfig({ spreadsheetId, keyFile });
    this.lastError = null;
    await this.reload();
    return this.state();
  }

  async disconnect() {
    this.writeConfig({});
    this.store = this.local;
    this.lastError = null;
    await this.reload();
    return this.state();
  }

  async importHistory() {
    const { workouts, sets } = parseHistory();
    const existing = new Set(this.cache.workouts.map((w) => w.id));
    const newW = workouts.filter((w) => !existing.has(w.id));
    const ids = new Set(newW.map((w) => w.id));
    const newS = sets.filter((s) => ids.has(s.workoutId));
    if (newW.length) await this.store.addWorkouts(newW, newS);
    await this.reload();
    return { added: newW.length, sets: newS.length, skipped: workouts.length - newW.length, state: this.state() };
  }

  async copyLocalToSheets(options = {}) {
    if (this.store.kind !== 'sheets') throw new Error('Connect Google Sheets first');
    const local = await this.local.loadAll();
    if (options.overwrite) {
      await this.store.clearAll();
      await this.reload();
    }
    const existing = new Set(this.cache.workouts.map((w) => w.id));
    const newW = local.workouts.filter((w) => !existing.has(w.id));
    const ids = new Set(newW.map((w) => w.id));
    const newS = local.sets.filter((s) => ids.has(s.workoutId));
    const knownEx = new Set(this.cache.exercises.map((e) => e.name.toLowerCase()));
    const newEx = local.exercises.filter((e) => !knownEx.has(e.name.toLowerCase()));
    if (newEx.length) await this.store.addExercises(newEx);
    if (newW.length) await this.store.addWorkouts(newW, newS);
    await this.reload();
    return { added: newW.length, sets: newS.length, skipped: local.workouts.length - newW.length, state: this.state() };
  }

  async clearAllData() {
    await this.store.clearAll();
    await this.reload();
    return this.state();
  }
}

function newId(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${crypto.randomBytes(3).toString('hex')}`;
}

function extractSheetId(input) {
  input = (input || '').trim();
  const m = input.match(/\/spreadsheets\/d\/([a-zA-Z0-9-_]+)/);
  return m ? m[1] : input;
}

module.exports = { DataService };
