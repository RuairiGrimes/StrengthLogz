'use strict';
/* global Chart */

// ---------------------------------------------------------------- helpers
const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmt = (n) => (n === null || n === undefined || Number.isNaN(n) ? '–' : String(+(+n).toFixed(2)));
const round = (n, step = 0.5) => Math.round(n / step) * step;
const MONTHS_SHORT = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
const toDate = (d) => {
  const s = String(d || '').trim();
  const m = s.match(/^(\d{1,2})-([A-Za-z]{3})-(\d{4})$/);
  if (m) {
    const mi = MONTHS_SHORT.indexOf(m[2].toUpperCase());
    if (mi >= 0) return new Date(Number(m[3]), mi, Number(m[1]));
  }
  return new Date(s + 'T00:00:00');
};
const dayMs = 86400000;
const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const fmtDdMonYyyy = (d) => {
  const dt = toDate(d);
  if (Number.isNaN(dt.getTime())) return String(d || '');
  return `${String(dt.getDate()).padStart(2, '0')}-${MONTHS_SHORT[dt.getMonth()]}-${dt.getFullYear()}`;
};
const fmtDate = (d) => fmtDdMonYyyy(d);
const shortDate = (d) => fmtDdMonYyyy(d);

/** Epley estimated one-rep max. */
const epley = (w, r) => (r === 1 ? w : w * (1 + r / 30));

const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
const COLORS = { up: css('--up'), down: css('--down'), pr: css('--pr'), accent: css('--accent'), muted: css('--muted'), border: css('--border') };

// ---------------------------------------------------------------- state
let S = { workouts: [], sets: [], exercises: [], storage: {} };
const idx = { byW: new Map(), byE: new Map(), exMap: new Map(), workouts: [] };

function setState(st) {
  S = st;
  buildIndex();
  renderAll();
}

function buildIndex() {
  idx.byW = new Map();
  idx.byE = new Map();
  idx.exMap = new Map(S.exercises.map((e) => [e.name.toLowerCase(), e]));
  for (const s of S.sets) {
    if (!idx.byW.has(s.workoutId)) idx.byW.set(s.workoutId, []);
    idx.byW.get(s.workoutId).push(s);
    const k = s.exercise.toLowerCase();
    if (!idx.byE.has(k)) idx.byE.set(k, []);
    idx.byE.get(k).push(s);
  }
  idx.workouts = [...S.workouts].sort((a, b) => {
    const dtDiff = toDate(b.date) - toDate(a.date);
    return dtDiff !== 0 ? dtDiff : (a.id < b.id ? 1 : -1);
  });
}

const exInfo = (name) => idx.exMap.get((name || '').toLowerCase()) || { name, category: 'Other' };
const isBW = (name) => !!exInfo(name).bodyweight;

// ---------------------------------------------------------------- formatting sets
function fmtWeight(name, w) {
  if (w === null || w === undefined) return '?';
  if (isBW(name)) return w === 0 ? 'BW' : `BW+${fmt(w)}`;
  return fmt(w);
}

/** Formatted summary, e.g. "3 x 10 @ 10kg" or "1 x 10 @ 10kg, 2 x 9 @ 10kg". */
function summarize(name, sets) {
  const groups = [];
  for (const s of sets) {
    const g = groups[groups.length - 1];
    if (g && g.w === s.weight && g.r === s.reps) g.n++;
    else groups.push({ w: s.weight, r: s.reps, n: 1 });
  }
  const bw = isBW(name);
  return groups.map(({ w, r, n }) => {
    if (w === null && r === null) return `${n} set${n > 1 ? 's' : ''}`;
    const reps = r === null ? '?' : r;
    if (w === null) return `${n} x ${reps}`;
    let wStr = '';
    if (bw) {
      wStr = w === 0 ? 'BW' : `BW+${fmt(w)}kg`;
    } else {
      wStr = `${fmt(w)}kg`;
    }
    return `${n} x ${reps} @ ${wStr}`;
  }).join(', ');
}

/** Group a workout's sets by exercise, preserving order. */
function groupByExercise(sets) {
  const map = new Map();
  for (const s of sets) {
    if (!map.has(s.exercise)) map.set(s.exercise, []);
    map.get(s.exercise).push(s);
  }
  for (const arr of map.values()) arr.sort((a, b) => (a.setNo || 0) - (b.setNo || 0));
  return map;
}

// ---------------------------------------------------------------- sessions / metrics
function sessionsFor(name, excludeWorkoutId = null) {
  const sets = idx.byE.get((name || '').toLowerCase()) || [];
  const byW = new Map();
  for (const s of sets) {
    if (s.workoutId === excludeWorkoutId) continue;
    if (!byW.has(s.workoutId)) byW.set(s.workoutId, { workoutId: s.workoutId, date: s.date, sets: [] });
    byW.get(s.workoutId).sets.push(s);
  }
  const bw = isBW(name);
  const out = [...byW.values()].map((sess) => {
    sess.sets.sort((a, b) => (a.setNo || 0) - (b.setNo || 0));
    let e1rm = null, top = null, volume = 0, maxReps = null, totalReps = 0, best = null;
    for (const s of sess.sets) {
      const w = s.weight, r = s.reps;
      if (r !== null) { totalReps += r; maxReps = Math.max(maxReps ?? 0, r); }
      if (w !== null && (r === null || r > 0)) top = Math.max(top ?? -Infinity, w);
      if (w !== null && r !== null && r > 0) {
        volume += w * r;
        const e = epley(w, r);
        if (e1rm === null || e > e1rm) { e1rm = e; best = s; }
      }
    }
    const w = idx.workouts.find((x) => x.id === sess.workoutId);
    return {
      ...sess, t: toDate(sess.date).getTime(), title: w ? w.title : '',
      e1rm: bw || e1rm === null ? null : round(e1rm), best, top,
      volume: bw ? (totalReps || null) : (volume || null), maxReps, totalReps,
    };
  });
  return out.sort((a, b) => a.t - b.t);
}

function metricsFor(name) {
  const bw = isBW(name);
  const m = {
    e1rm: { label: 'Est. 1RM', unit: 'kg', get: (s) => s.e1rm },
    top: { label: bw ? 'Top added weight' : 'Top weight', unit: 'kg', get: (s) => s.top },
    volume: { label: bw ? 'Total reps' : 'Volume', unit: bw ? 'reps' : 'kg', get: (s) => s.volume },
    reps: { label: 'Max reps', unit: 'reps', get: (s) => s.maxReps },
  };
  if (bw) delete m.e1rm;
  return m;
}

function defaultMetric(name, sessions) {
  const m = metricsFor(name);
  const order = isBW(name) ? ['reps', 'top', 'volume'] : ['e1rm', 'top', 'reps', 'volume'];
  return order.find((k) => m[k] && sessions.some((s) => m[k].get(s) !== null)) || order[0];
}

// ---------------------------------------------------------------- ui utilities
let toastTimer;
function toast(msg, kind = '') {
  const t = $('#toast');
  t.textContent = msg;
  t.className = `toast show ${kind}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { t.className = 'toast'; }, kind === 'error' ? 6000 : 3200);
}

async function busy(fn) {
  $('#busy').classList.remove('hidden');
  try { return await fn(); } catch (err) { toast(err.message, 'error'); throw err; } finally { $('#busy').classList.add('hidden'); }
}

function modal({ title, html, okText = 'OK', danger = false }) {
  return new Promise((resolve) => {
    const m = $('#modal');
    $('#modal-title').textContent = title;
    $('#modal-body').innerHTML = html;
    const ok = $('#modal-ok');
    ok.textContent = okText;
    ok.style.background = danger ? COLORS.down : '';
    ok.style.borderColor = danger ? COLORS.down : '';
    m.classList.remove('hidden');
    const first = $('#modal-body input');
    if (first) setTimeout(() => first.focus(), 0);
    const close = (val) => {
      m.classList.add('hidden');
      ok.onclick = null; $('#modal-cancel').onclick = null; m.onkeydown = null;
      resolve(val);
    };
    ok.onclick = () => close($('#modal-body'));
    $('#modal-cancel').onclick = () => close(null);
    m.onkeydown = (e) => {
      if (e.key === 'Escape') close(null);
      if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA') { e.preventDefault(); close($('#modal-body')); }
    };
  });
}

function showView(name) {
  $$('.nav-btn').forEach((b) => b.classList.toggle('active', b.dataset.view === name || (name === 'workout-detail' && b.dataset.view === 'history') || (name === 'goals' && b.dataset.view === 'progress')));
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${name}`));
  if (name === 'progress') renderProgress();
  if (name === 'goals') renderGoals();
  if (name === 'suggest') renderSuggest();
  if (name === 'history') renderHistory();
}

// ================================================================ LOG VIEW
const DRAFT_KEY = 'strengthlogz.draft';
const newDraft = () => ({ date: today(), title: '', notes: '', exercises: [], replaceId: null });
let draft = (() => { try { return JSON.parse(localStorage.getItem(DRAFT_KEY)) || newDraft(); } catch { return newDraft(); } })();
const saveDraft = () => localStorage.setItem(DRAFT_KEY, JSON.stringify(draft));

const wToInput = (name, w) => (w === null || w === undefined ? '' : isBW(name) && w === 0 ? 'BW' : fmt(w));
function parseWeight(v) {
  v = String(v).trim().toLowerCase().replace(/kg$/, '').trim();
  if (!v) return null;
  if (v === 'bw') return 0;
  const m = v.match(/^bw\s*\+\s*([\d.]+)$/);
  if (m) return Number(m[1]);
  const n = Number(v.replace(',', '.'));
  return Number.isFinite(n) ? n : NaN;
}
function parseReps(v) {
  v = String(v).trim();
  if (!v) return null;
  const n = Number(v);
  return Number.isInteger(n) && n >= 0 ? n : NaN;
}

function renderLog() {
  $('#log-date').value = draft.date;
  $('#log-title').value = draft.title;
  $('#log-notes').value = draft.notes;
  const editing = draft.replaceId && S.workouts.find((w) => w.id === draft.replaceId);
  $('#log-heading').textContent = editing ? `Editing ${fmtDate(editing.date)}` : 'Log Workout';
  $('#log-save').textContent = editing ? 'Save changes' : 'Save workout';

  const host = $('#log-exercises');
  if (!draft.exercises.length) {
    host.innerHTML = `<div class="empty">No exercises yet. Add one below, or hit <b>Repeat</b> on a past workout in History.</div>`;
  } else {
    host.innerHTML = draft.exercises.map((ex, ei) => exerciseCardHtml(ex, ei)).join('');
  }
  renderRecentChips();
}

function exerciseCardHtml(ex, ei) {
  const info = exInfo(ex.name);
  const bw = !!info.bodyweight;
  const sessions = sessionsFor(ex.name, draft.replaceId);
  const last = sessions[sessions.length - 1];
  const bestE = sessions.reduce((m, s) => (s.e1rm !== null && (m === null || s.e1rm > m.e1rm) ? s : m), null);
  const hints = [];
  if (last) hints.push(`Last (${shortDate(last.date)}): <b>${esc(summarize(ex.name, last.sets))}</b>`);
  if (bestE) hints.push(`Best e1RM: <b>${fmt(bestE.e1rm)} kg</b> (${shortDate(bestE.date)})`);
  if (!last) hints.push('First time logging this one 💪');

  const rows = ex.sets.map((s, si) => `
    <tr data-ei="${ei}" data-si="${si}">
      <td class="n">${si + 1}</td>
      <td class="w"><input data-f="weight" inputmode="decimal" value="${esc(s.weight)}" placeholder="${bw ? 'BW / BW+10' : 'kg'}"></td>
      <td class="r"><input data-f="reps" inputmode="numeric" value="${esc(s.reps)}" placeholder="reps"></td>
      <td><input data-f="notes" value="${esc(s.notes)}" placeholder="notes (optional)"></td>
      <td class="e1rm">${e1rmCell(ex.name, s)}</td>
      <td class="x"><button class="icon" data-act="del-set" title="Remove set">✕</button></td>
    </tr>`).join('');

  const activeGoal = getActiveGoalForExercise(ex.name);
  const goalEval = activeGoal ? evaluateGoalDesktop(activeGoal) : null;

  return `
  <div class="card ex-card" data-ei="${ei}">
    <div class="ex-head">
      <span class="ex-name" data-act="goto-progress" title="View progress">${esc(ex.name)}</span>
      <span class="tag">${esc(info.category || 'Other')}${bw ? ' · bodyweight' : ''}</span>
      ${activeGoal ? `<button class="primary" data-act="goal-progress" style="padding:4px 10px;font-size:12px;background:rgba(255,209,102,0.16);border-color:var(--pr);color:var(--pr)">🎯 Goal Progress</button>` : ''}
      <span class="spacer"></span>
      <button class="icon" data-act="move-up" title="Move up">↑</button>
      <button class="icon" data-act="move-down" title="Move down">↓</button>
      <button class="icon" data-act="del-ex" title="Remove exercise">✕</button>
    </div>
    <div class="ex-hint">${hints.join(' &nbsp;·&nbsp; ')}</div>
    ${goalEval ? `<div style="margin:6px 0 8px;padding:8px 10px;border-radius:8px;background:rgba(255,209,102,0.08);border:1px solid rgba(255,209,102,0.3);display:flex;justify-content:space-between;align-items:center;font-size:12px">
      <span><b style="color:var(--pr)">🎯 Goal (${esc(goalEval.targetSummary)}):</b> Aim today for <b>${esc(goalEval.nextSessionSummary)}</b> (${goalEval.progressPct}% to goal)</span>
      <button class="ghost" data-act="goal-report" style="padding:2px 8px;font-size:11px;color:var(--pr)">Report →</button>
    </div>` : ''}
    <table class="sets">
      <thead><tr><th>Set</th><th>${bw ? 'Added kg' : 'Weight kg'}</th><th>Reps</th><th>Notes</th><th>${bw ? '' : 'e1RM'}</th><th></th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
    <div class="ex-foot">
      <button data-act="add-set">+ Add set</button>
      ${activeGoal ? `<button class="ghost" data-act="goal-autofill" style="margin-left:8px;color:var(--pr);border-color:rgba(255,209,102,0.35)">⚡ Auto-Fill Goal Sets</button>` : ''}
    </div>
  </div>`;
}

function e1rmCell(name, s) {
  if (isBW(name)) return '';
  const w = parseWeight(s.weight), r = parseReps(s.reps);
  if (!(w > 0) || !(r > 0)) return '';
  return `≈ ${fmt(round(epley(w, r)))}`;
}

function renderRecentChips() {
  const inDraft = new Set(draft.exercises.map((e) => e.name.toLowerCase()));
  const seen = [];
  for (const w of idx.workouts) {
    for (const s of idx.byW.get(w.id) || []) {
      if (!seen.includes(s.exercise) && !inDraft.has(s.exercise.toLowerCase())) seen.push(s.exercise);
    }
    if (seen.length >= 14) break;
  }
  $('#recent-chips').innerHTML = seen.slice(0, 14).map((n) => `<button class="chip" data-name="${esc(n)}">${esc(n)}</button>`).join('')
    || '<span class="muted small">Nothing logged yet.</span>';
}

async function addExerciseToDraft(rawName) {
  let name = (rawName || '').trim();
  if (!name) return;
  const existing = idx.exMap.get(name.toLowerCase());
  if (existing) {
    name = existing.name;
  } else {
    const created = await createExerciseModal(name);
    if (!created) return;
    name = created;
  }
  const dup = draft.exercises.findIndex((e) => e.name.toLowerCase() === name.toLowerCase());
  if (dup >= 0) { focusSet(dup, draft.exercises[dup].sets.length - 1, 'reps'); return; }

  // Pre-fill with last session's sets so you only need to tweak.
  const sessions = sessionsFor(name, draft.replaceId);
  const last = sessions[sessions.length - 1];
  const sets = last && last.sets.some((s) => s.weight !== null || s.reps !== null)
    ? last.sets.map((s) => ({ weight: wToInput(name, s.weight), reps: s.reps === null ? '' : String(s.reps), notes: '' }))
    : [{ weight: '', reps: '', notes: '' }];
  draft.exercises.push({ name, sets });
  saveDraft();
  renderLog();
  $('#add-ex-input').value = '';
  focusSet(draft.exercises.length - 1, 0, 'weight');
}

async function createExerciseModal(name) {
  const cats = [...new Set(S.exercises.map((e) => e.category).filter(Boolean)), 'Other'];
  const body = await modal({
    title: 'Create new exercise',
    okText: 'Create',
    html: `
      <label>Name<input id="nx-name" value="${esc(name)}"></label>
      <label>Category<select id="nx-cat">${[...new Set(cats)].map((c) => `<option>${esc(c)}</option>`).join('')}</select></label>
      <label class="check"><input type="checkbox" id="nx-bw"> Bodyweight exercise (log added weight, BW = 0)</label>`,
  });
  if (!body) return null;
  const ex = { name: $('#nx-name', body).value.trim(), category: $('#nx-cat', body).value, bodyweight: $('#nx-bw', body).checked };
  if (!ex.name) return null;
  const st = await busy(() => window.api.addExercise(ex));
  setState(st);
  toast(`Created "${ex.name}"`, 'success');
  return ex.name;
}

function focusSet(ei, si, field) {
  const el = $(`tr[data-ei="${ei}"][data-si="${si}"] input[data-f="${field}"]`);
  if (el) { el.focus(); el.select(); el.scrollIntoView({ block: 'nearest' }); }
}

function addSet(ei, focusField = 'reps') {
  const ex = draft.exercises[ei];
  const prev = ex.sets[ex.sets.length - 1];
  ex.sets.push(prev ? { weight: prev.weight, reps: prev.reps, notes: '' } : { weight: '', reps: '', notes: '' });
  saveDraft();
  renderLog();
  focusSet(ei, ex.sets.length - 1, focusField);
}

function bindLog() {
  $('#log-date').addEventListener('change', (e) => { draft.date = e.target.value; saveDraft(); });
  $('#log-title').addEventListener('input', (e) => { draft.title = e.target.value; saveDraft(); });
  $('#log-notes').addEventListener('input', (e) => { draft.notes = e.target.value; saveDraft(); });

  $('#add-ex-btn').addEventListener('click', () => addExerciseToDraft($('#add-ex-input').value));
  $('#add-ex-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); addExerciseToDraft(e.target.value); } });
  $('#recent-chips').addEventListener('click', (e) => { const c = e.target.closest('.chip'); if (c) addExerciseToDraft(c.dataset.name); });

  const host = $('#log-exercises');
  host.addEventListener('input', (e) => {
    const tr = e.target.closest('tr[data-ei]');
    if (!tr || !e.target.dataset.f) return;
    const ei = +tr.dataset.ei, si = +tr.dataset.si;
    const set = draft.exercises[ei].sets[si];
    set[e.target.dataset.f] = e.target.value;
    $('.e1rm', tr).textContent = e1rmCell(draft.exercises[ei].name, set);
    saveDraft();
  });
  host.addEventListener('keydown', (e) => {
    if (e.key !== 'Enter') return;
    const tr = e.target.closest('tr[data-ei]');
    if (!tr) return;
    e.preventDefault();
    const ei = +tr.dataset.ei, si = +tr.dataset.si;
    const field = e.target.dataset.f === 'notes' ? 'reps' : e.target.dataset.f;
    if (si === draft.exercises[ei].sets.length - 1) addSet(ei, field);
    else focusSet(ei, si + 1, field);
  });
  host.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-act]');
    if (!btn) return;
    const card = btn.closest('.ex-card');
    const ei = +card.dataset.ei;
    const act = btn.dataset.act;
    if (act === 'add-set') return addSet(ei);
    if (act === 'goto-progress') { openProgress(draft.exercises[ei].name); return; }
    if (act === 'goal-progress') { openGoalProgressModal(ei); return; }
    if (act === 'goal-report') {
      const g = getActiveGoalForExercise(draft.exercises[ei].name);
      if (g) openExerciseGoalReportModal(g.id);
      return;
    }
    if (act === 'goal-autofill') {
      const g = getActiveGoalForExercise(draft.exercises[ei].name);
      if (g) {
        const ev = evaluateGoalDesktop(g);
        draft.exercises[ei].sets = Array.from({ length: ev.nextSets }, () => ({
          weight: wToInput(draft.exercises[ei].name, ev.nextWeight),
          reps: String(ev.nextReps),
          notes: '',
        }));
        saveDraft();
        renderLog();
        toast(`Auto-filled ${ev.nextSessionSummary} for ${draft.exercises[ei].name}`, 'success');
      }
      return;
    }
    if (act === 'del-set') {
      const si = +btn.closest('tr').dataset.si;
      draft.exercises[ei].sets.splice(si, 1);
      if (!draft.exercises[ei].sets.length) draft.exercises.splice(ei, 1);
    }
    if (act === 'del-ex') draft.exercises.splice(ei, 1);
    if (act === 'move-up' && ei > 0) [draft.exercises[ei - 1], draft.exercises[ei]] = [draft.exercises[ei], draft.exercises[ei - 1]];
    if (act === 'move-down' && ei < draft.exercises.length - 1) [draft.exercises[ei + 1], draft.exercises[ei]] = [draft.exercises[ei], draft.exercises[ei + 1]];
    saveDraft();
    renderLog();
  });

  $('#log-save').addEventListener('click', saveWorkout);
  $('#log-discard').addEventListener('click', async () => {
    if (draft.exercises.length && !(await modal({ title: 'Discard this workout?', html: '<p class="muted">Unsaved sets will be lost.</p>', okText: 'Discard', danger: true }))) return;
    draft = newDraft();
    saveDraft();
    renderLog();
  });
}

async function saveWorkout() {
  if (!draft.date) return toast('Pick a date', 'error');
  const sets = [];
  for (const ex of draft.exercises) {
    let n = 0;
    for (const s of ex.sets) {
      const weight = parseWeight(s.weight), reps = parseReps(s.reps);
      if (Number.isNaN(weight)) return toast(`${ex.name}: "${s.weight}" isn't a valid weight`, 'error');
      if (Number.isNaN(reps)) return toast(`${ex.name}: "${s.reps}" isn't a valid rep count`, 'error');
      if (weight === null && reps === null && !String(s.notes).trim()) continue;
      sets.push({ exercise: ex.name, setNo: ++n, weight, reps, notes: String(s.notes || '').trim() });
    }
  }
  if (!sets.length) return toast('Nothing to save yet — add some sets', 'error');

  // PR detection before saving
  const prs = [];
  for (const name of new Set(sets.map((s) => s.exercise))) {
    if (isBW(name)) continue;
    const prevBest = Math.max(0, ...sessionsFor(name, draft.replaceId).map((s) => s.e1rm || 0));
    const nowBest = Math.max(0, ...sets.filter((s) => s.exercise === name && s.weight > 0 && s.reps > 0).map((s) => epley(s.weight, s.reps)));
    if (prevBest > 0 && nowBest > prevBest) prs.push(`${name} (${fmt(round(nowBest))} kg e1RM)`);
  }

  const wasEdit = !!draft.replaceId;
  const workoutDate = draft.date;
  const st = await busy(() => window.api.saveWorkout({
    workout: { date: draft.date, title: draft.title.trim(), notes: draft.notes.trim() },
    sets,
    replaceId: draft.replaceId,
  }));
  draft = newDraft();
  saveDraft();
  setState(st);

  const updatedGoals = updateGoalsFromLoggedWorkout(workoutDate, sets);
  if (updatedGoals.length > 0) {
    toast(`🎯 Goal Updated for ${updatedGoals.map((g) => g.exercise).join(', ')}! Opening Goal Report...`, 'success');
    openExerciseGoalReportModal(updatedGoals[0].id);
  } else {
    toast(prs.length ? `🏆 New PR: ${prs.join(', ')}` : `${wasEdit ? 'Updated' : 'Saved'} ✓ ${sets.length} sets`, 'success');
  }
}

function loadIntoDraft(workoutId, { asEdit }) {
  const w = S.workouts.find((x) => x.id === workoutId);
  if (!w) return;
  const groups = groupByExercise(idx.byW.get(w.id) || []);
  draft = {
    date: asEdit ? w.date : today(),
    title: w.title || '',
    notes: asEdit ? w.notes || '' : '',
    replaceId: asEdit ? w.id : null,
    exercises: [...groups].map(([name, sets]) => ({
      name,
      sets: sets.map((s) => ({ weight: wToInput(name, s.weight), reps: s.reps === null ? '' : String(s.reps), notes: asEdit ? s.notes || '' : '' })),
    })),
  };
  saveDraft();
  renderLog();
  showView('log');
}

// ================================================================ HISTORY VIEW
function renderHistory() {
  const q = $('#history-search').value.trim().toLowerCase();
  const list = idx.workouts.filter((w) => {
    if (!q) return true;
    if ((w.title || '').toLowerCase().includes(q) || (w.notes || '').toLowerCase().includes(q)) return true;
    return (idx.byW.get(w.id) || []).some((s) => s.exercise.toLowerCase().includes(q) || (s.notes || '').toLowerCase().includes(q));
  });
  $('#history-sub').textContent = `${S.workouts.length} workouts · ${S.sets.length} sets logged${q ? ` · ${list.length} matching` : ''}`;
  if (!list.length) {
    $('#history-list').innerHTML = `<div class="empty">${S.workouts.length ? 'No workouts match that filter.' : 'No workouts yet. Log one, or import your historical notes from Settings.'}</div>`;
    return;
  }
  let month = '';
  const html = [];
  for (const w of list) {
    const m = fmtDate(w.date, { month: 'long', year: 'numeric' });
    if (m !== month) { month = m; html.push(`<div class="month">${esc(m)}</div>`); }
    const groups = groupByExercise(idx.byW.get(w.id) || []);
    const exLines = [...groups].map(([name, sets]) => {
      const notes = [...new Set(sets.map((s) => s.notes).filter(Boolean))].join('; ');
      return `<div class="w-ex"><span class="nm" data-ex="${esc(name)}">${esc(name)}</span><span class="st">${esc(summarize(name, sets))}${notes ? ` <i>— ${esc(notes)}</i>` : ''}</span></div>`;
    }).join('');
    html.push(`
      <div class="card w-card" data-id="${esc(w.id)}">
        <div class="w-date"><div class="d">${toDate(w.date).getDate()}</div><div class="dw">${esc(fmtDate(w.date, { weekday: 'long', month: 'short' }))}</div></div>
        <div>
          ${w.title ? `<div class="w-title">${esc(w.title)}</div>` : ''}
          ${w.notes ? `<div class="w-notes">${esc(w.notes)}</div>` : ''}
          ${exLines}
        </div>
        <div class="w-actions">
          <button data-act="repeat" title="Start a new workout with these exercises">Repeat</button>
          <button data-act="edit">Edit</button>
          <button data-act="delete" class="danger">Delete</button>
        </div>
      </div>`);
  }
  $('#history-list').innerHTML = html.join('');
}

function bindHistory() {
  $('#history-search').addEventListener('input', renderHistory);
  $('#history-list').addEventListener('click', async (e) => {
    const nm = e.target.closest('[data-ex]');
    if (nm) return openProgress(nm.dataset.ex);
    const btn = e.target.closest('[data-act]');
    if (btn) {
      const id = btn.closest('.w-card').dataset.id;
      const w = S.workouts.find((x) => x.id === id);
      if (btn.dataset.act === 'repeat' || btn.dataset.act === 'edit') {
        if (draft.exercises.length && !(await modal({ title: 'Replace current draft?', html: '<p class="muted">You have an unsaved workout in progress.</p>', okText: 'Replace', danger: true }))) return;
        loadIntoDraft(id, { asEdit: btn.dataset.act === 'edit' });
      }
      if (btn.dataset.act === 'delete') {
        if (!(await modal({ title: 'Delete workout?', html: `<p class="muted">${esc(fmtDate(w.date))}${w.title ? ` — ${esc(w.title)}` : ''} will be removed from your sheet.</p>`, okText: 'Delete', danger: true }))) return;
        setState(await busy(() => window.api.deleteWorkout(id)));
        toast('Workout deleted');
      }
      return;
    }
    const card = e.target.closest('.w-card');
    if (card) {
      openWorkoutDetail(card.dataset.id);
    }
  });
}

// ================================================================ WORKOUT DETAIL VIEW
let currentDetailWorkoutId = null;

function openWorkoutDetail(workoutId) {
  currentDetailWorkoutId = workoutId;
  renderWorkoutDetail(workoutId);
  showView('workout-detail');
}

function renderWorkoutDetail(workoutId) {
  const w = S.workouts.find((x) => x.id === workoutId);
  if (!w) return;

  const sets = idx.byW.get(w.id) || [];
  const groups = groupByExercise(sets);

  $('#workout-detail-heading').textContent = `${fmtDate(w.date)}${w.title ? ' — ' + w.title : ''}`;
  $('#workout-detail-sub').textContent = w.notes ? w.notes : `${groups.size} exercises · ${sets.length} sets logged`;

  // Total volume, sets, reps for this session
  let totalVolume = 0;
  let totalWorkingSets = 0;
  let totalReps = 0;
  for (const s of sets) {
    const r = s.reps ?? 0;
    const wt = s.weight ?? 0;
    if (r > 0) {
      totalWorkingSets++;
      totalReps += r;
      totalVolume += wt * r;
    }
  }

  // Previous overall workout to compare session total volume
  const sortedWorkouts = idx.workouts;
  const currentIdx = sortedWorkouts.findIndex((x) => x.id === w.id);
  const prevWorkout = currentIdx >= 0 && currentIdx < sortedWorkouts.length - 1 ? sortedWorkouts[currentIdx + 1] : null;
  let prevTotalVol = 0;
  if (prevWorkout) {
    const prevSets = idx.byW.get(prevWorkout.id) || [];
    for (const s of prevSets) {
      const r = s.reps ?? 0;
      const wt = s.weight ?? 0;
      if (r > 0) prevTotalVol += wt * r;
    }
  }

  // Count PRs in this session
  let prCount = 0;
  const prExercises = new Set();
  for (const [name] of groups) {
    const allSessions = sessionsFor(name);
    const thisSess = allSessions.find((s) => s.workoutId === w.id);
    if (thisSess && thisSess.e1rm !== null) {
      const priorSessions = allSessions.filter((s) => s.t < thisSess.t);
      const priorMaxE1rm = priorSessions.reduce((m, s) => (s.e1rm !== null && s.e1rm > m ? s.e1rm : m), 0);
      if (thisSess.e1rm > priorMaxE1rm) {
        prCount++;
        prExercises.add(name);
      }
    }
  }

  const stat = (lbl, val, sub = '') => `<div class="stat"><div class="lbl">${lbl}</div><div class="val">${val}</div><div class="sub">${sub}</div></div>`;
  $('#workout-detail-stats').innerHTML = [
    stat('Total Volume', `${fmt(round(totalVolume, 1))} kg`, prevWorkout ? deltaHtml(prevTotalVol, totalVolume, ' kg') : 'Overall session load'),
    stat('Working Sets', `${totalWorkingSets}`, `${totalReps} total reps`),
    stat('Exercises', `${groups.size}`, 'Tracked movements'),
    stat('PRs Achieved', prCount > 0 ? `<span class="delta up">🏆 ${prCount} PR${prCount > 1 ? 's' : ''}</span>` : '–', prCount > 0 ? [...prExercises].join(', ') : 'No all-time PRs'),
  ].join('');

  // Per-exercise cards
  const exHtml = [];
  for (const [name, exSets] of groups) {
    const info = exInfo(name);
    const bw = !!info.bodyweight;
    const allSessions = sessionsFor(name);
    const thisSessIdx = allSessions.findIndex((s) => s.workoutId === w.id);
    const thisSess = thisSessIdx >= 0 ? allSessions[thisSessIdx] : null;
    const prevSess = thisSessIdx > 0 ? allSessions[thisSessIdx - 1] : null;
    const isPr = prExercises.has(name);

    // Current metrics
    const currVol = thisSess ? thisSess.volume : null;
    const currTop = thisSess ? thisSess.top : null;
    const currE1rm = thisSess ? thisSess.e1rm : null;
    const currReps = thisSess ? thisSess.totalReps : null;

    // Previous metrics
    const prevVol = prevSess ? prevSess.volume : null;
    const prevTop = prevSess ? prevSess.top : null;
    const prevE1rm = prevSess ? prevSess.e1rm : null;
    const prevReps = prevSess ? prevSess.totalReps : null;

    const u = bw ? ' reps' : ' kg';

    let compareHtml = '';
    if (prevSess) {
      compareHtml = `
        <div class="detail-compare-panel">
          <div class="detail-compare-title">
            <span>Performance vs Previous Session (${shortDate(prevSess.date)})</span>
            ${isPr ? '<span class="pr-badge">🏆 All-Time PR</span>' : ''}
          </div>
          <div class="detail-compare-grid">
            <div class="detail-compare-metric">
              <span class="lbl">Volume</span>
              <span class="val">${fmt(currVol)}${u}</span>
              ${deltaHtml(prevVol, currVol, u)}
            </div>
            <div class="detail-compare-metric">
              <span class="lbl">Top Weight</span>
              <span class="val">${currTop !== null ? fmt(currTop) + ' kg' : '–'}</span>
              ${deltaHtml(prevTop, currTop, ' kg')}
            </div>
            <div class="detail-compare-metric">
              <span class="lbl">Est. 1RM</span>
              <span class="val">${currE1rm !== null ? fmt(currE1rm) + ' kg' : '–'}</span>
              ${bw ? '<span class="delta flat">Bodyweight</span>' : deltaHtml(prevE1rm, currE1rm, ' kg')}
            </div>
            <div class="detail-compare-metric">
              <span class="lbl">Total Reps</span>
              <span class="val">${currReps} reps</span>
              ${deltaHtml(prevReps, currReps, ' reps')}
            </div>
          </div>
          <div class="detail-compare-prev">
            Prior session: <b>${esc(summarize(name, prevSess.sets))}</b>
          </div>
        </div>`;
    } else {
      compareHtml = `
        <div class="detail-compare-panel">
          <div class="detail-compare-title">
            <span>First Logged Session</span>
            <span class="pr-badge">🌟 Baseline Established</span>
          </div>
          <p class="muted small" style="margin: 4px 0 0;">This was your first recorded session for ${esc(name)}. Subsequent workouts will display progress and deltas here.</p>
        </div>`;
    }

    const setRows = exSets.map((s, si) => {
      const wt = s.weight;
      const r = s.reps;
      const e = (!bw && wt > 0 && r > 0) ? round(epley(wt, r)) : null;
      const setVol = (!bw && wt > 0 && r > 0) ? round(wt * r, 1) : null;
      return `
        <tr>
          <td>${si + 1}</td>
          <td><b>${fmtWeight(name, wt)}${bw && wt === 0 ? '' : ' kg'}</b></td>
          <td><b>${r !== null ? (r === 0 ? '0 (failed)' : r) : '–'}</b></td>
          <td>${e !== null ? fmt(e) + ' kg' : '–'}</td>
          <td>${setVol !== null ? fmt(setVol) + ' kg' : (r ? r + ' reps' : '–')}</td>
          <td class="muted">${esc(s.notes || '')}</td>
        </tr>`;
    }).join('');

    exHtml.push(`
      <div class="detail-ex-card">
        <div class="detail-ex-head">
          <div class="detail-ex-title">
            <span class="nm" data-ex="${esc(name)}">${esc(name)}</span>
            <span class="tag">${esc(info.category || 'Other')}${bw ? ' · BW' : ''}</span>
          </div>
          <div class="detail-summary-badge">${esc(summarize(name, exSets))}</div>
        </div>
        ${compareHtml}
        <table class="detail-sets-table">
          <thead>
            <tr><th>Set</th><th>Weight</th><th>Reps</th><th>Est. 1RM</th><th>Volume</th><th>Notes</th></tr>
          </thead>
          <tbody>${setRows}</tbody>
        </table>
        <div class="detail-ex-foot">
          <button class="ghost" style="padding: 4px 10px; font-size: 12px;" data-ex="${esc(name)}">View Progress Chart →</button>
        </div>
      </div>`);
  }

  $('#workout-detail-exercises').innerHTML = exHtml.join('');
}

function bindWorkoutDetail() {
  $('#workout-detail-back').addEventListener('click', () => showView('history'));
  $('#workout-detail-repeat').addEventListener('click', async () => {
    if (!currentDetailWorkoutId) return;
    if (draft.exercises.length && !(await modal({ title: 'Replace current draft?', html: '<p class="muted">You have an unsaved workout in progress.</p>', okText: 'Replace', danger: true }))) return;
    loadIntoDraft(currentDetailWorkoutId, { asEdit: false });
  });
  $('#workout-detail-edit').addEventListener('click', async () => {
    if (!currentDetailWorkoutId) return;
    if (draft.exercises.length && !(await modal({ title: 'Replace current draft?', html: '<p class="muted">You have an unsaved workout in progress.</p>', okText: 'Replace', danger: true }))) return;
    loadIntoDraft(currentDetailWorkoutId, { asEdit: true });
  });
  $('#workout-detail-delete').addEventListener('click', async () => {
    if (!currentDetailWorkoutId) return;
    const w = S.workouts.find((x) => x.id === currentDetailWorkoutId);
    if (!w) return;
    if (!(await modal({ title: 'Delete workout?', html: `<p class="muted">${esc(fmtDate(w.date))}${w.title ? ' — ' + esc(w.title) : ''} will be removed from your sheet.</p>`, okText: 'Delete', danger: true }))) return;
    setState(await busy(() => window.api.deleteWorkout(currentDetailWorkoutId)));
    toast('Workout deleted');
    showView('history');
  });
  $('#workout-detail-exercises').addEventListener('click', (e) => {
    const el = e.target.closest('[data-ex]');
    if (el) openProgress(el.dataset.ex);
  });
}

// ================================================================ PROGRESS VIEW
const prog = { exercise: null, metric: null, range: 0, chart: null, sparks: [] };

function openProgress(name) {
  const info = idx.exMap.get((name || '').toLowerCase());
  prog.exercise = info ? info.name : name;
  prog.metric = null;
  $('#progress-input').value = '';
  showView('progress');
}

function renderProgress() {
  prog.sparks.forEach((c) => c.destroy());
  prog.sparks = [];
  const detail = !!prog.exercise;
  $('#progress-overview').classList.toggle('hidden', detail);
  $('#progress-detail').classList.toggle('hidden', !detail);
  $('#progress-back').classList.toggle('hidden', !detail);
  if (detail) renderProgressDetail();
  else renderProgressOverview();
}

function deltaHtml(a, b, unit = '') {
  if (a === null || b === null || a === undefined || b === undefined || a === 0) return '<span class="delta flat">–</span>';
  const d = b - a;
  const pct = (d / a) * 100;
  const cls = Math.abs(pct) < 0.5 ? 'flat' : d > 0 ? 'up' : 'down';
  const arrow = cls === 'up' ? '▲' : cls === 'down' ? '▼' : '■';
  return `<span class="delta ${cls}">${arrow} ${d > 0 ? '+' : ''}${fmt(round(d, 0.1))}${unit} (${pct > 0 ? '+' : ''}${pct.toFixed(1)}%)</span>`;
}

function renderProgressOverview() {
  $('#progress-heading').textContent = 'Progress';
  $('#progress-sub').textContent = 'Your main lifts at a glance. Click one for full history & charts.';
  const names = [...idx.byE.keys()].map((k) => exInfo(idx.byE.get(k)[0].exercise).name || idx.byE.get(k)[0].exercise);
  const cards = names.map((name) => {
    const sessions = sessionsFor(name);
    const metric = defaultMetric(name, sessions);
    const M = metricsFor(name)[metric];
    const pts = sessions.filter((s) => M.get(s) !== null);
    return { name, sessions, metric, M, pts };
  }).filter((c) => c.pts.length >= 3).sort((a, b) => b.pts.length - a.pts.length);

  const main = cards.slice(0, 16);
  const host = $('#progress-overview');
  if (!main.length) {
    host.innerHTML = '<div class="empty">Not enough data yet. Log a few sessions (or import your notes in Settings) and charts will appear here.</div>';
    return;
  }

  const byCat = {};
  for (const c of cards) {
    const cat = exInfo(c.name).category || 'Other';
    (byCat[cat] = byCat[cat] || []).push(c.name);
  }
  const rest = [...idx.byE.values()].map((sets) => sets[0].exercise).filter((n) => !cards.some((c) => c.name === n));

  host.innerHTML = `
    <div class="grid">${main.map((c, i) => {
      const first = c.pts[0], last = c.pts[c.pts.length - 1];
      const peak = c.pts.reduce((m, s) => (c.M.get(s) > c.M.get(m) ? s : m), first);
      return `<div class="card lift-card" data-ex="${esc(c.name)}">
        <div class="top"><span class="nm">${esc(c.name)}</span>${deltaHtml(c.M.get(first), c.M.get(last))}</div>
        <div class="big">${fmt(c.M.get(last))} <small>${esc(c.M.unit)} · ${esc(c.M.label.toLowerCase())}</small></div>
        <div class="muted small">Peak ${fmt(c.M.get(peak))} (${shortDate(peak.date)}) · ${c.pts.length} sessions</div>
        <canvas id="spark-${i}"></canvas>
      </div>`;
    }).join('')}</div>
    ${Object.keys(byCat).length ? `<div class="section-title">All tracked exercises</div>
    <div class="chips">${[...cards.map((c) => c.name), ...rest].sort().map((n) => `<button class="chip" data-ex="${esc(n)}">${esc(n)}</button>`).join('')}</div>` : ''}`;

  main.forEach((c, i) => {
    const ctx = $(`#spark-${i}`);
    prog.sparks.push(new Chart(ctx, {
      type: 'line',
      data: { datasets: [{ data: c.pts.map((s) => ({ x: s.t, y: c.M.get(s) })), borderWidth: 2, pointRadius: 0, tension: 0.3,
        segment: { borderColor: (g) => (g.p1.parsed.y >= g.p0.parsed.y ? COLORS.up : COLORS.down) } }] },
      options: { responsive: true, maintainAspectRatio: false, animation: false, events: [],
        plugins: { legend: { display: false }, tooltip: { enabled: false } },
        scales: { x: { type: 'linear', display: false }, y: { display: false } } },
    }));
  });
}

function renderProgressDetail() {
  const name = prog.exercise;
  const info = exInfo(name);
  const sessionsAll = sessionsFor(name);
  const metrics = metricsFor(name);
  if (!prog.metric || !metrics[prog.metric]) prog.metric = defaultMetric(name, sessionsAll);
  const M = metrics[prog.metric];

  $('#progress-heading').textContent = name;
  $('#progress-sub').innerHTML = `${esc(info.category || 'Other')}${info.bodyweight ? ' · bodyweight (weights are added kg)' : ''} · ${sessionsAll.length} sessions`;

  $('#metric-seg').innerHTML = Object.entries(metrics).map(([k, m]) => `<button data-metric="${k}" class="${k === prog.metric ? 'active' : ''}">${esc(m.label)}</button>`).join('');
  $$('#range-seg button').forEach((b) => b.classList.toggle('active', +b.dataset.range === prog.range));

  const cutoff = prog.range ? Date.now() - prog.range * dayMs : -Infinity;
  const sessions = sessionsAll.filter((s) => s.t >= cutoff);
  const pts = sessions.filter((s) => M.get(s) !== null);
  const allPts = sessionsAll.filter((s) => M.get(s) !== null);

  // running all-time PR flags (computed over full history so range filter doesn't fake PRs)
  const prSet = new Set();
  let run = -Infinity;
  for (const s of allPts) { const v = M.get(s); if (v > run) { run = v; prSet.add(s.workoutId); } }

  // ----- stats
  const peak = allPts.reduce((m, s) => (m === null || M.get(s) > M.get(m) ? s : m), null);
  const latest = allPts[allPts.length - 1] || null;
  const prev = allPts[allPts.length - 2] || null;
  const firstInRange = pts[0] || null;
  const heaviest = sessionsAll.flatMap((s) => s.sets).filter((s) => s.weight !== null && (s.reps === null || s.reps > 0))
    .reduce((m, s) => (m === null || s.weight > m.weight || (s.weight === m.weight && (s.reps || 0) > (m.reps || 0)) ? s : m), null);
  const u = M.unit === 'kg' ? ' kg' : ` ${M.unit}`;
  const stat = (lbl, val, sub = '') => `<div class="stat"><div class="lbl">${lbl}</div><div class="val">${val}</div><div class="sub">${sub}</div></div>`;
  $('#progress-stats').innerHTML = [
    stat(`Peak ${esc(M.label.toLowerCase())}`, peak ? `${fmt(M.get(peak))}${u}` : '–', peak ? shortDate(peak.date) : ''),
    stat('Latest', latest ? `${fmt(M.get(latest))}${u}` : '–', latest ? `${shortDate(latest.date)} · ${deltaHtml(M.get(prev || latest), M.get(latest))}` : ''),
    stat(prog.range ? 'Change in range' : 'Change since first', firstInRange && latest ? deltaHtml(M.get(firstInRange), M.get(latest), u) : '–',
      firstInRange ? `from ${fmt(M.get(firstInRange))}${u} on ${shortDate(firstInRange.date)}` : ''),
    stat('vs peak', peak && latest ? (latest === peak ? '<span class="delta up">At peak 🏆</span>' : deltaHtml(M.get(peak), M.get(latest), u)) : '–', 'latest compared to best'),
    stat('Heaviest set', heaviest ? `1 x ${heaviest.reps ?? '?'} @ ${fmtWeight(name, heaviest.weight)}${isBW(name) && heaviest.weight === 0 ? '' : 'kg'}` : '–', heaviest ? shortDate(heaviest.date) : ''),
  ].join('');

  // ----- chart
  if (prog.chart) prog.chart.destroy();
  const ctx = $('#progress-chart');
  const grad = ctx.getContext('2d').createLinearGradient(0, 0, 0, 340);
  grad.addColorStop(0, 'rgba(255,122,69,0.25)');
  grad.addColorStop(1, 'rgba(255,122,69,0)');
  prog.chart = new Chart(ctx, {
    type: 'line',
    data: {
      datasets: [{
        label: M.label,
        data: pts.map((s) => ({ x: s.t, y: M.get(s), s })),
        borderWidth: 2.5,
        tension: 0.25,
        fill: true,
        backgroundColor: grad,
        segment: { borderColor: (g) => (g.p1.parsed.y > g.p0.parsed.y ? COLORS.up : g.p1.parsed.y < g.p0.parsed.y ? COLORS.down : COLORS.muted) },
        pointBackgroundColor: pts.map((s) => (prSet.has(s.workoutId) ? COLORS.pr : '#e8eaf0')),
        pointBorderColor: pts.map((s) => (prSet.has(s.workoutId) ? COLORS.pr : '#e8eaf0')),
        pointRadius: pts.map((s) => (prSet.has(s.workoutId) ? 5 : 3)),
        pointHoverRadius: 7,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'nearest', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#1e222b', borderColor: COLORS.border, borderWidth: 1, padding: 10,
          callbacks: {
            title: (items) => fmtDate(items[0].raw.s.date) + (items[0].raw.s.title ? ` — ${items[0].raw.s.title}` : ''),
            label: (item) => `${M.label}: ${fmt(item.raw.y)}${u}${prSet.has(item.raw.s.workoutId) ? '  🏆 PR' : ''}`,
            afterLabel: (item) => summarize(name, item.raw.s.sets),
          },
        },
      },
      scales: {
        x: {
          type: 'linear',
          grid: { color: '#20242d' },
          ticks: { color: COLORS.muted, maxTicksLimit: 8, callback: (v) => new Date(v).toLocaleDateString('en-GB', { month: 'short', year: '2-digit' }) },
        },
        y: { grid: { color: '#20242d' }, ticks: { color: COLORS.muted, callback: (v) => `${fmt(v)}${M.unit === 'kg' ? 'kg' : ''}` } },
      },
    },
  });

  // ----- table (newest first)
  const rows = [...sessions].reverse().map((s, i, arr) => {
    const older = arr.slice(i + 1).find((x) => M.get(x) !== null);
    const v = M.get(s);
    const notes = [...new Set(s.sets.map((x) => x.notes).filter(Boolean))].join('; ');
    const bestSet = s.best ? `${fmtWeight(name, s.best.weight)}×${s.best.reps}` : '–';
    return `<tr>
      <td>${esc(shortDate(s.date))}${s.title ? `<div class="notes">${esc(s.title)}</div>` : ''}</td>
      <td>${esc(summarize(name, s.sets))}${notes ? `<div class="notes">${esc(notes)}</div>` : ''}</td>
      ${isBW(name) ? '' : `<td>${bestSet}</td>`}
      <td>${v === null ? '–' : fmt(v)}${prSet.has(s.workoutId) && v !== null ? '<span class="pr-badge">PR</span>' : ''}</td>
      <td>${v !== null && older ? deltaHtml(M.get(older), v) : ''}</td>
    </tr>`;
  }).join('');
  $('#progress-table').innerHTML = `<thead><tr><th>Date</th><th>Sets</th>${isBW(name) ? '' : '<th>Best set</th>'}<th>${esc(M.label)}</th><th>vs previous</th></tr></thead><tbody>${rows || '<tr><td colspan="5" class="muted">No sessions in this range.</td></tr>'}</tbody>`;
}

function bindProgress() {
  const pick = (v) => {
    const info = idx.exMap.get(v.trim().toLowerCase());
    if (info) openProgress(info.name);
  };
  $('#progress-input').addEventListener('change', (e) => pick(e.target.value));
  $('#progress-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') pick(e.target.value); });
  $('#progress-open-goals')?.addEventListener('click', () => showView('goals'));
  $('#progress-back').addEventListener('click', () => { prog.exercise = null; renderProgress(); });
  $('#progress-overview').addEventListener('click', (e) => { const c = e.target.closest('[data-ex]'); if (c) openProgress(c.dataset.ex); });
  $('#metric-seg').addEventListener('click', (e) => { const b = e.target.closest('[data-metric]'); if (b) { prog.metric = b.dataset.metric; renderProgressDetail(); } });
  $('#range-seg').addEventListener('click', (e) => { const b = e.target.closest('[data-range]'); if (b) { prog.range = +b.dataset.range; renderProgressDetail(); } });
}

// ================================================================ SETTINGS VIEW
function renderSettings() {
  const st = S.storage || {};
  const badge = $('#storage-badge');
  if (st.kind === 'sheets') {
    badge.className = 'storage-badge sheets';
    badge.textContent = `● Google Sheets${st.title ? ` — ${st.title}` : ''}`;
    $('#sheets-status').innerHTML = `<span class="ok">● Connected</span> to <b>${esc(st.title || st.spreadsheetId)}</b>. Every save goes straight to your sheet.`;
  } else {
    badge.className = 'storage-badge local';
    badge.textContent = '● Local storage (not synced)';
    $('#sheets-status').innerHTML = st.error
      ? `<span class="err">● ${esc(st.error)}</span>`
      : '<span class="warn">● Not connected.</span> Workouts are saved locally on this PC until you connect a Google Sheet.';
  }
  if (document.activeElement !== $('#cfg-sheet')) $('#cfg-sheet').value = st.spreadsheetId || $('#cfg-sheet').value;
  if (document.activeElement !== $('#cfg-key')) $('#cfg-key').value = st.keyFile || $('#cfg-key').value;
  updateEmailCallout(st.serviceEmail);
  $('#cfg-disconnect').disabled = st.kind !== 'sheets';
  $('#cfg-open').disabled = !st.spreadsheetId;
  $('#cfg-reload').disabled = st.kind !== 'sheets';
  $('#copy-local').disabled = st.kind !== 'sheets';
  $('#data-summary').textContent = `Currently stored: ${S.workouts.length} workouts · ${S.sets.length} sets · ${S.exercises.length} exercises in catalogue.`;
}

function updateEmailCallout(email) {
  const el = $('#cfg-email');
  if (!email) { el.classList.add('hidden'); return; }
  el.classList.remove('hidden');
  el.innerHTML = `Share your Google Sheet with <code>${esc(email)}</code> as <b>Editor</b>. <button class="ghost" id="copy-email" style="padding:2px 10px;margin-left:6px">Copy</button>`;
  $('#copy-email').onclick = () => { navigator.clipboard.writeText(email); toast('Email copied'); };
}

function bindSettings() {
  $('#cfg-browse').addEventListener('click', async () => {
    const p = await window.api.pickKeyFile();
    if (p) { $('#cfg-key').value = p; toast('Key selected — press Connect'); }
  });
  $('#cfg-connect').addEventListener('click', async () => {
    const st = await busy(() => window.api.connect({ spreadsheetId: $('#cfg-sheet').value, keyFile: $('#cfg-key').value.trim() }));
    setState(st);
    toast(`Connected to "${st.storage.title}" ✓`, 'success');
  });
  $('#cfg-disconnect').addEventListener('click', async () => { setState(await busy(() => window.api.disconnect())); toast('Switched to local storage'); });
  $('#cfg-reload').addEventListener('click', async () => { setState(await busy(() => window.api.reload())); toast('Reloaded from sheet'); });
  $('#cfg-open').addEventListener('click', () => window.api.openExternal(`https://docs.google.com/spreadsheets/d/${S.storage.spreadsheetId}/edit`));
  $('#import-history').addEventListener('click', async () => {
    const where = S.storage.kind === 'sheets' ? 'your Google Sheet' : 'local storage (connect Sheets first if you want them in the sheet)';
    if (!(await modal({ title: 'Import historical notes?', html: `<p class="muted">Adds the workouts from <code>data/history.txt</code> to ${where}. Already-imported workouts are skipped.</p>`, okText: 'Import' }))) return;
    const res = await busy(() => window.api.importHistory());
    setState(res.state);
    toast(`Imported ${res.added} workouts (${res.sets} sets)${res.skipped ? `, skipped ${res.skipped} already present` : ''}`, 'success');
  });
  $('#copy-local').addEventListener('click', async () => {
    let overwrite = false;
    if (S.workouts.length > 0) {
      const choice = await modal({
        title: 'Copy Local Data to Google Sheets',
        html: `<p class="muted">Your Google Sheet already has <b>${S.workouts.length}</b> workouts.</p>
               <p>Choose <b>Append Missing</b> to keep existing sheet workouts and only add what's missing, or <b>Replace All</b> to cleanly overwrite the sheet with your local data.</p>`,
        okText: 'Append Missing',
      });
      if (choice === null) return;
    }
    const res = await busy(() => window.api.copyLocalToSheets({ overwrite }));
    setState(res.state);
    toast(`Copied ${res.added} workouts (${res.sets} sets) to Sheets${res.skipped ? ` (${res.skipped} already in sheet)` : ''}`, 'success');
  });

  $('#clear-data').addEventListener('click', async () => {
    const target = S.storage.kind === 'sheets' ? 'Google Sheet' : 'local database';
    if (!(await modal({
      title: `Clear all workouts?`,
      html: `<p class="muted">This will remove all workouts and sets from your <b>${target}</b>.</p>
             <p class="muted">You can re-import your 120 historical workouts at any time using <b>Import historical notes</b>.</p>`,
      okText: 'Clear All Data',
      danger: true,
    }))) return;
    const st = await busy(() => window.api.clearAllData());
    setState(st);
    toast('All workouts cleared. You can now re-import or start fresh.', 'success');
  });
  $('#view-settings').addEventListener('click', (e) => {
    const a = e.target.closest('[data-ext]');
    if (a) { e.preventDefault(); window.api.openExternal(a.dataset.ext); }
  });
}

// ================================================================ SUGGEST VIEW
const suggestState = { split: 'Push', seed: 0, slotOffsets: {}, currentPlan: [] };

const EXERCISE_BENEFITS = {
  'Bulgarian Split Squat': 'Unilateral quad/glute strength, eliminates leg imbalances, and spares lower spine',
  'Leg Press': 'Maximum quad overload and knee flexion with zero spinal shear',
  'Hack Squat': 'Deep knee flexion and quad hypertrophy with locked-in back support',
  'Leg Extension': 'Direct isolated rectus femoris quad peak-contraction stimulus',
  'Good Morning': 'Posterior chain hip-hinge endurance, hamstring stretch, and bulletproof lumbar erectors',
  'Hyperextension': 'Decompresses lower back, strengthens spinal erectors, glutes, and hamstrings under full range',
  'Romanian Deadlift': 'Lengthened-position hamstring stretch and powerful glute hypertrophy',
  'Leg Curl': 'Direct hamstring knee flexion isolation to balance knee joint integrity',
  'Incline Chest Press (Machine)': 'Locked-in clavicular upper pec tension without shoulder stability fatigue',
  'Dips': 'High-threshold motor unit recruitment for lower pecs, anterior delts, and triceps',
  'Cable Rear Delt Fly': 'Continuous peak resistance through full horizontal shoulder abduction',
  'Pendlay Row': 'Dead-stop explosive concentric back power and strict thoracic strength',
  'Snatch Grip High Pull': 'Explosive upper back, trapezius, and posterior chain power transfer',
  'Band Face Pull': 'Rotator cuff longevity, external rotation, and scapular upward rotation',
  'Fat Gripz Hammer Curl': 'Radical forearm flexor recruitment, brachialis mass, and grip strength',
  'Reverse Grip Curl': 'Brachioradialis hypertrophy and forearm extensor tendon balance',
  'Dumbbell Pullover': 'Expanded ribcage lat stretch in the fully lengthened position',
  'Paused Close Grip Bench Press': 'Eliminates stretch reflex for brute triceps and lockout power',
};

const SUGGEST_SLOTS = {
  Push: [
    { slot: 'Primary Compound Press', cat: 'Chest', candidates: ['Bench Press', 'Incline Bench Press', 'Close Grip Bench Press', 'Paused Close Grip Bench Press'], sets: 3, reps: 5 },
    { slot: 'Push Leg: Quad Overload', cat: 'Legs', candidates: ['Leg Press', 'Bulgarian Split Squat', 'Hack Squat', 'Squat', 'Front Squat', 'Leg Extension'], sets: 3, reps: 8 },
    { slot: 'Vertical / Shoulder Press', cat: 'Shoulders', candidates: ['Overhead Press', 'Seated Dumbbell Press', 'Shoulder Press', 'Incline Dumbbell Press'], sets: 3, reps: 6 },
    { slot: 'Secondary Chest & Push Accessory', cat: 'Chest', candidates: ['Dips', 'Incline Chest Press (Machine)', 'Incline Dumbbell Press', 'Dumbbell Fly', 'Weighted Push Up'], sets: 3, reps: 8 },
    { slot: 'Delts & Triceps Finisher', cat: 'Shoulders', candidates: ['Lateral Raise', 'Overhead Tricep Extension', 'Cable Tricep Pushdown', 'Skull Crusher'], sets: 3, reps: 10 },
  ],
  Pull: [
    { slot: 'Primary Vertical Pull', cat: 'Back', candidates: ['Chin Ups', 'Pull Ups', 'Wide Grip Pull Ups', 'Lat Pulldown'], sets: 3, reps: 6 },
    { slot: 'Heavy Horizontal Row', cat: 'Back', candidates: ['Pendlay Row', 'Chest Supported Dumbbell Row', 'Barbell Row', 'Dumbbell Row', 'Cable Row'], sets: 3, reps: 6 },
    { slot: 'Pull Leg: Hip Hinge & Posterior Chain', cat: 'Legs', candidates: ['Good Morning', 'Hyperextension', 'Romanian Deadlift', 'Deadlift', 'Trap Bar Deadlift', 'Leg Curl'], sets: 3, reps: 8 },
    { slot: 'Rear Delts & Upper Back Stretch', cat: 'Back', candidates: ['Band Face Pull', 'Rear Delt Raise', 'Cable Rear Delt Fly', 'Dumbbell Pullover', 'Snatch Grip High Pull'], sets: 3, reps: 12 },
    { slot: 'Biceps & Grip Finisher', cat: 'Arms', candidates: ['Fat Gripz Hammer Curl', 'Barbell Curl', 'EZ Bar Preacher Curl', 'Reverse Grip Curl', 'Incline Fat Gripz Hammer Curl'], sets: 3, reps: 10 },
  ],
  'Full Body': [
    { slot: 'Primary Lower Compound (Push / Hinge)', cat: 'Legs', candidates: ['Squat', 'Front Squat', 'Leg Press', 'Good Morning', 'Deadlift', 'Trap Bar Deadlift'], sets: 3, reps: 6 },
    { slot: 'Primary Upper Push', cat: 'Chest', candidates: ['Bench Press', 'Incline Bench Press', 'Overhead Press', 'Dips', 'Close Grip Bench Press'], sets: 3, reps: 5 },
    { slot: 'Primary Upper Pull', cat: 'Back', candidates: ['Chin Ups', 'Pull Ups', 'Pendlay Row', 'Barbell Row', 'Dumbbell Row'], sets: 3, reps: 6 },
    { slot: 'Secondary Lower: Quad or Posterior Chain', cat: 'Legs', candidates: ['Bulgarian Split Squat', 'Leg Press', 'Hyperextension', 'Good Morning', 'Leg Extension', 'Leg Curl', 'Romanian Deadlift'], sets: 3, reps: 8 },
    { slot: 'Shoulders & Arms Overload', cat: 'Shoulders', candidates: ['Lateral Raise', 'Band Face Pull', 'Rear Delt Raise', 'Fat Gripz Hammer Curl', 'Overhead Tricep Extension', 'Cable Tricep Pushdown', 'Barbell Curl'], sets: 3, reps: 10 },
  ],
};

function computeWeeklyVolumeStats() {
  const targets = { Chest: 10, Back: 12, Shoulders: 9, Legs: 10, Arms: 9, Core: 4 };
  const validSets = S.sets.filter((s) => (s.reps ?? 0) > 0);
  const latestMs = validSets.reduce((m, s) => Math.max(m, toDate(s.date).getTime() || 0), Date.now());
  const counts7 = {}, counts14 = {};
  for (const s of validSets) {
    const cat = exInfo(s.exercise).category || 'Other';
    const diffDays = Math.max(0, (latestMs - (toDate(s.date).getTime() || 0)) / dayMs);
    if (diffDays <= 7) counts7[cat] = (counts7[cat] || 0) + 1;
    if (diffDays <= 14) counts14[cat] = (counts14[cat] || 0) + 1;
  }
  const out = {};
  for (const [cat, target] of Object.entries(targets)) {
    const c7 = counts7[cat] || 0;
    const c14 = counts14[cat] || 0;
    const ratio = Math.min(1, c7 / target);
    out[cat] = { cat, c7, c14, target, ratio, deficit: 1 - ratio };
  }
  return out;
}

function renderSuggest() {
  const vol = computeWeeklyVolumeStats();
  const pushDef = ((vol.Chest?.deficit || 0) + (vol.Shoulders?.deficit || 0)) / 2;
  const pullDef = vol.Back?.deficit || 0;
  const legsDef = vol.Legs?.deficit || 0;
  const recSplit = legsDef >= pushDef && legsDef >= pullDef ? 'Full Body' : pullDef >= pushDef ? 'Pull' : 'Push';

  const splits = [
    { key: 'Push', sub: 'Chest, Shoulders & Triceps' },
    { key: 'Pull', sub: 'Back, Rear Delts & Biceps' },
    { key: 'Full Body', sub: 'Legs, Push, Pull & Arms' },
  ];

  $('#suggest-controls').innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
      <strong>Select Workout Split</strong>
      <span class="tag" style="color:var(--up);border-color:var(--up)">Recommended Today: ${esc(recSplit)}</span>
    </div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px">
      ${splits.map((sp) => {
        const active = suggestState.split === sp.key;
        return `<button data-split="${esc(sp.key)}" class="${active ? 'primary' : 'ghost'}" style="padding:14px;text-align:left;border-radius:10px">
          <div style="font-size:16px;font-weight:700">${esc(sp.key === 'Full Body' ? 'Full Body (FB)' : sp.key)}</div>
          <div style="font-size:12px;opacity:0.8;margin-top:4px">${esc(sp.sub)}</div>
        </button>`;
      }).join('')}
    </div>`;

  $('#suggest-volume').innerHTML = `
    <h3 style="margin-bottom:10px">7-Day Muscle Group Volume &amp; Focus Areas</h3>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px">
      ${Object.values(vol).map((st) => {
        const pct = Math.round(st.ratio * 100);
        const col = st.ratio < 0.4 ? 'var(--down)' : st.ratio < 0.8 ? 'var(--accent)' : 'var(--up)';
        return `<div style="background:#0f1115;padding:10px 12px;border-radius:10px;border:1px solid var(--border)">
          <div style="display:flex;justify-content:space-between;font-size:13px;font-weight:600">
            <span>${esc(st.cat)}</span>
            <span style="color:${col}">${st.c7}/${st.target} sets (7d)</span>
          </div>
          <div style="height:6px;background:#232834;border-radius:3px;margin:6px 0;overflow:hidden">
            <div style="height:100%;width:${Math.max(6, pct)}%;background:${col}"></div>
          </div>
          <div class="muted small">14-day total: ${st.c14} sets</div>
        </div>`;
      }).join('')}
    </div>`;

  const slots = SUGGEST_SLOTS[suggestState.split] || SUGGEST_SLOTS.Push;
  const picked = new Set();
  const latestMs = S.sets.reduce((m, s) => Math.max(m, toDate(s.date).getTime() || 0), Date.now());

  const plan = slots.map((slotDef, idx) => {
    const avail = slotDef.candidates.filter((c) => !picked.has(c.toLowerCase()));
    const pool = avail.length ? avail : slotDef.candidates;
    const ranked = [...pool].sort((a, b) => scoreCandidate(b, slotDef, vol, latestMs) - scoreCandidate(a, slotDef, vol, latestMs));
    const offset = (suggestState.slotOffsets[idx] || 0) + suggestState.seed;
    const chosen = ranked[offset % ranked.length];
    picked.add(chosen.toLowerCase());
    return buildPrescription(chosen, slotDef, vol, idx);
  });

  suggestState.currentPlan = plan;

  $('#suggest-list').innerHTML = plan.map((item, idx) => `
    <div class="card" style="margin-bottom:12px">
      <div style="display:flex;justify-content:space-between;align-items:flex-start">
        <div>
          <div class="small" style="color:var(--accent);font-weight:700;text-transform:uppercase">${idx + 1}. ${esc(item.slot)}</div>
          <div style="font-size:18px;font-weight:700;margin-top:2px">${esc(item.name)} <span class="tag">${esc(item.category)}</span></div>
        </div>
        <button class="ghost" data-swap="${idx}" style="padding:5px 12px">⇄ Swap Exercise</button>
      </div>
      <div style="margin-top:10px;padding:10px 12px;background:#0f1115;border-radius:8px;border:1px solid var(--border);display:flex;justify-content:space-between;align-items:center">
        <div style="font-weight:700;color:var(--accent);font-size:14px">TARGET: ${esc(item.targetSummary)}</div>
        ${item.peakE1rm ? `<div style="color:var(--pr);font-weight:700;font-size:12px">🏆 PR e1RM: ${fmt(item.peakE1rm)} kg</div>` : ''}
      </div>
      <div style="margin-top:8px;font-size:13px">${esc(item.reason)}</div>
      <div class="muted small" style="margin-top:4px">Last (${esc(item.lastDate)}): ${esc(item.lastSummary)}</div>
    </div>`).join('');
}

function scoreCandidate(name, slotDef, vol, latestMs) {
  const sessions = sessionsFor(name);
  const cat = exInfo(name).category || slotDef.cat;
  const volBonus = (vol[cat]?.deficit || 0.5) * 10;
  const benefitBonus = EXERCISE_BENEFITS[name] ? 4 : 0;

  if (!sessions.length) {
    // Unlogged / new exercise: grant appealing discovery score so it surfaces regularly on swap/shuffle
    return 8 + volBonus + benefitBonus;
  }

  const last = sessions[sessions.length - 1];
  const peakE = sessions.reduce((m, s) => Math.max(m, s.e1rm || 0), 0);
  const currE = last.e1rm || 0;
  const daysSince = Math.max(0, (latestMs - last.t) / dayMs);
  const plateauBonus = peakE > 0 && currE < peakE ? Math.min(15, ((peakE - currE) / peakE) * 25) : 5;
  const recencyBonus = daysSince < 2 ? -20 : daysSince <= 18 ? 12 : 6;
  return Math.min(25, sessions.length) * 0.8 + plateauBonus + recencyBonus + volBonus + benefitBonus;
}

function buildPrescription(name, slotDef, vol, idx) {
  const info = exInfo(name);
  const cat = info.category || slotDef.cat;
  const sessions = sessionsFor(name);
  const last = sessions[sessions.length - 1];
  const prev = sessions[sessions.length - 2];
  const peak = sessions.reduce((m, s) => (!m || (s.e1rm || 0) > (m.e1rm || 0) ? s : m), null);
  const peakE1rm = peak?.e1rm || 0;
  const currE1rm = last?.e1rm || 0;
  const benefitText = EXERCISE_BENEFITS[name];

  if (!last) {
    const sets = Array.from({ length: slotDef.sets }, () => ({ weight: '', reps: String(slotDef.reps), notes: '' }));
    const newReason = benefitText
      ? `✨ New Exercise Suggestion: ${benefitText}`
      : `🎯 New stimulus for ${cat} (${slotDef.sets} × ${slotDef.reps})`;
    return {
      idx, slot: slotDef.slot, name, category: cat,
      lastDate: 'First time', lastSummary: 'No prior sessions logged',
      peakE1rm: 0, targetSummary: `${slotDef.sets} sets × ${slotDef.reps} reps`,
      reason: newReason, sets,
    };
  }

  const completedSets = last.sets.filter((s) => (s.reps ?? 0) > 0);
  const bestWSet = completedSets.reduce((m, s) => (!m || (s.weight || 0) > (m.weight || 0) || ((s.weight || 0) === (m.weight || 0) && (s.reps || 0) > (m.reps || 0)) ? s : m), null);
  const baseW = bestWSet?.weight || 0;
  const baseR = bestWSet?.reps || slotDef.reps;
  const numSets = Math.min(4, Math.max(3, completedSets.length || 3));

  const readyJump = baseW > 0 && ((baseR >= 8 && slotDef.reps <= 6) || baseR >= 12 || (prev && (last.e1rm || 0) > (prev.e1rm || 0) && baseR >= slotDef.reps + 1));
  let targetW = baseW, targetR = baseR, note = '';
  if (baseW <= 0) {
    targetW = 0;
    targetR = Math.max(slotDef.reps, baseR + 1);
    note = `+1 rep progression (${baseR} → ${targetR} reps)`;
  } else if (readyJump) {
    const inc = cat === 'Arms' || name.toLowerCase().includes('raise') ? 1.25 : 2.5;
    targetW = round(baseW + inc, 0.5);
    targetR = Math.max(slotDef.reps, baseR - 2);
    note = `+${fmt(targetW - baseW)}kg weight jump`;
  } else {
    targetW = baseW;
    targetR = Math.min(15, baseR + 1);
    note = `+1 rep at ${fmt(targetW)}kg`;
  }

  const targetE = targetW > 0 ? round(epley(targetW, targetR)) : 0;
  const volStat = vol[cat];
  const benefitSuffix = benefitText ? ` • Benefit: ${benefitText}` : '';
  let reason = `📈 Progressive Overload: Build on ${shortDate(last.date)} (${note})${benefitSuffix}`;
  if (peakE1rm > 0 && currE1rm < peakE1rm * 0.94) {
    reason = `🔧 Needs Improvement: Current e1RM (${fmt(currE1rm)}kg) is below your ${fmt(peakE1rm)}kg PR (${shortDate(peak.date)})${benefitSuffix}`;
  } else if (targetE > peakE1rm && peakE1rm > 0) {
    reason = `🏆 PR Opportunity: Hitting ${fmt(targetW)}kg × ${targetR} sets a new e1RM PR (${fmt(targetE)}kg)!${benefitSuffix}`;
  } else if (volStat && volStat.c7 < volStat.target / 2) {
    reason = `📊 Weekly Volume Gap: ${cat} has only ${volStat.c7}/${volStat.target} sets in the last 7 days${benefitSuffix}`;
  }

  const targetSummary = targetW > 0 ? `${numSets} x ${targetR} @ ${fmt(targetW)}kg` : `${numSets} x ${targetR} @ BW`;
  const sets = Array.from({ length: numSets }, () => ({
    weight: targetW > 0 ? fmt(targetW) : (isBW(name) ? 'BW' : ''),
    reps: String(targetR),
    notes: '',
  }));

  return {
    idx, slot: slotDef.slot, name, category: cat,
    lastDate: shortDate(last.date), lastSummary: summarize(name, completedSets),
    peakE1rm, targetSummary, reason, sets,
  };
}

function bindSuggest() {
  $('#suggest-controls').addEventListener('click', (e) => {
    const b = e.target.closest('[data-split]');
    if (b) {
      suggestState.split = b.dataset.split;
      suggestState.slotOffsets = {};
      renderSuggest();
    }
  });
  $('#suggest-list').addEventListener('click', (e) => {
    const b = e.target.closest('[data-swap]');
    if (b) {
      const idx = +b.dataset.swap;
      suggestState.slotOffsets[idx] = (suggestState.slotOffsets[idx] || 0) + 1;
      renderSuggest();
    }
  });
  $('#suggest-shuffle').addEventListener('click', () => {
    suggestState.seed++;
    renderSuggest();
  });
  $('#suggest-load').addEventListener('click', () => {
    draft = {
      date: today(),
      title: suggestState.split,
      notes: `Auto-designed ${suggestState.split} session`,
      exercises: suggestState.currentPlan.map((it) => ({ name: it.name, sets: it.sets.map((s) => ({ ...s })) })),
      replaceId: null,
    };
    saveDraft();
    renderLog();
    showView('log');
    toast(`Loaded ${suggestState.split} workout into logger`, 'success');
  });
}

// ================================================================ GOALS VIEW & GOAL ENGINE
const GOALS_KEY = 'strengthlogz.goals';
let goalsList = (() => {
  try { return JSON.parse(localStorage.getItem(GOALS_KEY)) || []; } catch { return []; }
})();
const saveGoalsStore = () => localStorage.setItem(GOALS_KEY, JSON.stringify(goalsList));

function getActiveGoalForExercise(name) {
  const k = String(name || '').trim().toLowerCase();
  return goalsList.find((g) => g.enabled !== false && String(g.exercise || '').toLowerCase() === k) || null;
}

function evaluateGoalDesktop(goal) {
  const name = goal.exercise;
  const bw = isBW(name);
  const sessions = sessionsFor(name);
  const bestSess = sessions.reduce((m, s) => (s.e1rm !== null && (!m || s.e1rm > m.e1rm) ? s : m), null);
  const latestSess = sessions[sessions.length - 1] || null;
  const bestE1rm = bestSess?.e1rm || 0;
  const recentE1rm = latestSess?.e1rm || bestE1rm;
  const currentWorkingE1rm = recentE1rm > 0 ? Math.max(recentE1rm, bestE1rm * 0.92) : 40;

  const targetE1rm = goal.goalType === 'SETS_REPS'
    ? round(epley(goal.targetWeight || 0, Math.max(1, goal.targetReps || 5)))
    : round(goal.targetE1rm || 0);

  const startBase = goal.startingE1rm > 0 ? goal.startingE1rm : currentWorkingE1rm * 0.9;
  const rawPct = targetE1rm > startBase
    ? Math.round(((Math.max(bestE1rm, recentE1rm) - startBase) / (targetE1rm - startBase)) * 100)
    : (Math.max(bestE1rm, recentE1rm) >= targetE1rm ? 100 : 0);
  const progressPct = Math.max(0, Math.min(100, rawPct));
  const e1rmGap = Math.max(0, round(targetE1rm - currentWorkingE1rm, 0.5));

  const targetSummary = goal.goalType === 'SETS_REPS'
    ? `${goal.targetSets || 3} × ${goal.targetReps || 5} @ ${fmt(goal.targetWeight || 0)}kg (≈ ${fmt(targetE1rm)}kg 1RM)`
    : `${fmt(targetE1rm)}kg Est. 1RM`;

  const step = (exInfo(name).category === 'Arms' || name.toLowerCase().includes('raise')) ? 1.25 : 2.5;
  let nextSets = 4, nextReps = 5, nextWeight = round((currentWorkingE1rm / (1 + 5 / 30)) * 0.96, step), blockTitle = 'Strength Intensification (4×5)';
  if (goal.goalType === 'SETS_REPS') {
    nextSets = goal.targetSets || 3;
    nextReps = goal.targetReps || 8;
    const baseW = round(currentWorkingE1rm / (1 + nextReps / 30), step);
    nextWeight = Math.min(goal.targetWeight || baseW, Math.max(step, baseW));
    blockTitle = `Specificity Rep-Builder (${nextSets}×${nextReps})`;
  } else if (e1rmGap <= 5) {
    nextSets = 4; nextReps = 3;
    nextWeight = round((currentWorkingE1rm / (1 + 3 / 30)) * 0.97, step);
    blockTitle = 'Neural Realization & Peaking (4×3)';
  } else if (e1rmGap > 15) {
    nextSets = 4; nextReps = 8;
    nextWeight = round((currentWorkingE1rm / (1 + 8 / 30)) * 0.95, step);
    blockTitle = 'Hypertrophy & Work Capacity Base (4×8)';
  }
  const nextSessionE1rm = round(epley(nextWeight, nextReps));
  const nextSessionSummary = `${nextSets} sets × ${nextReps} reps @ ${bw && nextWeight === 0 ? 'BW' : fmt(nextWeight) + 'kg'}`;
  const estSessionsRemaining = e1rmGap <= 0 ? 0 : Math.max(1, Math.min(24, Math.ceil(e1rmGap / 1.5)));

  return {
    goal, name, bw, sessionsCount: sessions.length,
    bestE1rm: round(bestE1rm), recentE1rm: round(recentE1rm),
    targetE1rm, targetSummary, progressPct, e1rmGap,
    nextSets, nextReps, nextWeight, nextSessionE1rm, nextSessionSummary,
    blockTitle, estSessionsRemaining,
    latestSummary: latestSess ? `${shortDate(latestSess.date)}: ${summarize(name, latestSess.sets)}` : 'No sessions logged yet',
  };
}

function updateGoalsFromLoggedWorkout(dateStr, loggedSets) {
  const byEx = new Map();
  for (const s of loggedSets) {
    if ((s.reps ?? 0) <= 0) continue;
    const k = s.exercise.toLowerCase();
    if (!byEx.has(k)) byEx.set(k, []);
    byEx.get(k).push(s);
  }
  const updated = [];
  for (const g of goalsList) {
    if (g.enabled === false) continue;
    const sets = byEx.get(String(g.exercise || '').toLowerCase());
    if (!sets || !sets.length) continue;
    let bestE = 0, bestW = 0, bestR = 0, vol = 0;
    for (const s of sets) {
      const w = s.weight || 0, r = s.reps || 0;
      if (r > 0) {
        vol += w * r;
        const e = epley(w, r);
        if (e > bestE) { bestE = e; bestW = w; bestR = r; }
      }
    }
    const ev = evaluateGoalDesktop(g);
    const achieved = g.goalType === 'SETS_REPS'
      ? sets.filter((s) => (s.weight || 0) >= (g.targetWeight || 0) && (s.reps || 0) >= (g.targetReps || 1)).length >= (g.targetSets || 1)
      : bestE >= (g.targetE1rm || 0);
    g.achieved = g.achieved || achieved;
    g.sessionHistory = g.sessionHistory || [];
    g.sessionHistory.push({
      date: fmtDdMonYyyy(dateStr),
      bestWeight: bestW,
      bestReps: bestR,
      achievedE1rm: round(bestE),
      workingSets: sets.length,
      totalVolume: round(vol),
      progressPct: achieved ? 100 : ev.progressPct,
      hitSessionTarget: bestE >= ev.nextSessionE1rm * 0.98,
      summary: `${sets.length} working sets · Best ${fmt(bestW)}kg × ${bestR} (≈ ${fmt(round(bestE))}kg e1RM)`,
    });
    updated.push(g);
  }
  if (updated.length) saveGoalsStore();
  return updated;
}

function renderGoals() {
  const evals = goalsList.map((g) => evaluateGoalDesktop(g));
  const activeCount = evals.filter((e) => e.goal.enabled !== false).length;
  const achievedCount = evals.filter((e) => e.goal.achieved || e.progressPct >= 100).length;
  const avgPct = evals.length ? Math.round(evals.reduce((s, e) => s + e.progressPct, 0) / evals.length) : 0;

  $('#goals-summary-stats').innerHTML = [
    `<div class="stat"><div class="lbl">Configured Goals</div><div class="val">${evals.length}</div><div class="sub">${activeCount} active in logger</div></div>`,
    `<div class="stat"><div class="lbl">Goals Achieved</div><div class="val" style="color:var(--up)">${achievedCount}</div><div class="sub">Completed targets</div></div>`,
    `<div class="stat"><div class="lbl">Average Progress</div><div class="val" style="color:var(--pr)">${avgPct}%</div><div class="sub">Across all goals</div></div>`,
    `<div class="stat"><div class="lbl">Logged Goal Sessions</div><div class="val">${goalsList.reduce((acc, g) => acc + (g.sessionHistory?.length || 0), 0)}</div><div class="sub">Tracked reports</div></div>`,
  ].join('');

  const host = $('#goals-list');
  if (!evals.length) {
    host.innerHTML = `<div class="empty">No Exercise Goals set yet. Click <b>+ Set New Goal</b> above to configure a 1RM or Sets × Reps target and unlock custom programming &amp; live workout coaching.</div>`;
    return;
  }

  host.innerHTML = evals.map((ev) => {
    const g = ev.goal;
    const reports = g.sessionHistory || [];
    const lastRep = reports[reports.length - 1];
    return `
      <div class="card" style="margin-bottom:12px" data-goal-id="${esc(g.id)}">
        <div style="display:flex;justify-content:space-between;align-items:flex-start">
          <div>
            <span class="tag" style="color:var(--pr);border-color:var(--pr)">${g.goalType === 'SETS_REPS' ? 'SETS × REPS GOAL' : '1RM GOAL'}</span>
            <span style="font-size:19px;font-weight:700;margin-left:8px">${esc(ev.name)}</span>
            <div class="muted small" style="margin-top:4px">${esc(ev.latestSummary)}</div>
          </div>
          <div style="display:flex;gap:6px">
            <button class="ghost" data-gact="report">📊 Report</button>
            <button class="ghost" data-gact="load">⚡ Load in Logger</button>
            <button class="ghost" data-gact="edit">Edit</button>
            <button class="ghost danger" data-gact="del">✕</button>
          </div>
        </div>
        <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin-top:12px">
          <div style="background:#0f1115;padding:10px;border-radius:8px;border:1px solid var(--border)">
            <div class="muted small">CURRENT / PR e1RM</div>
            <div style="font-size:16px;font-weight:700;margin-top:2px">${fmt(ev.recentE1rm)}kg <span class="muted small">(PR ${fmt(ev.bestE1rm)}kg)</span></div>
          </div>
          <div style="background:#0f1115;padding:10px;border-radius:8px;border:1px solid rgba(255,209,102,0.35)">
            <div class="small" style="color:var(--pr);font-weight:700">TARGET GOAL</div>
            <div style="font-size:15px;font-weight:700;color:var(--pr);margin-top:2px">${esc(ev.targetSummary)}</div>
          </div>
          <div style="background:#0f1115;padding:10px;border-radius:8px;border:1px solid rgba(255,122,69,0.35)">
            <div class="small" style="color:var(--accent);font-weight:700">NEXT SESSION PRESCRIPTION</div>
            <div style="font-size:15px;font-weight:700;color:var(--accent);margin-top:2px">${esc(ev.nextSessionSummary)}</div>
          </div>
        </div>
        <div style="margin-top:10px">
          <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:4px">
            <span>Block: <b>${esc(ev.blockTitle)}</b></span>
            <span style="color:var(--pr);font-weight:700">${ev.progressPct}% · ${ev.e1rmGap <= 0 ? 'Goal Achieved! 🏆' : `${fmt(ev.e1rmGap)}kg E1RM gap (~${ev.estSessionsRemaining} sessions)`}</span>
          </div>
          <div style="height:7px;background:#232834;border-radius:4px;overflow:hidden">
            <div style="height:100%;width:${Math.max(4, ev.progressPct)}%;background:var(--pr)"></div>
          </div>
        </div>
        ${lastRep ? `<div class="muted small" style="margin-top:8px">Latest Logged Goal Session (${esc(lastRep.date)}): <b>${esc(lastRep.summary)}</b></div>` : ''}
      </div>`;
  }).join('');
}

async function openGoalModal(existingGoal = null) {
  const defaultEx = existingGoal?.exercise || S.exercises[0]?.name || 'Bench Press';
  const body = await modal({
    title: existingGoal ? `Edit Goal — ${existingGoal.exercise}` : 'Set Exercise Goal',
    okText: existingGoal ? 'Save Goal' : 'Create Goal',
    html: `
      <label>Exercise
        <input id="gm-ex" list="exercise-list" value="${esc(defaultEx)}">
      </label>
      <label>Goal Type
        <select id="gm-type">
          <option value="E1RM" ${existingGoal?.goalType !== 'SETS_REPS' ? 'selected' : ''}>1RM Goal (Estimated or True 1-Rep Max)</option>
          <option value="SETS_REPS" ${existingGoal?.goalType === 'SETS_REPS' ? 'selected' : ''}>Sets / Reps Goal (e.g. 3 sets × 8 reps @ 100kg)</option>
        </select>
      </label>
      <div id="gm-e1rm-wrap">
        <label>Target 1RM (kg)<input id="gm-e1rm" type="number" step="0.5" value="${esc(existingGoal?.targetE1rm || 100)}"></label>
      </div>
      <div id="gm-sr-wrap">
        <label>Target Weight (kg)<input id="gm-w" type="number" step="0.5" value="${esc(existingGoal?.targetWeight || 80)}"></label>
        <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px">
          <label>Target Sets<input id="gm-s" type="number" value="${esc(existingGoal?.targetSets || 3)}"></label>
          <label>Target Reps<input id="gm-r" type="number" value="${esc(existingGoal?.targetReps || 8)}"></label>
        </div>
      </div>
      <label>Notes (optional)<input id="gm-notes" value="${esc(existingGoal?.notes || '')}"></label>`,
  });
  if (!body) return;
  const exName = $('#gm-ex', body).value.trim();
  if (!exName) return;
  const goalType = $('#gm-type', body).value;
  const sessions = sessionsFor(exName);
  const startE1rm = sessions.reduce((m, s) => Math.max(m, s.e1rm || 0), 0);
  const goalObj = {
    id: existingGoal?.id || `goal-${Date.now()}`,
    exercise: exName,
    goalType,
    targetE1rm: Number($('#gm-e1rm', body).value) || 100,
    targetWeight: Number($('#gm-w', body).value) || 80,
    targetSets: Number($('#gm-s', body).value) || 3,
    targetReps: Number($('#gm-r', body).value) || 8,
    startingE1rm: existingGoal?.startingE1rm || startE1rm,
    createdDate: existingGoal?.createdDate || fmtDdMonYyyy(today()),
    enabled: true,
    achieved: false,
    notes: $('#gm-notes', body).value.trim(),
    sessionHistory: existingGoal?.sessionHistory || [],
  };
  const idxExisting = goalsList.findIndex((g) => g.id === goalObj.id || g.exercise.toLowerCase() === exName.toLowerCase());
  if (idxExisting >= 0) goalsList[idxExisting] = goalObj;
  else goalsList.push(goalObj);
  saveGoalsStore();
  renderGoals();
  renderLog();
  toast(`Saved goal for ${exName}`, 'success');
}

async function openGoalProgressModal(ei) {
  const ex = draft.exercises[ei];
  if (!ex) return;
  const g = getActiveGoalForExercise(ex.name);
  if (!g) return;
  const ev = evaluateGoalDesktop(g);
  const validSets = ex.sets.map((s) => ({ w: parseWeight(s.weight) || 0, r: parseReps(s.reps) || 0 })).filter((s) => s.r > 0);
  const bestTodayE1rm = validSets.reduce((m, s) => Math.max(m, round(epley(s.w, s.r))), 0);
  const res = await modal({
    title: `🎯 Goal Progress — ${ex.name}`,
    okText: '⚡ Auto-Fill Prescribed Sets',
    html: `
      <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid rgba(255,209,102,0.4);margin-bottom:10px">
        <div class="small" style="color:var(--pr);font-weight:700">ACTIVE GOAL (${ev.progressPct}% COMPLETE)</div>
        <div style="font-size:16px;font-weight:700;margin-top:2px">${esc(ev.targetSummary)}</div>
        <div class="muted small" style="margin-top:4px">Current Best e1RM: ${fmt(ev.bestE1rm)}kg · Gap: ${fmt(ev.e1rmGap)}kg</div>
      </div>
      <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid rgba(255,122,69,0.4);margin-bottom:10px">
        <div class="small" style="color:var(--accent);font-weight:700">WHAT TO AIM FOR TODAY (${esc(ev.blockTitle)})</div>
        <div style="font-size:17px;font-weight:700;color:var(--accent);margin-top:2px">${esc(ev.nextSessionSummary)}</div>
        <div class="muted small" style="margin-top:4px">Target Session E1RM: ≈ ${fmt(ev.nextSessionE1rm)}kg · Rest 2–3 min between working sets</div>
      </div>
      <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid var(--border)">
        <div class="small" style="font-weight:700">LIVE SESSION STATUS</div>
        <div style="margin-top:4px">${validSets.length
          ? `${validSets.length}/${ev.nextSets} working sets entered · Best today: <b>≈ ${fmt(bestTodayE1rm)}kg e1RM</b> ${bestTodayE1rm >= ev.nextSessionE1rm ? '✅ Target Hit!' : ''}`
          : 'No completed sets entered yet. Click Auto-Fill below to populate today\'s target sets.'}</div>
      </div>`,
  });
  if (res) {
    draft.exercises[ei].sets = Array.from({ length: ev.nextSets }, () => ({
      weight: wToInput(ex.name, ev.nextWeight),
      reps: String(ev.nextReps),
      notes: '',
    }));
    saveDraft();
    renderLog();
    toast(`Auto-filled ${ev.nextSessionSummary}`, 'success');
  }
}

async function openExerciseGoalReportModal(goalId) {
  const g = goalsList.find((x) => x.id === goalId);
  if (!g) return;
  const ev = evaluateGoalDesktop(g);
  const hist = [...(g.sessionHistory || [])].reverse();
  await modal({
    title: `📊 Exercise Goal Report — ${g.exercise}`,
    okText: 'Close',
    html: `
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:10px">
        <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid var(--border)">
          <div class="muted small">TARGET</div>
          <div style="font-weight:700;color:var(--pr)">${esc(ev.targetSummary)}</div>
        </div>
        <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid var(--border)">
          <div class="muted small">PROGRESS</div>
          <div style="font-weight:700;color:var(--up)">${ev.progressPct}% (${fmt(ev.bestE1rm)}kg / ${fmt(ev.targetE1rm)}kg)</div>
        </div>
      </div>
      <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid var(--border);margin-bottom:10px">
        <div class="small" style="color:var(--accent);font-weight:700">PROGRAMMING &amp; NEXT SESSION RECOMMENDATION</div>
        <div style="margin-top:4px"><b>${esc(ev.blockTitle)}:</b> Aim for <b>${esc(ev.nextSessionSummary)}</b> (≈ ${fmt(ev.nextSessionE1rm)}kg e1RM). Estimated ${ev.estSessionsRemaining} sessions to goal.</div>
      </div>
      <div class="small" style="font-weight:700;margin-bottom:6px">LOGGED GOAL SESSION HISTORY (${hist.length})</div>
      ${hist.length ? hist.map((h) => `
        <div style="padding:8px 10px;background:#0f1115;border-radius:6px;border:1px solid var(--border);margin-bottom:6px;font-size:12px">
          <div style="display:flex;justify-content:space-between"><b>${esc(h.date)}</b><span style="color:var(--pr)">${h.progressPct}% to goal</span></div>
          <div class="muted" style="margin-top:2px">${esc(h.summary)}</div>
        </div>`).join('') : '<div class="muted small">Log a workout containing this exercise to record session-by-session goal reports.</div>'}`,
  });
}

async function openAllGoalsReportModal() {
  const evals = goalsList.map((g) => evaluateGoalDesktop(g));
  await modal({
    title: '📊 All Exercise Goals — Master Report',
    okText: 'Close',
    html: evals.length ? evals.map((ev) => `
      <div style="padding:10px;background:#0f1115;border-radius:8px;border:1px solid var(--border);margin-bottom:8px">
        <div style="display:flex;justify-content:space-between">
          <b>${esc(ev.name)}</b>
          <span style="color:var(--pr);font-weight:700">${ev.progressPct}%</span>
        </div>
        <div class="small" style="margin-top:2px">Target: <b>${esc(ev.targetSummary)}</b> · Current Best: <b>${fmt(ev.bestE1rm)}kg</b></div>
        <div class="muted small" style="margin-top:2px">Next Session: ${esc(ev.nextSessionSummary)} (${esc(ev.blockTitle)})</div>
      </div>`).join('') : '<div class="muted">No goals configured yet.</div>',
  });
}

function bindGoals() {
  $('#goals-back-progress')?.addEventListener('click', () => showView('progress'));
  $('#goals-add-btn')?.addEventListener('click', () => openGoalModal(null));
  $('#goals-master-report-btn')?.addEventListener('click', () => openAllGoalsReportModal());
  $('#goals-list')?.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-gact]');
    if (!btn) return;
    const card = btn.closest('[data-goal-id]');
    const id = card?.dataset.goalId;
    const g = goalsList.find((x) => x.id === id);
    if (!g) return;
    const act = btn.dataset.gact;
    if (act === 'report') openExerciseGoalReportModal(id);
    if (act === 'edit') openGoalModal(g);
    if (act === 'del') {
      goalsList = goalsList.filter((x) => x.id !== id);
      saveGoalsStore();
      renderGoals();
      renderLog();
      toast('Goal removed');
    }
    if (act === 'load') {
      const ev = evaluateGoalDesktop(g);
      const sets = Array.from({ length: ev.nextSets }, () => ({
        weight: wToInput(g.exercise, ev.nextWeight),
        reps: String(ev.nextReps),
        notes: '',
      }));
      const existingIdx = draft.exercises.findIndex((x) => x.name.toLowerCase() === g.exercise.toLowerCase());
      if (existingIdx >= 0) draft.exercises[existingIdx].sets = sets;
      else draft.exercises.push({ name: g.exercise, sets });
      saveDraft();
      renderLog();
      showView('log');
      toast(`Loaded ${ev.nextSessionSummary} for ${g.exercise} into logger`, 'success');
    }
  });
}

// ================================================================ boot
function renderAll() {
  $('#exercise-list').innerHTML = S.exercises.map((e) => `<option value="${esc(e.name)}">${esc(e.category || '')}</option>`).join('');
  $('#title-list').innerHTML = [...new Set(S.workouts.map((w) => w.title).filter(Boolean))].map((t) => `<option value="${esc(t)}">`).join('');
  renderLog();
  renderHistory();
  renderSettings();
  if ($('#view-progress').classList.contains('active')) renderProgress();
  if ($('#view-goals')?.classList.contains('active')) renderGoals();
  if ($('#view-suggest').classList.contains('active')) renderSuggest();
  if ($('#view-workout-detail').classList.contains('active') && currentDetailWorkoutId) renderWorkoutDetail(currentDetailWorkoutId);
}

function bindGlobal() {
  $$('.nav-btn').forEach((b) => b.addEventListener('click', () => showView(b.dataset.view)));
  document.addEventListener('keydown', (e) => {
    if (!$('#modal').classList.contains('hidden')) return;
    if (e.ctrlKey && e.key.toLowerCase() === 's') { e.preventDefault(); if ($('#view-log').classList.contains('active')) saveWorkout(); }
    if (e.ctrlKey && /^[1-5]$/.test(e.key)) { e.preventDefault(); showView(['log', 'suggest', 'history', 'progress', 'settings'][+e.key - 1]); }
  });
}

(async function boot() {
  Chart.defaults.font.family = '"Segoe UI", system-ui, sans-serif';
  Chart.defaults.color = COLORS.muted;
  bindGlobal();
  bindLog();
  bindSuggest();
  bindHistory();
  bindWorkoutDetail();
  bindProgress();
  bindGoals();
  bindSettings();
  try {
    setState(await window.api.state());
    if (S.storage.error) toast(S.storage.error, 'error');
  } catch (err) {
    toast(err.message, 'error');
  }
})();
