// Local JSON storage backend - used when Google Sheets isn't configured yet.
const fs = require('fs');
const path = require('path');

class LocalStore {
  constructor(dir) {
    this.file = path.join(dir, 'local-data.json');
    this.kind = 'local';
  }

  async init() {
    if (!fs.existsSync(this.file)) this._write({ workouts: [], sets: [], exercises: [] });
  }

  _read() {
    return JSON.parse(fs.readFileSync(this.file, 'utf8'));
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

  async clearAll() {
    this._write({ workouts: [], sets: [], exercises: [] });
  }
}

module.exports = { LocalStore };

