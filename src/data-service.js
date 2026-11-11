// Data service: owns config, active storage backend, and in-memory state.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { LocalStore } = require('./stores/local-store');
const { SheetsStore } = require('./stores/sheets-store');
const SyncMerge = require('./sync-merge');
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
      bodyWeights: [],
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

  async reload(options = {}) {
    const localData = await this.local.loadAll();
    let storeData = localData;
    let loadOk = true;
    if (this.store.kind === 'sheets') {
      try {
        storeData = await this.store.loadAll();
      } catch (err) {
        if (options.strict) throw err;
        loadOk = false;
        console.error('[sync] loadAll', err);
        this.lastError = `Could not load from Google Sheets: ${err.message}`;
        storeData = {
          workouts: this.cache.workouts.length ? this.cache.workouts : localData.workouts,
          sets: this.cache.sets.length ? this.cache.sets : localData.sets,
          exercises: this.cache.exercises.length ? this.cache.exercises : localData.exercises,
        };
      }
    }
    let meta = {
      bodyWeights: localData.bodyWeights || [],
      goals: localData.goals || [],
      programs: localData.programs || [],
      activeProgramId: localData.activeProgramId || null,
    };
    if (this.store.kind === 'sheets' && loadOk) {
      try {
        meta = await this._syncMetadata(storeData, localData);
        this.lastError = null;
      } catch (err) {
        console.error('[sync] body weights / goals / programs', err);
        this.lastError = `Could not sync body weights, goals and programs: ${err.message}`;
      }
    }
    this.cache = {
      workouts: storeData.workouts || [],
      sets: storeData.sets || [],
      exercises: storeData.exercises || [],
      bodyWeights: meta.bodyWeights,
      goals: meta.goals,
      programs: meta.programs,
      activeProgramId: meta.activeProgramId || (meta.programs && meta.programs[0] ? meta.programs[0].id : null),
      draft: localData.draft || null,
    };
  }

  /**
   * Merges local BodyWeights / Goals / Programs with the Google Sheets tabs (same rules as Android),
   * writes the result to local storage and pushes a tab only when it actually changed.
   */
  async _syncMetadata(remote, local) {
    const sync = local.sync || {};
    const sameSheet = !sync.spreadsheetId || sync.spreadsheetId === this.store.spreadsheetId;
    const arr = (k) => (sameSheet ? (sync[k] || []) : []);
    const cmp = (v) => SyncMerge.stable(JSON.parse(JSON.stringify(v)));
    const normBw = (b) => ({ date: b.date, weightKg: Number(b.weightKg), source: b.source || 'manual', updatedAt: Number(b.updatedAt) || 0 });
    const normStamp = (x) => ({ ...x, updatedAt: Number(x.updatedAt) || 0 });

    const remoteBw = (remote.bodyWeights || []).map(normBw).sort((a, b) => SyncMerge.isoKey(a.date).localeCompare(SyncMerge.isoKey(b.date)));
    const bw = SyncMerge.mergeBodyWeights((local.bodyWeights || []).map(normBw), remoteBw, arr('pendingBodyWeights'), arr('syncedBodyWeights'));
    if (cmp(bw) !== cmp(remoteBw)) await this.store.overwriteBodyWeights(bw);

    const remoteGoals = (remote.goals || []).map(normStamp);
    const goals = SyncMerge.mergeGoals((local.goals || []).map(normStamp), remoteGoals, arr('pendingGoals'), arr('syncedGoals'));
    if (cmp([...goals].sort((a, b) => a.id.localeCompare(b.id))) !== cmp([...remoteGoals].sort((a, b) => a.id.localeCompare(b.id)))) {
      await this.store.overwriteGoals(goals);
    }

    const remotePrograms = (remote.programs || []).map(normStamp);
    const programs = SyncMerge.mergePrograms((local.programs || []).map(normStamp), remotePrograms, arr('pendingPrograms'), arr('syncedPrograms'));
    const has = (id) => !!id && programs.some((p) => p.id === id);
    const activeProgramId = has(local.activeProgramId) ? local.activeProgramId
      : (has(remote.activeProgramId) ? remote.activeProgramId : (programs[0] ? programs[0].id : null));
    if (cmp([...programs].sort((a, b) => a.id.localeCompare(b.id))) !== cmp([...remotePrograms].sort((a, b) => a.id.localeCompare(b.id)))
      || (programs.length && activeProgramId !== remote.activeProgramId)) {
      await this.store.overwritePrograms(programs, activeProgramId);
    }

    await this.local.saveBodyWeights(bw);
    await this.local.saveGoals(goals);
    await this.local.savePrograms(programs, activeProgramId);
    await this.local.saveSync({
      spreadsheetId: this.store.spreadsheetId,
      pendingBodyWeights: [], pendingGoals: [], pendingPrograms: [],
      syncedBodyWeights: bw.map((b) => SyncMerge.isoKey(b.date)),
      syncedGoals: goals.map((g) => g.id),
      syncedPrograms: programs.map((p) => p.id),
    });
    return { bodyWeights: bw, goals, programs, activeProgramId };
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
    const updatedGoals = await this._updateGoalsOnWorkoutSave(w, rows);

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
    const cleanOld = (oldName || '').trim();
    const cleanNew = (updated?.name || '').trim();
    if (!cleanNew) throw new Error('Exercise name required');
    if (cleanOld.toLowerCase() !== cleanNew.toLowerCase()
      && this.exercises().some((e) => e.name.toLowerCase() === cleanNew.toLowerCase())) {
      throw new Error(`An exercise named "${cleanNew}" already exists`);
    }
    const cleanUpdated = {
      name: cleanNew,
      category: updated.category || 'Other',
      bodyweight: !!updated.bodyweight,
      description: updated.description || '',
    };
    await this.local.updateExercise(cleanOld, cleanUpdated);
    if (this.store.kind === 'sheets') {
      if (cleanOld.toLowerCase() !== cleanNew.toLowerCase()) {
        await this.store.renameExerciseInSets(cleanOld, cleanNew);
      }
      const list = [...(this.cache.exercises || [])];
      const idx = list.findIndex((e) => e.name.trim().toLowerCase() === cleanOld.toLowerCase());
      if (idx >= 0) list[idx] = cleanUpdated;
      else list.push(cleanUpdated);
      await this.store.overwriteExercises(list);
    }
    await this.reload();
    return this.state();
  }

  // ---------------- Programs (Multi-Program) ----------------

  async saveProgram(program) {
    const programs = [...(this.cache.programs || [])];
    const idx = programs.findIndex((p) => p.id === program.id);
    program = { ...program, updatedAt: Date.now() };
    if (idx >= 0) {
      programs[idx] = program;
    } else {
      programs.push(program);
    }
    let activeId = this.cache.activeProgramId;
    if (!activeId || activeId === program.id) activeId = program.id;
    await this.local.removePendingDelete('Programs', program.id);
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
      updatedAt: Date.now(),
    };
    const programs = [...(this.cache.programs || []), newProg];
    await this.local.savePrograms(programs, newProg.id);
    await this.reload();
    return this.state();
  }

  async deleteProgram(id) {
    await this.local.addPendingDelete('Programs', id);
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
      updatedAt: Date.now(),
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
      enabled: goal.enabled !== false,
      createdDate: goal.createdDate || new Date().toISOString().slice(0, 10),
      sessionReports: goal.sessionReports || [],
      updatedAt: Date.now(),
    };
    const idx = goals.findIndex((g) => g.id === clean.id || g.exerciseName.toLowerCase() === clean.exerciseName.toLowerCase());
    if (idx >= 0) {
      goals[idx] = clean;
    } else {
      goals.unshift(clean);
    }
    await this.local.removePendingDelete('Goals', clean.id);
    await this.local.saveGoals(goals);
    await this.reload();
    return this.state();
  }

  async deleteGoal(id) {
    await this.local.addPendingDelete('Goals', id);
    const goals = (this.cache.goals || []).filter((g) => g.id !== id);
    await this.local.saveGoals(goals);
    await this.reload();
    return this.state();
  }

  async toggleGoalEnabled(id) {
    const goals = (this.cache.goals || []).map((g) => (g.id === id ? { ...g, enabled: g.enabled === false, updatedAt: Date.now() } : g));
    await this.local.saveGoals(goals);
    await this.reload();
    return this.state();
  }

  // ---------------- Body Weights ----------------

  async addBodyWeight({ date, weightKg, source }) {
    if (!date || !weightKg) throw new Error('Date and weight required');
    const list = [...(this.cache.bodyWeights || [])];
    const cleanKg = Number(weightKg);
    const key = SyncMerge.isoKey(date);
    const existingIdx = list.findIndex((e) => SyncMerge.isoKey(e.date) === key);
    const entry = { date, weightKg: cleanKg, source: source || 'manual', updatedAt: Date.now() };
    if (existingIdx >= 0) {
      list[existingIdx] = entry;
    } else {
      list.push(entry);
    }
    await this.local.removePendingDelete('BodyWeights', key);
    await this.local.saveBodyWeights(list);
    await this.reload();
    return this.state();
  }

  async deleteBodyWeight(date) {
    const key = SyncMerge.isoKey(date);
    if (key) await this.local.addPendingDelete('BodyWeights', key);
    const list = (this.cache.bodyWeights || []).filter((e) => SyncMerge.isoKey(e.date) !== key);
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

  async _updateGoalsOnWorkoutSave(workout, sets) {
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
        updatedAt: Date.now(),
      };
      goals[i] = updatedGoal;
      updatedGoals.push(updatedGoal);
    }

    if (updatedGoals.length) {
      await this.local.saveGoals(goals);
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
    await this.reload({ strict: true });
    return this.state();
  }

  async disconnect() {
    this.writeConfig({});
    this.store = this.local;
    this.lastError = null;
    await this.reload();
    return this.state();
  }

  async copyLocalToSheets(options = {}) {
    if (this.store.kind !== 'sheets') throw new Error('Connect Google Sheets first');
    const local = await this.local.loadAll();
    if (options.overwrite) {
      await this.store.clearAll();
    }
    // Clear last-synced keys for this sheet so local bodyWeights/goals/programs push even if remote was wiped
    await this.local.saveSync({
      ...(local.sync || {}),
      spreadsheetId: this.store.spreadsheetId,
      syncedBodyWeights: [],
      syncedGoals: [],
      syncedPrograms: [],
    });
    if (options.overwrite) {
      await this.reload({ strict: true });
    }
    const existing = new Set(this.cache.workouts.map((w) => w.id));
    const newW = local.workouts.filter((w) => !existing.has(w.id));
    const ids = new Set(newW.map((w) => w.id));
    const newS = local.sets.filter((s) => ids.has(s.workoutId));
    const knownEx = new Set(this.cache.exercises.map((e) => e.name.toLowerCase()));
    const newEx = local.exercises.filter((e) => !knownEx.has(e.name.toLowerCase()));
    if (newEx.length) await this.store.addExercises(newEx);
    if (newW.length) await this.store.addWorkouts(newW, newS);
    await this.reload({ strict: true });
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
