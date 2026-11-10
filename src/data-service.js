// Data service: owns config, the active storage backend and an in-memory cache.
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { LocalStore } = require('./stores/local-store');
const { SheetsStore } = require('./stores/sheets-store');
const { parseHistory } = require('./history-parser');
const DEFAULT_EXERCISES = require('../data/exercises.json');

class DataService {
  constructor(userDataDir) {
    this.dir = userDataDir;
    this.configFile = path.join(userDataDir, 'config.json');
    this.local = new LocalStore(userDataDir);
    this.store = this.local;
    this.cache = { workouts: [], sets: [], exercises: [] };
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
    this.cache = await this.store.loadAll();
  }

  serviceAccountEmail(keyFile) {
    try { return JSON.parse(fs.readFileSync(keyFile, 'utf8')).client_email || null; } catch { return null; }
  }

  exercises() {
    const map = new Map();
    for (const e of DEFAULT_EXERCISES) map.set(e.name.toLowerCase(), { ...e, bodyweight: !!e.bodyweight });
    for (const e of this.cache.exercises) map.set(e.name.toLowerCase(), { ...e, custom: true });
    for (const s of this.cache.sets) {
      if (!map.has(s.exercise.toLowerCase())) map.set(s.exercise.toLowerCase(), { name: s.exercise, category: 'Other' });
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
    };
  }

  async saveWorkout({ workout, sets, replaceId }) {
    if (!workout.date) throw new Error('Workout needs a date');
    if (!sets.length) throw new Error('Add at least one set');
    if (replaceId) await this.store.deleteWorkout(replaceId);
    const w = { id: replaceId || newId('w'), date: workout.date, title: workout.title || '', notes: workout.notes || '' };
    const rows = sets.map((s) => ({
      id: newId('s'), workoutId: w.id, date: w.date, exercise: s.exercise, setNo: s.setNo,
      weight: s.weight, reps: s.reps, notes: s.notes || '',
    }));
    // auto-register brand new exercises
    const known = new Set(this.exercises().map((e) => e.name.toLowerCase()));
    const fresh = [...new Set(rows.map((r) => r.exercise))].filter((n) => !known.has(n.toLowerCase()));
    if (fresh.length) await this.store.addExercises(fresh.map((name) => ({ name, category: 'Other' })));
    await this.store.addWorkouts([w], rows);
    await this.reload();
    return this.state();
  }

  async deleteWorkout(id) {
    await this.store.deleteWorkout(id);
    await this.reload();
    return this.state();
  }

  async addExercise({ name, category, bodyweight }) {
    name = (name || '').trim();
    if (!name) throw new Error('Exercise name required');
    if (this.exercises().some((e) => e.name.toLowerCase() === name.toLowerCase())) return this.state();
    await this.store.addExercises([{ name, category: category || 'Other', bodyweight: !!bodyweight }]);
    await this.reload();
    return this.state();
  }

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

  /** Import data/history.txt. Idempotent: workouts already present are skipped. */
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

  /** Copy anything logged locally (before Sheets was connected) into Sheets. */
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

  /** Reset all workouts and sets in active storage (useful if user wants a clean slate or re-import). */
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
