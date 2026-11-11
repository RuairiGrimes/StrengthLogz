// Run with: node --test tests/
const test = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { DataService } = require('../src/data-service');

function makeTempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), 'sl-test-'));
}

function makeMockSheets(spreadsheetId = 'sheet-1') {
  const remote = {
    workouts: [],
    sets: [],
    exercises: [],
    bodyWeights: [],
    goals: [],
    programs: [],
    activeProgramId: null,
  };
  const calls = [];
  const store = {
    kind: 'sheets',
    title: 'Mock Sheet',
    spreadsheetId,
    async loadAll() {
      calls.push(['loadAll']);
      return JSON.parse(JSON.stringify(remote));
    },
    async overwriteBodyWeights(list) {
      calls.push(['overwriteBodyWeights', list.length]);
      remote.bodyWeights = JSON.parse(JSON.stringify(list));
    },
    async overwriteGoals(list) {
      calls.push(['overwriteGoals', list.length]);
      remote.goals = JSON.parse(JSON.stringify(list));
    },
    async overwritePrograms(list, activeId) {
      calls.push(['overwritePrograms', list.length, activeId]);
      remote.programs = JSON.parse(JSON.stringify(list));
      remote.activeProgramId = activeId || null;
    },
    async overwriteExercises(list) {
      calls.push(['overwriteExercises', list.length]);
      remote.exercises = JSON.parse(JSON.stringify(list));
    },
    async renameExerciseInSets(oldName, newName) {
      calls.push(['renameExerciseInSets', oldName, newName]);
      for (const s of remote.sets) {
        if (s.exercise.toLowerCase() === oldName.toLowerCase()) s.exercise = newName;
      }
    },
    async addExercises(list) {
      calls.push(['addExercises', list.length]);
      remote.exercises.push(...JSON.parse(JSON.stringify(list)));
    },
    async addWorkouts(ws, ss) {
      calls.push(['addWorkouts', ws.length, ss.length]);
      remote.workouts.push(...JSON.parse(JSON.stringify(ws)));
      remote.sets.push(...JSON.parse(JSON.stringify(ss)));
    },
    async deleteWorkout(id) {
      calls.push(['deleteWorkout', id]);
      remote.workouts = remote.workouts.filter((w) => w.id !== id);
      remote.sets = remote.sets.filter((s) => s.workoutId !== id);
    },
    async clearAll() {
      calls.push(['clearAll']);
      remote.workouts = [];
      remote.sets = [];
    },
  };
  return { remote, calls, store };
}

test('DataService syncs BodyWeights, Goals, Programs and activeProgramId with Sheets', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();
    const { remote, calls, store } = makeMockSheets('sheet-A');
    remote.bodyWeights.push({ date: '01-JAN-2026', weightKg: 80.5, source: 'healthconnect', updatedAt: 100 });
    remote.goals.push({ id: 'g-remote', exerciseName: 'Squat', goalType: 'E1RM', targetE1rmKg: 160, sessionReports: [], updatedAt: 100 });
    remote.programs.push({ id: 'p-remote', programName: 'Remote Split', days: [], updatedAt: 100 });
    remote.activeProgramId = 'p-remote';

    ds.store = store;
    await ds.reload();

    let st = ds.state();
    assert.strictEqual(st.bodyWeights.length, 1);
    assert.strictEqual(st.goals.length, 1);
    assert.strictEqual(st.programs.length, 1);
    assert.strictEqual(st.activeProgramId, 'p-remote');

    // Add manual body weight -> pushes to Sheets
    st = await ds.addBodyWeight({ date: '02-JAN-2026', weightKg: 81.2, source: 'manual' });
    assert.strictEqual(remote.bodyWeights.length, 2);

    // Delete body weight -> removes from Sheets
    st = await ds.deleteBodyWeight('01-JAN-2026');
    assert.strictEqual(remote.bodyWeights.length, 1);
    assert.strictEqual(remote.bodyWeights[0].date, '02-JAN-2026');

    // Create second program & switch activeProgramId -> pushes IsActive to Sheets
    st = await ds.createProgram('Second Split');
    assert.strictEqual(remote.programs.length, 2);
    assert.strictEqual(remote.activeProgramId, st.activeProgramId);
    const createdId = st.activeProgramId;

    calls.length = 0;
    st = await ds.setActiveProgramId('p-remote');
    assert.strictEqual(st.activeProgramId, 'p-remote');
    assert.strictEqual(remote.activeProgramId, 'p-remote');
    assert.ok(calls.some((c) => c[0] === 'overwritePrograms' && c[2] === 'p-remote'));

    // Toggle & delete goal -> pushes to Sheets
    st = await ds.toggleGoalEnabled('g-remote');
    assert.strictEqual(remote.goals[0].enabled, false);
    st = await ds.deleteGoal('g-remote');
    assert.strictEqual(remote.goals.length, 0);

    // Delete created program -> pushes to Sheets
    st = await ds.deleteProgram(createdId);
    assert.strictEqual(remote.programs.length, 1);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('DataService re-adding offline-deleted body weight clears pending delete', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();
    await ds.addBodyWeight({ date: '05-FEB-2026', weightKg: 79.0, source: 'manual' });
    await ds.deleteBodyWeight('05-FEB-2026');
    await ds.addBodyWeight({ date: '05-FEB-2026', weightKg: 79.4, source: 'manual' });

    const { remote, store } = makeMockSheets('sheet-B');
    remote.bodyWeights.push({ date: '05-FEB-2026', weightKg: 78.0, source: 'healthconnect', updatedAt: 9999999999999 });
    ds.store = store;
    await ds.reload();

    assert.strictEqual(ds.state().bodyWeights.length, 1);
    assert.strictEqual(ds.state().bodyWeights[0].weightKg, 79.4);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('DataService updateExercise renames across sets, goals, programs and overwrites Sheets cleanly', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();
    const { remote, calls, store } = makeMockSheets('sheet-C');
    remote.exercises.push({ name: 'Incline Press', category: 'Chest', bodyweight: false, description: 'Old desc' });
    remote.workouts.push({ id: 'w1', date: '01-MAR-2026', title: 'Push', notes: '' });
    remote.sets.push({ id: 's1', workoutId: 'w1', date: '01-MAR-2026', exercise: 'Incline Press', setNo: 1, weight: 70, reps: 8, notes: '' });
    remote.goals.push({ id: 'g1', exerciseName: 'Incline Press', goalType: 'E1RM', targetE1rmKg: 100, sessionReports: [], updatedAt: 10 });
    remote.programs.push({
      id: 'p1', programName: 'Push/Pull', updatedAt: 10,
      days: [{ dayIndex: 0, dayName: 'Push', exercises: [{ exerciseName: 'Incline Press', category: 'Chest', targetSets: 3, targetReps: 8 }] }],
    });

    ds.store = store;
    await ds.reload();

    const st = await ds.updateExercise({
      oldName: 'Incline Press',
      updated: { name: 'Incline Barbell Press', category: 'Chest', bodyweight: false, description: '30 deg' },
    });

    assert.ok(calls.some((c) => c[0] === 'renameExerciseInSets' && c[1] === 'Incline Press' && c[2] === 'Incline Barbell Press'));
    assert.ok(calls.some((c) => c[0] === 'overwriteExercises'));
    assert.strictEqual(remote.exercises.filter((e) => e.name.includes('Incline')).length, 1);
    assert.strictEqual(remote.exercises[0].name, 'Incline Barbell Press');
    assert.strictEqual(remote.sets[0].exercise, 'Incline Barbell Press');
    assert.strictEqual(remote.goals[0].exerciseName, 'Incline Barbell Press');
    assert.strictEqual(remote.programs[0].days[0].exercises[0].exerciseName, 'Incline Barbell Press');
    assert.strictEqual(st.sets[0].exercise, 'Incline Barbell Press');
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('DataService switching to a new spreadsheetId preserves local metadata and copyLocalToSheets pushes all tabs', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();
    const sheetA = makeMockSheets('sheet-A');
    ds.store = sheetA.store;
    await ds.addBodyWeight({ date: '10-MAR-2026', weightKg: 82, source: 'manual' });
    await ds.saveGoal({ id: 'g1', exerciseName: 'Bench Press', goalType: 'E1RM', targetE1rmKg: 120 });
    await ds.createProgram('My Split');
    await ds.saveWorkout({
      workout: { date: '10-MAR-2026', title: 'Upper' },
      sets: [{ exercise: 'Bench Press', setNo: 1, weight: 100, reps: 5 }],
    });

    // Create a workout offline in local store, then switch to sheetB and copyLocalToSheets
    ds.store = ds.local;
    await ds.reload();
    await ds.saveWorkout({
      workout: { date: '11-MAR-2026', title: 'Offline Upper' },
      sets: [{ exercise: 'Bench Press', setNo: 1, weight: 100, reps: 5 }],
    });

    // Switch to a brand new blank sheet B
    const sheetB = makeMockSheets('sheet-B');
    ds.store = sheetB.store;
    await ds.reload();

    // Local bodyWeights/goals/programs must NOT be deleted when switching sheets
    assert.strictEqual(ds.state().bodyWeights.length, 1);
    assert.strictEqual(ds.state().goals.length, 1);
    assert.strictEqual(ds.state().programs.length, 1);
    assert.strictEqual(sheetB.remote.bodyWeights.length, 1);
    assert.strictEqual(sheetB.remote.goals.length, 1);
    assert.strictEqual(sheetB.remote.programs.length, 1);

    // copyLocalToSheets pushes local workouts too
    const res = await ds.copyLocalToSheets();
    assert.strictEqual(res.added, 1);
    assert.strictEqual(sheetB.remote.workouts.length, 1);
    assert.strictEqual(sheetB.remote.sets.length, 1);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('renderer/app.js DOM ID selectors exist in renderer/index.html', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'renderer', 'index.html'), 'utf8');
  const js = fs.readFileSync(path.join(__dirname, '..', 'renderer', 'app.js'), 'utf8');
  const idsInHtml = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map((m) => m[1]));
  const missing = [];
  for (const m of js.matchAll(/\$\(['"]#([a-zA-Z0-9_-]+)['"]\)/g)) {
    const id = m[1];
    // Skip dynamic modal IDs created inside modal() HTML strings
    if (id.startsWith('m-')) continue;
    if (!idsInHtml.has(id)) missing.push(id);
  }
  assert.deepStrictEqual([...new Set(missing)], []);
});

test('DataService saveGoal defaults enabled=true, updateExercise rejects duplicate names, and offline reload preserves local metadata', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();
    const { remote, store } = makeMockSheets('sheet-D');
    ds.store = store;

    // 1. saveGoal explicitly sets enabled: true when omitted
    await ds.saveGoal({ id: 'g-def', exerciseName: 'Bench Press', goalType: 'E1RM', targetE1rmKg: 120 });
    assert.strictEqual(remote.goals[0].enabled, true);

    // 2. updateExercise rejects renaming onto an existing exercise
    await assert.rejects(
      () => ds.updateExercise({ oldName: 'Bench Press', updated: { name: 'Squat', category: 'Legs' } }),
      /already exists/
    );

    // 3. Offline loadAll failure during addBodyWeight keeps local entry in cache and sets error
    store.loadAll = async () => { throw new Error('Network down'); };
    const st = await ds.addBodyWeight({ date: '12-MAR-2026', weightKg: 83.0, source: 'manual' });
    assert.strictEqual(st.bodyWeights.length, 1);
    assert.strictEqual(st.bodyWeights[0].weightKg, 83.0);
    assert.match(st.storage.error, /Network down/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
