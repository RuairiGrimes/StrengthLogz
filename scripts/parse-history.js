// Dry-run: parse data/history.txt and print a summary (no data is written).
const { parseHistory } = require('../src/history-parser');

const { workouts, sets } = parseHistory();
const byExercise = {};
for (const s of sets) byExercise[s.exercise] = (byExercise[s.exercise] || 0) + 1;

console.log(`Workouts: ${workouts.length}`);
console.log(`Sets:     ${sets.length}`);
console.log(`Range:    ${workouts[0].date} -> ${workouts[workouts.length - 1].date}`);
console.log(`Exercises (${Object.keys(byExercise).length}):`);
Object.entries(byExercise)
  .sort((a, b) => b[1] - a[1])
  .forEach(([name, n]) => console.log(`  ${String(n).padStart(4)}  ${name}`));

const catalog = require('../data/exercises.json').map((e) => e.name);
const missing = Object.keys(byExercise).filter((n) => !catalog.includes(n));
if (missing.length) console.log('\nNot in exercises.json:', missing);

