// Google Sheets storage backend.
// Layout (tabs are created automatically):
//   Workouts : Id | Date | Title | Notes
//   Sets     : Id | WorkoutId | Date | Exercise | Set | Weight (kg) | Reps | Notes
//   Exercises: Name | Category | Bodyweight
const { google } = require('googleapis');

const TABS = {
  Workouts: ['Id', 'Date', 'Title', 'Notes'],
  Sets: ['Id', 'WorkoutId', 'Date', 'Exercise', 'Set', 'Weight (kg)', 'Reps', 'Notes'],
  Exercises: ['Name', 'Category', 'Bodyweight', 'Description'],
};

const colLetter = (n) => String.fromCharCode(64 + n);

class SheetsStore {
  constructor({ spreadsheetId, keyFile }) {
    this.kind = 'sheets';
    this.spreadsheetId = spreadsheetId;
    this.auth = new google.auth.GoogleAuth({
      keyFile,
      scopes: ['https://www.googleapis.com/auth/spreadsheets'],
    });
    this.api = google.sheets({ version: 'v4', auth: this.auth });
    this.sheetIds = {};
  }

  async init() {
    const meta = await this.api.spreadsheets.get({ spreadsheetId: this.spreadsheetId });
    const existing = {};
    for (const s of meta.data.sheets) existing[s.properties.title] = s.properties.sheetId;

    const missing = Object.keys(TABS).filter((t) => !(t in existing));
    if (missing.length) {
      const res = await this.api.spreadsheets.batchUpdate({
        spreadsheetId: this.spreadsheetId,
        requestBody: {
          requests: missing.map((title) => ({
            addSheet: { properties: { title, gridProperties: { frozenRowCount: 1 } } },
          })),
        },
      });
      for (const r of res.data.replies) existing[r.addSheet.properties.title] = r.addSheet.properties.sheetId;
      await this.api.spreadsheets.values.batchUpdate({
        spreadsheetId: this.spreadsheetId,
        requestBody: {
          valueInputOption: 'RAW',
          data: missing.map((t) => ({ range: `${t}!A1`, values: [TABS[t]] })),
        },
      });
    }
    for (const t of Object.keys(TABS)) this.sheetIds[t] = existing[t];

    // Clean up empty default "Sheet1" if present so the document opens directly on Workouts
    if ('Sheet1' in existing && Object.keys(existing).length > 1) {
      try {
        await this.api.spreadsheets.batchUpdate({
          spreadsheetId: this.spreadsheetId,
          requestBody: {
            requests: [{ deleteSheet: { sheetId: existing['Sheet1'] } }],
          },
        });
      } catch {
        // ignore if it cannot be deleted
      }
    }

    this.title = meta.data.properties.title;
  }

  async loadAll() {
    const res = await this.api.spreadsheets.values.batchGet({
      spreadsheetId: this.spreadsheetId,
      ranges: Object.keys(TABS).map((t) => `${t}!A2:${colLetter(TABS[t].length)}`),
      valueRenderOption: 'UNFORMATTED_VALUE',
    });
    const [w, s, e] = res.data.valueRanges.map((vr) => vr.values || []);
    const num = (v) => (v === '' || v === undefined || v === null ? null : Number(v));
    return {
      workouts: w.filter((r) => r[0]).map((r) => ({
        id: String(r[0]), date: String(r[1] || ''), title: r[2] || '', notes: r[3] || '',
      })),
      sets: s.filter((r) => r[0]).map((r) => ({
        id: String(r[0]), workoutId: String(r[1]), date: String(r[2] || ''), exercise: r[3] || '',
        setNo: num(r[4]), weight: num(r[5]), reps: num(r[6]), notes: r[7] || '',
      })),
      exercises: e.filter((r) => r[0]).map((r) => ({
        name: r[0], category: r[1] || 'Other', bodyweight: r[2] === true || String(r[2]).toUpperCase() === 'TRUE',
        description: r[3] || '',
      })),
    };
  }

  async _append(tab, rows) {
    if (!rows.length) return;
    await this.api.spreadsheets.values.append({
      spreadsheetId: this.spreadsheetId,
      range: `${tab}!A1`,
      valueInputOption: 'RAW',
      insertDataOption: 'INSERT_ROWS',
      requestBody: { values: rows },
    });
  }

  async addWorkouts(workouts, sets) {
    const blank = (v) => (v === null || v === undefined ? '' : v);
    await this._append('Workouts', workouts.map((w) => [w.id, w.date, w.title || '', w.notes || '']));
    await this._append('Sets', sets.map((s) => [
      s.id, s.workoutId, s.date, s.exercise, blank(s.setNo), blank(s.weight), blank(s.reps), s.notes || '',
    ]));
  }

  async deleteWorkout(id) {
    const res = await this.api.spreadsheets.values.batchGet({
      spreadsheetId: this.spreadsheetId,
      ranges: ['Workouts!A:A', 'Sets!B:B'],
    });
    const [wCol, sCol] = res.data.valueRanges.map((vr) => (vr.values || []).map((r) => r[0]));
    const requests = [];
    const addDeletes = (tab, col) => {
      const rows = [];
      col.forEach((v, i) => { if (i > 0 && String(v) === id) rows.push(i); });
      // delete bottom-up so indices stay valid
      rows.sort((a, b) => b - a).forEach((i) => requests.push({
        deleteDimension: {
          range: { sheetId: this.sheetIds[tab], dimension: 'ROWS', startIndex: i, endIndex: i + 1 },
        },
      }));
    };
    addDeletes('Sets', sCol);
    addDeletes('Workouts', wCol);
    if (requests.length) {
      await this.api.spreadsheets.batchUpdate({ spreadsheetId: this.spreadsheetId, requestBody: { requests } });
    }
  }

  async addExercises(exercises) {
    await this._append('Exercises', exercises.map((e) => [e.name, e.category || 'Other', !!e.bodyweight, e.description || '']));
  }

  async clearAll() {
    await this.api.spreadsheets.values.batchClear({
      spreadsheetId: this.spreadsheetId,
      requestBody: {
        ranges: ['Workouts!A2:Z', 'Sets!A2:Z'],
      },
    });
  }
}

module.exports = { SheetsStore };

