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
    trainingMode: null,
    trainingModeUpdatedAt: 0,
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
    async overwriteSettings(trainingMode, updatedAt) {
      calls.push(['overwriteSettings', trainingMode, updatedAt]);
      remote.trainingMode = trainingMode;
      remote.trainingModeUpdatedAt = updatedAt;
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

test('DataService 14-day summary computes metrics deterministically and persists saved summary entries', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();

    await ds.addBodyWeight({ date: '01-NOV-2026', weightKg: 80.0, source: 'manual' });
    await ds.addBodyWeight({ date: '16-NOV-2026', weightKg: 81.0, source: 'manual' });
    await ds.saveWorkout({
      workout: { date: '01-NOV-2026', title: 'Prior Push' },
      sets: [{ exercise: 'Bench Press', setNo: 1, weight: 90, reps: 5 }],
    });
    await ds.saveWorkout({
      workout: { date: '15-NOV-2026', title: 'Recent Upper' },
      sets: [
        { exercise: 'Bench Press', setNo: 1, weight: 100, reps: 5 },
        { exercise: 'Bench Press', setNo: 2, weight: 120, reps: 0 }, // failed -> excluded
        { exercise: 'Pull Ups', setNo: 1, weight: 10, reps: 8 }, // BW on 15-NOV is 80 + 10 = 90 -> E1RM 114.0
      ],
    });

    const summary = ds.get14DaySummary({ referenceDate: '17-NOV-2026' });
    assert.strictEqual(summary.startDate, '04-NOV-2026');
    assert.strictEqual(summary.endDate, '17-NOV-2026');
    assert.strictEqual(summary.workoutsCount, 1);
    assert.strictEqual(summary.priorWorkoutsCount, 1);
    assert.strictEqual(summary.workingSetsCount, 2);
    assert.strictEqual(summary.failedSetsCount, 1);
    assert.strictEqual(summary.totalVolumeKg, 580); // 100*5 + 10*8
    assert.strictEqual(summary.priorVolumeKg, 450);
    assert.strictEqual(summary.heavySets, 1);
    assert.strictEqual(summary.strengthHypSets, 1);
    assert.strictEqual(summary.bodyWeightDeltaKg, 1);
    assert.strictEqual(summary.prsCount, 1); // Bench PR 105 -> 116.5
    assert.ok(summary.overallScore > 0 && summary.overallScore <= 100);

    // Save entry -> persisted in state & disk
    const saved = await ds.saveSummaryEntry({
      title: 'Block 1 Review',
      notes: 'Good bench PR',
      referenceDate: '17-NOV-2026',
    });
    assert.strictEqual(saved.entry.title, 'Block 1 Review');
    assert.strictEqual(saved.state.savedSummaries.length, 1);

    // Reload DataService from same dir -> entry still present
    const ds2 = new DataService(dir);
    await ds2.init();
    assert.strictEqual(ds2.state().savedSummaries.length, 1);
    assert.strictEqual(ds2.state().savedSummaries[0].title, 'Block 1 Review');

    // Delete entry -> removed cleanly
    const afterDel = await ds2.deleteSummaryEntry(saved.entry.id);
    assert.strictEqual(afterDel.savedSummaries.length, 0);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('TrainingMode persists locally, syncs via Settings tab, adapts 14-day summary & evaluates RELATIVE_BW goals', async () => {
  const dir = makeTempDir();
  try {
    const ds = new DataService(dir);
    await ds.init();
    assert.strictEqual(ds.state().trainingMode, 'MAINGAIN');

    const { remote, store } = makeMockSheets('sheet-Mode');
    ds.store = store;

    // 1. Switching to CUT persists locally and pushes Settings tab
    let st = await ds.setTrainingMode('CUT');
    assert.strictEqual(st.trainingMode, 'CUT');
    assert.strictEqual(remote.trainingMode, 'CUT');
    assert.ok(remote.trainingModeUpdatedAt > 0);

    // Reloading offline still restores CUT
    const dsOffline = new DataService(dir);
    await dsOffline.init();
    assert.strictEqual(dsOffline.state().trainingMode, 'CUT');

    // Remote newer BULK wins on Sheets sync
    remote.trainingMode = 'BULK';
    remote.trainingModeUpdatedAt = Date.now() + 5000;
    await ds.reload();
    assert.strictEqual(ds.state().trainingMode, 'BULK');

    // 2. Cut summary recognizes mild <5% dip as retention & rewards Rel PR
    await ds.setTrainingMode('CUT');
    await ds.addBodyWeight({ date: '01-NOV-2026', weightKg: 90.0, source: 'manual' });
    await ds.addBodyWeight({ date: '15-NOV-2026', weightKg: 84.0, source: 'manual' });
    await ds.saveWorkout({
      workout: { date: '01-NOV-2026', title: 'Prior Cut Upper' },
      sets: [{ exercise: 'Bench Press', setNo: 1, weight: 100, reps: 5 }], // E1RM ~116.5kg (1.29x BW @ 90kg)
    });
    await ds.saveWorkout({
      workout: { date: '15-NOV-2026', title: 'Cut Upper' },
      sets: [{ exercise: 'Bench Press', setNo: 1, weight: 97.5, reps: 5 }], // E1RM ~113.5kg (~97% held, 1.35x BW -> REL PR)
    });

    const cutSummary = ds.get14DaySummary({ referenceDate: '17-NOV-2026', trainingMode: 'CUT' });
    assert.strictEqual(cutSummary.trainingMode, 'CUT');
    assert.strictEqual(cutSummary.regressingCount, 0);
    assert.strictEqual(cutSummary.retainedCount, 1);
    assert.strictEqual(cutSummary.relPrsCount, 1);
    assert.ok(cutSummary.lifts[0].retentionPct >= 95);
    assert.strictEqual(cutSummary.lifts[0].isAllTimeRelPr, true);

    const bulkSummary = ds.get14DaySummary({ referenceDate: '17-NOV-2026', trainingMode: 'BULK' });
    assert.strictEqual(bulkSummary.trainingMode, 'BULK');
    assert.strictEqual(bulkSummary.regressingCount, 1);

    // 3. RELATIVE_BW goal updates dynamically against bodyweight on workout save
    await ds.saveGoal({
      id: 'g-rel-bench',
      exerciseName: 'Bench Press',
      goalType: 'RELATIVE_BW',
      targetRelativeBw: 1.40, // @ 84kg -> 117.6kg target
      baselineE1rmKg: 113.5,
      enabled: true,
    });
    const saveRes = await ds.saveWorkout({
      workout: { date: '16-NOV-2026', title: 'Bench PR' },
      sets: [{ exercise: 'Bench Press', setNo: 1, weight: 102.5, reps: 5 }], // E1RM ~119.5kg >= 117.6 -> achieved!
    });
    const savedGoal = saveRes.state.goals.find((g) => g.id === 'g-rel-bench');
    assert.ok(savedGoal.achievedDate);
    assert.strictEqual(savedGoal.sessionReports.at(-1).achievedGoal, true);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
