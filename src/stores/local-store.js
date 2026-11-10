// Local JSON storage backend - handles workouts, sets, exercises, body weights, goals, programs, and drafts.
const fs = require('fs');
const path = require('path');

class LocalStore {
  constructor(dir) {
    this.file = path.join(dir, 'local-data.json');
    this.kind = 'local';
  }

  async init() {
    if (!fs.existsSync(this.file)) {
      this._write({
        workouts: [],
        sets: [],
        exercises: [],
        bodyWeights: [],
        goals: [],
        programs: [],
        activeProgramId: null,
        draft: null,
      });
    } else {
      const data = this._read();
      let changed = false;
      if (!Array.isArray(data.bodyWeights)) {
        data.bodyWeights = [];
        changed = true;
      }
      if (!Array.isArray(data.goals)) {
        data.goals = [];
        changed = true;
      }
      if (!Array.isArray(data.programs)) {
        data.programs = [];
        changed = true;
      }
      if (changed) this._write(data);
    }
  }

  _read() {
    try {
      const parsed = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      return {
        workouts: Array.isArray(parsed.workouts) ? parsed.workouts : [],
        sets: Array.isArray(parsed.sets) ? parsed.sets : [],
        exercises: Array.isArray(parsed.exercises) ? parsed.exercises : [],
        bodyWeights: Array.isArray(parsed.bodyWeights) ? parsed.bodyWeights : [],
        goals: Array.isArray(parsed.goals) ? parsed.goals : [],
        programs: Array.isArray(parsed.programs) ? parsed.programs : [],
        activeProgramId: parsed.activeProgramId || null,
        draft: parsed.draft || null,
      };
    } catch {
      return {
        workouts: [],
        sets: [],
        exercises: [],
        bodyWeights: [],
        goals: [],
        programs: [],
        activeProgramId: null,
        draft: null,
      };
    }
  }

  _write(data) {
    const tmp = this.file + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(data, null, 1));
    fs.renameSync(tmp, this.file);
  }

  async loadAll() {
    return this._read();
  }

  async addWorkouts(workouts, sets) {
    const data = this._read();
    data.workouts.push(...workouts);
    data.sets.push(...sets);
    this._write(data);
  }

  async deleteWorkout(id) {
    const data = this._read();
    data.workouts = data.workouts.filter((w) => w.id !== id);
    data.sets = data.sets.filter((s) => s.workoutId !== id);
    this._write(data);
  }

  async addExercises(exercises) {
    const data = this._read();
    data.exercises.push(...exercises);
    this._write(data);
  }

  async updateExercise(oldName, updated) {
    const data = this._read();
    const cleanOld = oldName.trim().toLowerCase();
    const cleanNew = updated.name.trim();

    // 1. Update in exercises list
    const exIdx = data.exercises.findIndex((e) => e.name.trim().toLowerCase() === cleanOld);
    if (exIdx >= 0) {
      data.exercises[exIdx] = { ...data.exercises[exIdx], ...updated, name: cleanNew };
    } else {
      data.exercises.push(updated);
    }

    // 2. Cascade rename across sets
    for (const s of data.sets) {
      if (s.exercise && s.exercise.trim().toLowerCase() === cleanOld) {
        s.exercise = cleanNew;
      }
    }

    // 3. Cascade rename across goals
    for (const g of data.goals) {
      if (g.exerciseName && g.exerciseName.trim().toLowerCase() === cleanOld) {
        g.exerciseName = cleanNew;
      }
    }

    // 4. Cascade rename across program days
    for (const p of data.programs) {
      if (Array.isArray(p.days)) {
        for (const d of p.days) {
          if (Array.isArray(d.exercises)) {
            for (const item of d.exercises) {
              if (item.exerciseName && item.exerciseName.trim().toLowerCase() === cleanOld) {
                item.exerciseName = cleanNew;
                if (updated.category) item.category = updated.category;
              }
            }
          }
        }
      }
    }

    this._write(data);
  }

  async saveGoals(goals) {
    const data = this._read();
    data.goals = goals;
    this._write(data);
  }

  async savePrograms(programs, activeProgramId) {
    const data = this._read();
    data.programs = programs;
    if (activeProgramId !== undefined) data.activeProgramId = activeProgramId;
    this._write(data);
  }

  async saveBodyWeights(bodyWeights) {
    const data = this._read();
    data.bodyWeights = bodyWeights;
    this._write(data);
  }

  async saveDraft(draft) {
    const data = this._read();
    data.draft = draft;
    this._write(data);
  }

  async clearDraft() {
    const data = this._read();
    data.draft = null;
    this._write(data);
  }

  async clearAll() {
    const current = this._read();
    this._write({
      workouts: [],
      sets: [],
      exercises: current.exercises,
      bodyWeights: current.bodyWeights,
      goals: current.goals,
      programs: current.programs,
      activeProgramId: current.activeProgramId,
      draft: null,
    });
  }
}

module.exports = { LocalStore };
