// Run with: node --test tests/
const test = require('node:test');
const assert = require('node:assert');
const SM = require('../src/sync-merge');

test('isoKey parses DD-MON-YYYY and ISO', () => {
  assert.strictEqual(SM.isoKey('05-JAN-2026'), '2026-01-05');
  assert.strictEqual(SM.isoKey('2026-01-05'), '2026-01-05');
  assert.strictEqual(SM.isoKey('garbage'), '');
});

test('manual body weight beats automatic on same date', () => {
  const m = SM.mergeBodyWeights(
    [{ date: '05-JAN-2026', weightKg: 80, source: 'manual', updatedAt: 1 }],
    [{ date: '05-JAN-2026', weightKg: 81, source: 'healthconnect', updatedAt: 999 }], [], []);
  assert.strictEqual(m.length, 1);
  assert.strictEqual(m[0].weightKg, 80);
});

test('newer edit wins for same source', () => {
  const m = SM.mergeBodyWeights(
    [{ date: '05-JAN-2026', weightKg: 80, source: 'manual', updatedAt: 10 }],
    [{ date: '2026-01-05', weightKg: 79, source: 'manual', updatedAt: 20 }], [], []);
  assert.strictEqual(m[0].weightKg, 79);
});

test('local delete is dropped, remote delete is not resurrected, new local kept', () => {
  const e1 = { date: '01-JAN-2026', weightKg: 80 };
  const e2 = { date: '02-JAN-2026', weightKg: 81 };
  const e3 = { date: '03-JAN-2026', weightKg: 82 };
  const m = SM.mergeBodyWeights([e2, e3], [e1], ['2026-01-01'], ['2026-01-01', '2026-01-02']);
  assert.deepStrictEqual(m.map((b) => SM.isoKey(b.date)), ['2026-01-03']);
});

test('goals: newer wins, remote-only kept; programs merge by id', () => {
  const g = SM.mergeGoals(
    [{ id: 'g1', targetE1rmKg: 150, updatedAt: 5 }],
    [{ id: 'g1', targetE1rmKg: 160, updatedAt: 9 }, { id: 'g2', updatedAt: 1 }], [], []);
  assert.strictEqual(g.find((x) => x.id === 'g1').targetE1rmKg, 160);
  assert.strictEqual(g.length, 2);
  const p = SM.mergePrograms(
    [{ id: 'p1', programName: 'A', updatedAt: 50 }],
    [{ id: 'p1', programName: 'B', updatedAt: 10 }, { id: 'p2', programName: 'C', updatedAt: 1 }], [], []);
  assert.strictEqual(p.find((x) => x.id === 'p1').programName, 'A');
  assert.strictEqual(p.length, 2);
});

test('stable stringify ignores key order', () => {
  assert.strictEqual(SM.stable({ a: 1, b: [1, { c: 2, d: 3 }] }), SM.stable({ b: [1, { d: 3, c: 2 }], a: 1 }));
});
