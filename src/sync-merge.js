// Pure merge helpers for syncing BodyWeights / Goals / Programs with Google Sheets.
// Mirrors mobile-android/.../util/SyncMerge.kt so both clients resolve conflicts identically.
//
// Per key:
//  - deleted locally while offline        -> dropped everywhere
//  - present on both sides                -> resolve() picks the winner
//  - local only, key seen at last sync    -> another device deleted it -> dropped
//  - local only, key NOT seen at last sync-> new local item -> kept (and pushed)
//  - remote only                          -> kept (new from another device)

const MONTHS = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];

/** Sortable YYYY-MM-DD for either DD-MON-YYYY or ISO date strings ('' when unparsable). */
function isoKey(d) {
  const s = String(d || '').trim();
  const m = s.match(/^(\d{1,2})-([A-Za-z]{3})-(\d{4})$/);
  if (m) {
    const mi = MONTHS.indexOf(m[2].toUpperCase());
    if (mi >= 0) return `${m[3]}-${String(mi + 1).padStart(2, '0')}-${m[1].padStart(2, '0')}`;
  }
  const iso = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (iso) return `${iso[1]}-${iso[2].padStart(2, '0')}-${iso[3].padStart(2, '0')}`;
  return '';
}

function mergeByKey({ local, remote, key, deletedLocally = [], lastSynced = [], resolve }) {
  const del = new Set(deletedLocally);
  const seen = new Set(lastSynced);
  const lm = new Map();
  local.forEach((x) => lm.set(key(x), x));
  const rm = new Map();
  remote.forEach((x) => rm.set(key(x), x));
  const out = [];
  for (const k of new Set([...lm.keys(), ...rm.keys()])) {
    if (del.has(k)) continue;
    const l = lm.get(k);
    const r = rm.get(k);
    if (l && r) out.push(resolve(l, r));
    else if (l) { if (!seen.has(k)) out.push(l); }
    else if (r) out.push(r);
  }
  return out;
}

const stamp = (x) => Number(x && x.updatedAt) || 0;

function resolveBodyWeight(l, r) {
  const lm = (l.source || 'manual') === 'manual';
  const rmn = (r.source || 'manual') === 'manual';
  if (lm && !rmn) return l;
  if (rmn && !lm) return r;
  return stamp(l) >= stamp(r) ? l : r;
}

function resolveGoal(l, r) {
  if (stamp(l) > stamp(r)) return l;
  if (stamp(r) > stamp(l)) return r;
  return (l.sessionReports || []).length >= (r.sessionReports || []).length ? l : r;
}

function resolveProgram(l, r) {
  return stamp(l) >= stamp(r) ? l : r;
}

function mergeBodyWeights(local, remote, deletedLocally, lastSynced) {
  return mergeByKey({
    local, remote, key: (b) => isoKey(b.date), deletedLocally, lastSynced, resolve: resolveBodyWeight,
  }).sort((a, b) => isoKey(a.date).localeCompare(isoKey(b.date)));
}

function mergeGoals(local, remote, deletedLocally, lastSynced) {
  return mergeByKey({ local, remote, key: (g) => g.id, deletedLocally, lastSynced, resolve: resolveGoal });
}

function mergePrograms(local, remote, deletedLocally, lastSynced) {
  return mergeByKey({ local, remote, key: (p) => p.id, deletedLocally, lastSynced, resolve: resolveProgram });
}

/** JSON with sorted keys so equal objects compare equal regardless of property order. */
function stable(value) {
  if (Array.isArray(value)) return `[${value.map(stable).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map((k) => `${JSON.stringify(k)}:${stable(value[k])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

module.exports = {
  isoKey, mergeByKey, mergeBodyWeights, mergeGoals, mergePrograms,
  resolveBodyWeight, resolveGoal, resolveProgram, stable,
};
