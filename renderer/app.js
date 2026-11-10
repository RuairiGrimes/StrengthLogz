'use strict';
/* global Chart */

// ---------------------------------------------------------------- helpers & DOM utilities
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

const toSortableIso = (d) => {
  const dt = toDate(d);
  if (Number.isNaN(dt.getTime())) return '';
  return `${dt.getFullYear()}-${String(dt.getMonth() + 1).padStart(2, '0')}-${String(dt.getDate()).padStart(2, '0')}`;
};

const fmtDdMonYyyy = (d) => {
  const dt = toDate(d);
  if (Number.isNaN(dt.getTime())) return String(d || '');
  return `${String(dt.getDate()).padStart(2, '0')}-${MONTHS_SHORT[dt.getMonth()]}-${dt.getFullYear()}`;
};
const fmtDate = (d) => fmtDdMonYyyy(d);
const shortDate = (d) => fmtDdMonYyyy(d);

const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Epley formula: E1RM = weight * (1 + reps / 30). Invalidate failed / zero-rep sets. */
const epley = (w, r) => (!r || r <= 0 ? 0 : (r === 1 ? (w || 0) : (w || 0) * (1 + r / 30)));

const css = (v) => getComputedStyle(document.documentElement).getPropertyValue(v).trim();
const COLORS = {
  up: css('--up') || '#3ddc97',
  down: css('--down') || '#ff5d73',
  pr: css('--pr') || '#ffd166',
  accent: css('--accent') || '#ff7a45',
  muted: css('--muted') || '#8b93a7',
  border: css('--border') || '#2a2f3a',
};

// ---------------------------------------------------------------- state
let S = {
  workouts: [],
  sets: [],
  exercises: [],
  bodyWeights: [],
  goals: [],
  programs: [],
  activeProgramId: null,
  draft: null,
  storage: {},
};

const idx = {
  byW: new Map(),
  byE: new Map(),
  exMap: new Map(),
  workouts: [],
};

function setState(st) {
  S = {
    ...st,
    bodyWeights: st.bodyWeights || [],
    goals: st.goals || [],
    programs: st.programs || [],
    activeProgramId: st.activeProgramId || (st.programs && st.programs[0] ? st.programs[0].id : null),
  };
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

const exInfo = (name) => idx.exMap.get((name || '').toLowerCase()) || { name, category: 'Other', bodyweight: false, description: '' };
const isBW = (name) => !!exInfo(name).bodyweight;

// ---------------------------------------------------------------- Body Weight & Total Load Engine
const BodyWeightUtils = {
  weightOn(dateStr) {
    const list = S.bodyWeights || [];
    if (!list.length) return 75.0;
    const targetIso = toSortableIso(dateStr) || toSortableIso(today());
    const sorted = [...list].sort((a, b) => toSortableIso(a.date).localeCompare(toSortableIso(b.date)));
    let candidate = sorted[0].weightKg;
    for (const entry of sorted) {
      if (toSortableIso(entry.date) <= targetIso) {
        candidate = entry.weightKg;
      } else {
        break;
      }
    }
    return candidate;
  },

  latestWeight() {
    const list = S.bodyWeights || [];
    if (!list.length) return null;
    const sorted = [...list].sort((a, b) => toSortableIso(b.date).localeCompare(toSortableIso(a.date)));
    return sorted[0].weightKg;
  },

  totalLoad(exerciseName, dateStr, addedWeight) {
    const w = addedWeight !== null && addedWeight !== undefined ? Number(addedWeight) : 0;
    if (isBW(exerciseName)) {
      return this.weightOn(dateStr) + w;
    }
    return w;
  },

  calculateE1rm(exerciseName, dateStr, addedWeight, reps) {
    if (!reps || reps <= 0) return 0;
    const load = this.totalLoad(exerciseName, dateStr, addedWeight);
    return epley(load, reps);
  },
};

// ---------------------------------------------------------------- formatting sets
function fmtWeight(name, w) {
  if (w === null || w === undefined) return '?';
  if (isBW(name)) return w === 0 ? 'BW' : `BW+${fmt(w)}`;
  return fmt(w);
}

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
    const wStr = bw ? (w === 0 ? 'BW' : `BW+${fmt(w)}kg`) : `${fmt(w)}kg`;
    return `${n} x ${reps} @ ${wStr}`;
  }).join(', ');
}

function groupByExercise(sets) {
  const map = new Map();
  for (const s of sets) {
    if (!map.has(s.exercise)) map.set(s.exercise, []);
    map.get(s.exercise).push(s);
  }
  for (const arr of map.values()) arr.sort((a, b) => (a.setNo || 0) - (b.setNo || 0));
  return map;
}

// ---------------------------------------------------------------- sessions & metrics
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
        const load = BodyWeightUtils.totalLoad(name, sess.date, w);
        volume += w * r;
        const e = epley(load, r);
        if (e1rm === null || e > e1rm) { e1rm = e; best = s; }
      }
    }
    const w = idx.workouts.find((x) => x.id === sess.workoutId);
    return {
      ...sess,
      t: toDate(sess.date).getTime(),
      title: w ? w.title : '',
      e1rm: e1rm === null ? null : round(e1rm),
      best,
      top,
      volume: bw ? (totalReps || null) : (volume || null),
      maxReps,
      totalReps,
    };
  });
  return out.sort((a, b) => a.t - b.t);
}

function metricsFor(name) {
  const bw = isBW(name);
  return {
    e1rm: { label: bw ? 'Est. Total 1RM' : 'Est. 1RM', unit: 'kg', get: (s) => s.e1rm },
    top: { label: bw ? 'Top added weight' : 'Top weight', unit: 'kg', get: (s) => s.top },
    volume: { label: bw ? 'Total reps' : 'Volume', unit: bw ? 'reps' : 'kg', get: (s) => s.volume },
    reps: { label: 'Max reps', unit: 'reps', get: (s) => s.maxReps },
  };
}

function defaultMetric(name, sessions) {
  const m = metricsFor(name);
  const order = ['e1rm', 'top', 'reps', 'volume'];
  return order.find((k) => m[k] && sessions.some((s) => m[k].get(s) !== null)) || order[0];
}

// ---------------------------------------------------------------- UI utilities & Modal
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
  try {
    return await fn();
  } catch (err) {
    toast(err.message, 'error');
    throw err;
  } finally {
    $('#busy').classList.add('hidden');
  }
}

function modal({ title, html, okText = 'OK', cancelText = 'Cancel', danger = false, wide = false, large = false }) {
  return new Promise((resolve) => {
    const m = $('#modal');
    const box = $('.modal-box', m);
    $('#modal-title').textContent = title;
    $('#modal-body').innerHTML = html;

    box.classList.toggle('wide', !!wide);
    box.classList.toggle('large', !!large);

    const ok = $('#modal-ok');
    ok.textContent = okText;
    ok.style.background = danger ? COLORS.down : '';
    ok.style.borderColor = danger ? COLORS.down : '';
    ok.classList.toggle('hidden', okText === false);

    const cancel = $('#modal-cancel');
    cancel.textContent = cancelText;

    m.classList.remove('hidden');
    const first = $('#modal-body input:not([type="hidden"])');
    if (first) setTimeout(() => first.focus(), 50);

    const close = (val) => {
      m.classList.add('hidden');
      box.classList.remove('wide', 'large');
      ok.onclick = null;
      cancel.onclick = null;
      $('#modal-x').onclick = null;
      m.onkeydown = null;
      resolve(val);
    };

    ok.onclick = () => close($('#modal-body'));
    cancel.onclick = () => close(null);
    $('#modal-x').onclick = () => close(null);
    m.onkeydown = (e) => {
      if (e.key === 'Escape') close(null);
      if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA') {
        e.preventDefault();
        close($('#modal-body'));
      }
    };
  });
}

// ---------------------------------------------------------------- Navigation
function showView(name, arg = null) {
  $$('.nav-btn').forEach((b) => b.classList.toggle('active', b.dataset.view === name));
  $$('.view').forEach((v) => v.classList.toggle('active', v.id === `view-${name}`));

  if (name === 'log') {
    if (arg && arg.exercises) {
      loadPlanIntoLogger(arg);
    }
  } else if (name === 'program') {
    renderProgramView();
  } else if (name === 'suggest') {
    renderSuggest();
  } else if (name === 'history') {
    renderHistory();
  } else if (name === 'workout-detail') {
    renderWorkoutDetail(arg);
  } else if (name === 'progress') {
    if (arg) renderProgressDetail(arg);
    else renderProgressOverview();
  } else if (name === 'goals') {
    renderGoals();
  } else if (name === 'exercises') {
    renderExercisesCatalog();
  } else if (name === 'settings') {
    renderSettings();
  }
}

// ---------------------------------------------------------------- LOG WORKOUT VIEW
let logExercises = [];
let draftDebounceTimer = null;

function initLog() {
  $('#log-date').value = today();
  $('#log-save').addEventListener('click', saveWorkoutFromLog);
  $('#log-discard').addEventListener('click', () => {
    if (confirm('Discard current workout in progress?')) {
      logExercises = [];
      $('#log-title').value = '';
      $('#log-notes').value = '';
      $('#log-date').value = today();
      renderLogExercises();
      window.api.clearDraft().catch(() => {});
      toast('Workout discarded');
    }
  });

  $('#log-load-program-btn').addEventListener('click', openLoadProgramDayDialog);

  $('#add-ex-btn').addEventListener('click', () => {
    const val = $('#add-ex-input').value.trim();
    if (val) {
      addExerciseToLog(val);
      $('#add-ex-input').value = '';
    }
  });

  $('#add-ex-input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      $('#add-ex-btn').click();
    }
  });

  // Restore draft if any
  if (S.draft && (S.draft.exercises?.length || S.draft.title || S.draft.notes)) {
    $('#log-date').value = S.draft.date || today();
    $('#log-title').value = S.draft.title || '';
    $('#log-notes').value = S.draft.notes || '';
    logExercises = S.draft.exercises || [];
  }
}

function persistDraft() {
  clearTimeout(draftDebounceTimer);
  draftDebounceTimer = setTimeout(() => {
    const draft = {
      date: $('#log-date').value,
      title: $('#log-title').value,
      notes: $('#log-notes').value,
      exercises: logExercises,
    };
    window.api.saveDraft(draft).catch(() => {});
  }, 400);
}

function addExerciseToLog(exerciseName, prefilledSets = null) {
  const prevSessions = sessionsFor(exerciseName);
  const latestSess = prevSessions[prevSessions.length - 1];
  const lastSets = latestSess ? latestSess.sets : [];

  const sets = prefilledSets && prefilledSets.length
    ? prefilledSets.map((ps, i) => ({
        setNo: i + 1,
        weight: ps.weight !== '' && ps.weight !== null ? Number(ps.weight) : null,
        reps: ps.reps !== '' && ps.reps !== null ? Number(ps.reps) : null,
        notes: ps.notes || '',
      }))
    : (lastSets.length
      ? lastSets.map((ls, i) => ({ setNo: i + 1, weight: ls.weight, reps: ls.reps, notes: '' }))
      : [{ setNo: 1, weight: null, reps: null, notes: '' }]);

  logExercises.push({
    name: exerciseName,
    sets,
  });

  renderLogExercises();
  persistDraft();
}

function renderLogExercises() {
  const host = $('#log-exercises');
  if (!logExercises.length) {
    host.innerHTML = '<div class="empty">No exercises added yet. Pick one below to start logging!</div>';
    return;
  }

  const workoutDate = $('#log-date').value || today();
  host.innerHTML = logExercises.map((ex, exIdx) => {
    const info = exInfo(ex.name);
    const bw = isBW(ex.name);
    const activeGoal = getActiveGoalForExercise(ex.name);
    const prevSessions = sessionsFor(ex.name);
    const last = prevSessions[prevSessions.length - 1];
    const hint = last ? `Last (${shortDate(last.date)}): <b>${summarize(ex.name, last.sets)}</b>` : 'First time logging this lift';

    const setsRows = ex.sets.map((s, sIdx) => {
      const e1rm = (s.reps && s.reps > 0 && s.weight !== null)
        ? Math.round(BodyWeightUtils.calculateE1rm(ex.name, workoutDate, s.weight, s.reps) * 10) / 10
        : null;

      return `
        <tr data-ex="${exIdx}" data-set="${sIdx}">
          <td class="n">${sIdx + 1}</td>
          <td class="w"><input type="number" step="0.5" class="set-w" value="${s.weight ?? ''}" placeholder="${bw ? '+kg' : 'kg'}" /></td>
          <td class="r"><input type="number" step="1" class="set-r" value="${s.reps ?? ''}" placeholder="reps" /></td>
          <td class="e1rm">${e1rm ? `${e1rm}k` : '–'}</td>
          <td class="x"><button class="icon del-set" title="Delete set">✕</button></td>
        </tr>
      `;
    }).join('');

    return `
      <div class="card ex-card" data-ex="${exIdx}">
        <div class="ex-head">
          <span class="ex-name" data-jump-progress="${esc(ex.name)}">${esc(ex.name)}</span>
          <span class="tag">${esc(info.category || 'Other')}</span>
          ${bw ? '<span class="tag" style="color:var(--pr);border-color:rgba(255,209,102,0.4)">BW</span>' : ''}
          ${activeGoal ? `<button class="primary compact goal-progress-btn" data-goal-ex="${esc(ex.name)}" style="margin-left:8px;font-size:11px;padding:3px 8px;">🎯 Goal Progress</button>` : ''}
          <div class="spacer"></div>
          <button class="ghost compact del-ex danger" title="Remove exercise">Remove</button>
        </div>
        <div class="ex-hint">${hint}</div>
        <table class="sets">
          <thead>
            <tr>
              <th class="n">Set</th>
              <th class="w">${bw ? 'Added kg' : 'Weight (kg)'}</th>
              <th class="r">Reps</th>
              <th class="e1rm">${bw ? 'Tot E1RM' : 'Est 1RM'}</th>
              <th class="x"></th>
            </tr>
          </thead>
          <tbody>${setsRows}</tbody>
        </table>
        <div class="ex-foot">
          <button class="ghost compact add-set">+ Add set</button>
        </div>
      </div>
    `;
  }).join('');

  bindLogEvents();
}

function bindLogEvents() {
  const host = $('#log-exercises');

  // Input changes
  host.querySelectorAll('input.set-w, input.set-r').forEach((inp) => {
    inp.addEventListener('input', (e) => {
      const tr = e.target.closest('tr');
      const exIdx = Number(tr.dataset.ex);
      const sIdx = Number(tr.dataset.set);
      const wVal = tr.querySelector('.set-w').value.trim();
      const rVal = tr.querySelector('.set-r').value.trim();

      const w = wVal === '' ? null : Number(wVal);
      const r = rVal === '' ? null : Number(rVal);

      logExercises[exIdx].sets[sIdx].weight = w;
      logExercises[exIdx].sets[sIdx].reps = r;

      const e1rmEl = tr.querySelector('.e1rm');
      if (r && r > 0 && w !== null) {
        const dateStr = $('#log-date').value || today();
        const eVal = Math.round(BodyWeightUtils.calculateE1rm(logExercises[exIdx].name, dateStr, w, r) * 10) / 10;
        e1rmEl.textContent = `${eVal}k`;
      } else {
        e1rmEl.textContent = '–';
      }

      persistDraft();
    });

    inp.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        const tr = e.target.closest('tr');
        const exIdx = Number(tr.dataset.ex);
        logExercises[exIdx].sets.push({
          setNo: logExercises[exIdx].sets.length + 1,
          weight: logExercises[exIdx].sets[logExercises[exIdx].sets.length - 1]?.weight ?? null,
          reps: null,
          notes: '',
        });
        renderLogExercises();
        persistDraft();
      }
    });
  });

  // Add set button
  host.querySelectorAll('.add-set').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const card = e.target.closest('.ex-card');
      const exIdx = Number(card.dataset.ex);
      const lastSet = logExercises[exIdx].sets[logExercises[exIdx].sets.length - 1];
      logExercises[exIdx].sets.push({
        setNo: logExercises[exIdx].sets.length + 1,
        weight: lastSet ? lastSet.weight : null,
        reps: null,
        notes: '',
      });
      renderLogExercises();
      persistDraft();
    });
  });

  // Delete set button
  host.querySelectorAll('.del-set').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const tr = e.target.closest('tr');
      const exIdx = Number(tr.dataset.ex);
      const sIdx = Number(tr.dataset.set);
      logExercises[exIdx].sets.splice(sIdx, 1);
      if (!logExercises[exIdx].sets.length) {
        logExercises.splice(exIdx, 1);
      }
      renderLogExercises();
      persistDraft();
    });
  });

  // Remove entire exercise
  host.querySelectorAll('.del-ex').forEach((btn) => {
    btn.addEventListener('click', (e) => {
      const card = e.target.closest('.ex-card');
      const exIdx = Number(card.dataset.ex);
      logExercises.splice(exIdx, 1);
      renderLogExercises();
      persistDraft();
    });
  });

  // Jump to progress
  host.querySelectorAll('[data-jump-progress]').forEach((el) => {
    el.addEventListener('click', () => {
      showView('progress', el.dataset.jumpProgress);
    });
  });

  // Goal Progress button
  host.querySelectorAll('.goal-progress-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      const exName = btn.dataset.goalEx;
      const g = getActiveGoalForExercise(exName);
      if (g) openGoalProgressModal(g);
    });
  });
}

function loadPlanIntoLogger(plan) {
  if (!plan || !plan.exercises) return;
  $('#log-title').value = plan.title || '';
  $('#log-notes').value = plan.notes || '';
  $('#log-date').value = today();

  logExercises = plan.exercises.map((item) => {
    const prevSets = sessionsFor(item.exerciseName);
    const lastSess = prevSets[prevSets.length - 1];
    const lastWeight = lastSess?.top ?? null;

    const sets = (item.prescribedSets || []).map((ps, idx) => ({
      setNo: idx + 1,
      weight: ps.weight !== '' && ps.weight !== null ? Number(ps.weight) : lastWeight,
      reps: ps.reps !== '' && ps.reps !== null ? Number(ps.reps) : 8,
      notes: ps.notes || '',
    }));

    return {
      name: item.exerciseName,
      sets: sets.length ? sets : [{ setNo: 1, weight: lastWeight, reps: 8, notes: '' }],
    };
  });

  renderLogExercises();
  persistDraft();
  toast(`Loaded "${plan.title || 'Workout'}" into logger!`, 'success');
}

async function saveWorkoutFromLog() {
  const dateVal = $('#log-date').value;
  if (!dateVal) return toast('Workout needs a date', 'error');

  const setsToSave = [];
  for (const ex of logExercises) {
    let sNo = 1;
    for (const s of ex.sets) {
      if (s.reps === null && s.weight === null) continue;
      setsToSave.push({
        exercise: ex.name,
        setNo: sNo++,
        weight: s.weight,
        reps: s.reps,
        notes: s.notes || '',
      });
    }
  }

  if (!setsToSave.length) return toast('Add at least one set with weight or reps', 'error');

  const workoutDateDdMon = fmtDdMonYyyy(dateVal);
  const workoutPayload = {
    workout: {
      date: workoutDateDdMon,
      title: $('#log-title').value.trim(),
      notes: $('#log-notes').value.trim(),
    },
    sets: setsToSave,
  };

  await busy(async () => {
    const res = await window.api.saveWorkout(workoutPayload);
    setState(res.state || res);
    await window.api.clearDraft();

    logExercises = [];
    $('#log-title').value = '';
    $('#log-notes').value = '';
    $('#log-date').value = today();
    renderLogExercises();

    toast('Workout saved!', 'success');

    // If any goal was updated in this session, surface the Exercise Goal Report
    if (res.updatedGoals && res.updatedGoals.length > 0) {
      const topGoal = res.updatedGoals[0];
      toast(`🎯 Goal progress logged for ${topGoal.exerciseName}!`, 'success');
      setTimeout(() => openExerciseGoalReportModal(topGoal.id), 400);
    } else {
      showView('history');
    }
  });
}

// ---------------------------------------------------------------- PROGRAM SPLIT BUILDER VIEW
let selectedProgramDayIdx = 0;

function renderProgramView() {
  const progs = S.programs || [];
  const activeProg = progs.find((p) => p.id === S.activeProgramId) || progs[0] || null;

  const emptyEl = $('#program-empty-state');
  const contentEl = $('#program-content');

  if (!activeProg) {
    emptyEl.classList.remove('hidden');
    contentEl.classList.add('hidden');
    $('#program-active-name').textContent = 'No Programs';
    return;
  }

  emptyEl.classList.add('hidden');
  contentEl.classList.remove('hidden');

  $('#program-active-name').textContent = activeProg.programName || 'Custom Program';

  const days = activeProg.days || [];
  selectedProgramDayIdx = Math.max(0, Math.min(selectedProgramDayIdx, Math.max(0, days.length - 1)));
  const activeDay = days[selectedProgramDayIdx] || null;

  // Render days tabs
  $('#program-days-label').textContent = `WORKOUT DAYS (${days.length})`;
  const daysRow = $('#program-days-row');
  daysRow.innerHTML = days.map((d, idx) => {
    const isActive = idx === selectedProgramDayIdx;
    return `
      <div class="day-tab ${isActive ? 'active' : ''}" data-day-idx="${idx}">
        <span>${esc(d.dayName || `Day ${idx + 1}`)}</span>
        <span class="count-badge">${(d.exercises || []).length}</span>
      </div>
    `;
  }).join('');

  daysRow.querySelectorAll('.day-tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      selectedProgramDayIdx = Number(tab.dataset.dayIdx);
      renderProgramView();
    });
  });

  // Render active day card & exercises
  if (!activeDay) {
    $('#program-day-name').textContent = 'No Days Created';
    $('#program-day-focus').textContent = 'Click "+ Add Day" above to create your first training day.';
    $('#program-day-edit-btn').classList.add('hidden');
    $('#program-day-delete-btn').classList.add('hidden');
    $('#program-exercises-list').innerHTML = '<div class="empty">No days in this program. Tap "+ Add Day" above!</div>';
    $('#program-add-exercise-card').classList.add('hidden');
    $('#program-bottom-bar').classList.add('hidden');
    return;
  }

  $('#program-day-name').textContent = activeDay.dayName || `Day ${selectedProgramDayIdx + 1}`;
  $('#program-day-focus').textContent = activeDay.focus ? `Focus: ${activeDay.focus}` : '';
  $('#program-day-edit-btn').classList.remove('hidden');
  $('#program-day-delete-btn').classList.remove('hidden');
  $('#program-add-exercise-card').classList.remove('hidden');

  const exList = activeDay.exercises || [];
  const totalSets = exList.reduce((sum, e) => sum + (e.targetSets || 3), 0);

  if (!exList.length) {
    $('#program-exercises-list').innerHTML = '<div class="empty">No exercises added to this day yet. Click "+ Add Exercise to Day" below!</div>';
    $('#program-bottom-bar').classList.add('hidden');
  } else {
    $('#program-exercises-list').innerHTML = exList.map((item, eIdx) => {
      const info = exInfo(item.exerciseName);
      const bw = isBW(item.exerciseName);
      const prevSessions = sessionsFor(item.exerciseName);
      const last = prevSessions[prevSessions.length - 1];
      const hint = last ? `Last: ${summarize(item.exerciseName, last.sets)}` : 'First time';

      return `
        <div class="prog-ex-card" data-idx="${eIdx}">
          <div class="prog-ex-left">
            <div class="prog-ex-idx">${eIdx + 1}</div>
            <div class="prog-ex-meta">
              <div class="name-row">
                <span class="name">${esc(item.exerciseName)}</span>
                <span class="tag">${esc(info.category || 'Other')}</span>
                ${bw ? '<span class="tag" style="color:var(--pr);border-color:rgba(255,209,102,0.4)">BW</span>' : ''}
              </div>
              <div class="hint">${hint}</div>
            </div>
          </div>
          <div class="prog-ex-right">
            <div class="steppers-group">
              <div class="stepper-pill">
                <button class="stepper-btn dec-sets">−</button>
                <div class="stepper-value"><span class="val">${item.targetSets || 3}</span><span class="lbl">Sets</span></div>
                <button class="stepper-btn inc-sets">+</button>
              </div>
              <div class="stepper-pill">
                <button class="stepper-btn dec-reps">−</button>
                <div class="stepper-value"><span class="val">${item.targetReps || 8}</span><span class="lbl">Reps</span></div>
                <button class="stepper-btn inc-reps">+</button>
              </div>
            </div>
            <div class="reorder-group">
              <button class="ghost compact reorder-btn move-up" ${eIdx === 0 ? 'disabled' : ''}>▲</button>
              <button class="ghost compact reorder-btn move-down" ${eIdx === exList.length - 1 ? 'disabled' : ''}>▼</button>
            </div>
            <button class="icon del-prog-ex danger" title="Remove exercise">✕</button>
          </div>
        </div>
      `;
    }).join('');

    $('#program-bottom-bar').classList.remove('hidden');
    $('#program-day-summary').textContent = `${activeDay.dayName}: ${exList.length} exercise${exList.length === 1 ? '' : 's'} • ${totalSets} working sets`;
  }

  bindProgramViewEvents(activeProg, activeDay);
}

function bindProgramViewEvents(activeProg, activeDay) {
  // Steppers & Reorder
  const host = $('#program-exercises-list');
  const days = activeProg.days || [];
  const exList = activeDay ? (activeDay.exercises || []) : [];

  host.querySelectorAll('.prog-ex-card').forEach((card) => {
    const eIdx = Number(card.dataset.idx);

    card.querySelector('.dec-sets')?.addEventListener('click', () => {
      if (exList[eIdx].targetSets > 1) {
        exList[eIdx].targetSets--;
        saveCurrentProgramDays(activeProg, days);
      }
    });

    card.querySelector('.inc-sets')?.addEventListener('click', () => {
      if (exList[eIdx].targetSets < 15) {
        exList[eIdx].targetSets++;
        saveCurrentProgramDays(activeProg, days);
      }
    });

    card.querySelector('.dec-reps')?.addEventListener('click', () => {
      if (exList[eIdx].targetReps > 1) {
        exList[eIdx].targetReps--;
        saveCurrentProgramDays(activeProg, days);
      }
    });

    card.querySelector('.inc-reps')?.addEventListener('click', () => {
      if (exList[eIdx].targetReps < 50) {
        exList[eIdx].targetReps++;
        saveCurrentProgramDays(activeProg, days);
      }
    });

    card.querySelector('.move-up')?.addEventListener('click', () => {
      if (eIdx > 0) {
        const item = exList.splice(eIdx, 1)[0];
        exList.splice(eIdx - 1, 0, item);
        saveCurrentProgramDays(activeProg, days);
      }
    });

    card.querySelector('.move-down')?.addEventListener('click', () => {
      if (eIdx < exList.length - 1) {
        const item = exList.splice(eIdx, 1)[0];
        exList.splice(eIdx + 1, 0, item);
        saveCurrentProgramDays(activeProg, days);
      }
    });

    card.querySelector('.del-prog-ex')?.addEventListener('click', () => {
      exList.splice(eIdx, 1);
      saveCurrentProgramDays(activeProg, days);
    });
  });

  // Start Workout / Load in Logger
  $('#program-start-workout-btn').onclick = () => {
    if (!activeDay || !activeDay.exercises?.length) return;
    const plan = {
      title: activeDay.dayName,
      notes: activeDay.focus ? `Focus: ${activeDay.focus}` : `Loaded from ${activeProg.programName || 'Program'}`,
      exercises: activeDay.exercises.map((item) => ({
        exerciseName: item.exerciseName,
        category: item.category,
        prescribedSets: Array.from({ length: item.targetSets || 3 }, () => ({
          weight: '',
          reps: item.targetReps || 8,
          notes: '',
        })),
      })),
    };
    showView('log', plan);
  };
}

async function saveCurrentProgramDays(activeProg, days) {
  const updatedProg = { ...activeProg, days };
  await busy(async () => {
    const res = await window.api.saveProgram(updatedProg);
    setState(res);
    renderProgramView();
  });
}

function initProgramModule() {
  $('#program-create-btn').addEventListener('click', openCreateProgramDialog);
  $('#program-empty-create-btn').addEventListener('click', openCreateProgramDialog);
  $('#program-switcher-btn').addEventListener('click', openManageProgramsDialog);
  $('#program-manage-btn').addEventListener('click', openManageProgramsDialog);

  $('#program-add-day-btn').addEventListener('click', openAddDayDialog);
  $('#program-day-edit-btn').addEventListener('click', openEditDayDialog);
  $('#program-day-delete-btn').addEventListener('click', deleteCurrentDay);
  $('#program-add-ex-btn').addEventListener('click', openAddExerciseToDayDialog);
}

async function openCreateProgramDialog() {
  const res = await modal({
    title: 'Create New Program',
    html: `
      <label>Program Name
        <input type="text" id="m-prog-name" placeholder="e.g. Push Pull Legs, Upper Lower, 4-Day Split" />
      </label>
    `,
    okText: 'Create Program',
  });
  if (res) {
    const name = res.querySelector('#m-prog-name').value.trim();
    if (name) {
      await busy(async () => {
        const state = await window.api.createProgram(name);
        setState(state);
        selectedProgramDayIdx = 0;
        showView('program');
        toast(`Program "${name}" created!`, 'success');
      });
    }
  }
}

async function openManageProgramsDialog() {
  const progs = S.programs || [];
  const rows = progs.map((p) => `
    <div class="bw-entry-row" style="padding:10px 12px;margin-bottom:8px;">
      <div>
        <div style="font-weight:700;color:${p.id === S.activeProgramId ? 'var(--accent)' : 'var(--text)'};font-size:15px;">
          ${esc(p.programName || 'Program')} ${p.id === S.activeProgramId ? '✓' : ''}
        </div>
        <div class="muted small">${(p.days || []).length} workout days</div>
      </div>
      <div style="display:flex;gap:6px;">
        <button class="ghost compact m-switch-prog" data-id="${p.id}">Select</button>
        <button class="ghost compact m-dup-prog" data-id="${p.id}" title="Duplicate">Copy</button>
        <button class="ghost compact m-ren-prog" data-id="${p.id}" title="Rename">Rename</button>
        <button class="danger compact m-del-prog" data-id="${p.id}" title="Delete">✕</button>
      </div>
    </div>
  `).join('');

  const html = `
    <div style="max-height:360px;overflow-y:auto;margin-bottom:12px;">${rows || '<div class="muted">No programs yet.</div>'}</div>
    <button id="m-new-prog-sub" class="primary full-width">+ Create New Program</button>
  `;

  const m = $('#modal');
  $('#modal-title').textContent = `Manage Programs (${progs.length})`;
  $('#modal-body').innerHTML = html;
  $('.modal-box', m).classList.add('wide');
  $('#modal-ok').classList.add('hidden');
  $('#modal-cancel').textContent = 'Close';
  m.classList.remove('hidden');

  $('#m-new-prog-sub').onclick = () => {
    m.classList.add('hidden');
    openCreateProgramDialog();
  };

  $$('.m-switch-prog', m).forEach((b) => {
    b.onclick = async () => {
      m.classList.add('hidden');
      const state = await window.api.setActiveProgramId(b.dataset.id);
      setState(state);
      selectedProgramDayIdx = 0;
      renderProgramView();
    };
  });

  $$('.m-dup-prog', m).forEach((b) => {
    b.onclick = async () => {
      m.classList.add('hidden');
      const state = await window.api.duplicateProgram(b.dataset.id);
      setState(state);
      renderProgramView();
      toast('Program duplicated!', 'success');
    };
  });

  $$('.m-ren-prog', m).forEach((b) => {
    b.onclick = async () => {
      m.classList.add('hidden');
      const p = progs.find((x) => x.id === b.dataset.id);
      if (!p) return;
      const res = await modal({
        title: 'Rename Program',
        html: `<label>Program Name<input type="text" id="m-ren-val" value="${esc(p.programName)}" /></label>`,
        okText: 'Save',
      });
      if (res) {
        const newName = res.querySelector('#m-ren-val').value.trim();
        if (newName) {
          const state = await window.api.saveProgram({ ...p, programName: newName });
          setState(state);
          renderProgramView();
        }
      }
    };
  });

  $$('.m-del-prog', m).forEach((b) => {
    b.onclick = async () => {
      if (confirm('Delete this program? This cannot be undone.')) {
        m.classList.add('hidden');
        const state = await window.api.deleteProgram(b.dataset.id);
        setState(state);
        selectedProgramDayIdx = 0;
        renderProgramView();
        toast('Program deleted');
      }
    };
  });
}

async function openAddDayDialog() {
  const progs = S.programs || [];
  const activeProg = progs.find((p) => p.id === S.activeProgramId) || progs[0];
  if (!activeProg) return;

  const defaultDayNum = (activeProg.days || []).length + 1;
  const res = await modal({
    title: 'Add Workout Day',
    html: `
      <label>Day Name
        <input type="text" id="m-day-name" value="Day ${defaultDayNum}: " placeholder="e.g. Day 1: Push, Upper Body, Chest & Triceps" />
      </label>
      <label>Focus (Optional)
        <input type="text" id="m-day-focus" placeholder="e.g. Quad overload, Heavy bench progression" />
      </label>
    `,
    okText: 'Add Day',
  });

  if (res) {
    const dayName = res.querySelector('#m-day-name').value.trim();
    const focus = res.querySelector('#m-day-focus').value.trim();
    if (dayName) {
      const days = [...(activeProg.days || [])];
      days.push({
        dayIndex: days.length,
        dayName,
        focus,
        exercises: [],
      });
      selectedProgramDayIdx = days.length - 1;
      await saveCurrentProgramDays(activeProg, days);
    }
  }
}

async function openEditDayDialog() {
  const progs = S.programs || [];
  const activeProg = progs.find((p) => p.id === S.activeProgramId) || progs[0];
  if (!activeProg || !activeProg.days) return;
  const day = activeProg.days[selectedProgramDayIdx];
  if (!day) return;

  const res = await modal({
    title: 'Edit Workout Day',
    html: `
      <label>Day Name
        <input type="text" id="m-day-name" value="${esc(day.dayName)}" />
      </label>
      <label>Focus (Optional)
        <input type="text" id="m-day-focus" value="${esc(day.focus || '')}" />
      </label>
    `,
    okText: 'Save Changes',
  });

  if (res) {
    day.dayName = res.querySelector('#m-day-name').value.trim() || day.dayName;
    day.focus = res.querySelector('#m-day-focus').value.trim();
    await saveCurrentProgramDays(activeProg, activeProg.days);
  }
}

async function deleteCurrentDay() {
  const progs = S.programs || [];
  const activeProg = progs.find((p) => p.id === S.activeProgramId) || progs[0];
  if (!activeProg || !activeProg.days) return;
  if (!confirm(`Delete day "${activeProg.days[selectedProgramDayIdx]?.dayName}"?`)) return;

  activeProg.days.splice(selectedProgramDayIdx, 1);
  selectedProgramDayIdx = Math.max(0, selectedProgramDayIdx - 1);
  await saveCurrentProgramDays(activeProg, activeProg.days);
}

async function openAddExerciseToDayDialog() {
  const progs = S.programs || [];
  const activeProg = progs.find((p) => p.id === S.activeProgramId) || progs[0];
  if (!activeProg || !activeProg.days) return;
  const day = activeProg.days[selectedProgramDayIdx];
  if (!day) return;

  const allEx = S.exercises || [];
  const categories = ['All', ...new Set(allEx.map((e) => e.category || 'Other').sort())];

  let chosenCategory = 'All';
  let targetSets = 3;
  let targetReps = 8;

  const renderPicker = () => {
    const q = ($('#m-ex-search') ? $('#m-ex-search').value : '').toLowerCase().trim();
    const filtered = allEx.filter((e) => {
      const matchCat = chosenCategory === 'All' || (e.category || 'Other').toLowerCase() === chosenCategory.toLowerCase();
      const matchQ = !q || e.name.toLowerCase().includes(q);
      return matchCat && matchQ;
    });

    const catChips = categories.map((c) => `
      <button class="chip ${c === chosenCategory ? 'active' : ''}" style="${c === chosenCategory ? 'background:var(--accent);color:#140d07;font-weight:700;' : ''}">${esc(c)}</button>
    `).join('');

    const rows = filtered.map((e) => `
      <div class="bw-entry-row picker-item" data-name="${esc(e.name)}" style="padding:8px 12px;margin-bottom:6px;cursor:pointer;">
        <div>
          <span style="font-weight:700;color:var(--text);">${esc(e.name)}</span>
          <span class="tag" style="margin-left:6px;">${esc(e.category || 'Other')}</span>
          ${e.bodyweight ? '<span class="tag" style="color:var(--pr)">BW</span>' : ''}
        </div>
        <button class="primary compact">+ Add</button>
      </div>
    `).join('');

    return { catChips, rows };
  };

  const m = $('#modal');
  $('#modal-title').textContent = `Add Exercise to ${day.dayName}`;
  $('.modal-box', m).classList.add('large');
  $('#modal-ok').classList.add('hidden');
  $('#modal-cancel').textContent = 'Done';

  $('#modal-body').innerHTML = `
    <div style="display:flex;gap:12px;align-items:center;margin-bottom:12px;background:var(--bg);padding:8px 12px;border-radius:10px;">
      <span class="muted small uppercase font-bold">PRESCRIBED TARGETS:</span>
      <div class="stepper-pill">
        <button class="stepper-btn" id="m-set-dec">−</button>
        <div class="stepper-value"><span class="val" id="m-sets-val">3</span><span class="lbl">Sets</span></div>
        <button class="stepper-btn" id="m-set-inc">+</button>
      </div>
      <div class="stepper-pill">
        <button class="stepper-btn" id="m-rep-dec">−</button>
        <div class="stepper-value"><span class="val" id="m-reps-val">8</span><span class="lbl">Reps</span></div>
        <button class="stepper-btn" id="m-rep-inc">+</button>
      </div>
    </div>
    <input type="search" id="m-ex-search" placeholder="Search exercises..." style="margin-bottom:10px;" />
    <div id="m-cat-chips" class="chips" style="margin-bottom:12px;"></div>
    <div id="m-picker-list" style="max-height:300px;overflow-y:auto;"></div>
  `;

  m.classList.remove('hidden');

  const updateList = () => {
    const { catChips, rows } = renderPicker();
    $('#m-cat-chips').innerHTML = catChips;
    $('#m-picker-list').innerHTML = rows || '<div class="muted small" style="padding:16px;text-align:center;">No exercises match search.</div>';

    $$('#m-cat-chips .chip').forEach((c) => {
      c.onclick = () => {
        chosenCategory = c.textContent.trim();
        updateList();
      };
    });

    $$('.picker-item', m).forEach((row) => {
      row.onclick = () => {
        const exName = row.dataset.name;
        day.exercises = day.exercises || [];
        day.exercises.push({
          exerciseName: exName,
          category: exInfo(exName).category || 'Other',
          targetSets,
          targetReps,
        });
        saveCurrentProgramDays(activeProg, activeProg.days);
        toast(`Added ${exName} (${targetSets}×${targetReps})`, 'success');
      };
    });
  };

  $('#m-set-dec').onclick = () => { if (targetSets > 1) { targetSets--; $('#m-sets-val').textContent = targetSets; } };
  $('#m-set-inc').onclick = () => { if (targetSets < 15) { targetSets++; $('#m-sets-val').textContent = targetSets; } };
  $('#m-rep-dec').onclick = () => { if (targetReps > 1) { targetReps--; $('#m-reps-val').textContent = targetReps; } };
  $('#m-rep-inc').onclick = () => { if (targetReps < 50) { targetReps++; $('#m-reps-val').textContent = targetReps; } };

  $('#m-ex-search').oninput = updateList;
  updateList();
}

async function openLoadProgramDayDialog() {
  const progs = S.programs || [];
  if (!progs.length) {
    return toast('No programs created yet. Create one in the Program tab first!', 'error');
  }

  const daysOptions = [];
  for (const p of progs) {
    for (let i = 0; i < (p.days || []).length; i++) {
      const d = p.days[i];
      daysOptions.push({
        progName: p.programName,
        dayName: d.dayName,
        focus: d.focus,
        day: d,
      });
    }
  }

  if (!daysOptions.length) {
    return toast('No workout days exist in your programs yet.', 'error');
  }

  const rows = daysOptions.map((opt, i) => `
    <div class="bw-entry-row load-prog-day-item" data-idx="${i}" style="padding:10px 12px;margin-bottom:8px;cursor:pointer;">
      <div>
        <div style="font-weight:700;color:var(--text);font-size:14.5px;">${esc(opt.dayName)}</div>
        <div class="muted small">${esc(opt.progName)} • ${(opt.day.exercises || []).length} exercises ${opt.focus ? `(${esc(opt.focus)})` : ''}</div>
      </div>
      <button class="primary compact">Load</button>
    </div>
  `).join('');

  const m = $('#modal');
  $('#modal-title').textContent = 'Load Workout Day from Program';
  $('#modal-body').innerHTML = `<div style="max-height:360px;overflow-y:auto;">${rows}</div>`;
  $('.modal-box', m).classList.add('wide');
  $('#modal-ok').classList.add('hidden');
  $('#modal-cancel').textContent = 'Cancel';
  m.classList.remove('hidden');

  $$('.load-prog-day-item', m).forEach((item) => {
    item.onclick = () => {
      m.classList.add('hidden');
      const idx = Number(item.dataset.idx);
      const opt = daysOptions[idx];
      const plan = {
        title: opt.day.dayName,
        notes: opt.day.focus ? `Focus: ${opt.day.focus}` : `Loaded from ${opt.progName}`,
        exercises: (opt.day.exercises || []).map((e) => ({
          exerciseName: e.exerciseName,
          category: e.category,
          prescribedSets: Array.from({ length: e.targetSets || 3 }, () => ({
            weight: '',
            reps: e.targetReps || 8,
            notes: '',
          })),
        })),
      };
      loadPlanIntoLogger(plan);
    };
  });
}

// ---------------------------------------------------------------- GOALS MODULE (Decluttered)
let activeGoalFilterTab = 'all';

function getActiveGoalForExercise(name) {
  const k = String(name || '').trim().toLowerCase();
  return (S.goals || []).find((g) => g.enabled !== false && String(g.exerciseName || g.exercise || '').toLowerCase() === k) || null;
}

function evaluateGoalDesktop(goal) {
  const name = goal.exerciseName || goal.exercise;
  const bw = isBW(name);
  const sessions = sessionsFor(name);
  const bestSess = sessions.reduce((m, s) => (s.e1rm !== null && (!m || s.e1rm > m.e1rm) ? s : m), null);
  const latestSess = sessions[sessions.length - 1] || null;
  const bestE1rm = bestSess?.e1rm || 0;
  const recentE1rm = latestSess?.e1rm || bestE1rm;
  const currentWorkingE1rm = recentE1rm > 0 ? Math.max(recentE1rm, bestE1rm * 0.92) : 40;

  const targetE1rm = goal.goalType === 'SETS_REPS'
    ? round(BodyWeightUtils.calculateE1rm(name, today(), goal.targetWeightKg || 0, Math.max(1, goal.targetReps || 8)))
    : round(goal.targetE1rmKg || 0);

  const startBase = goal.baselineE1rmKg > 0 ? goal.baselineE1rmKg : currentWorkingE1rm * 0.88;
  const rawPct = targetE1rm > startBase
    ? Math.round(((Math.max(bestE1rm, recentE1rm) - startBase) / (targetE1rm - startBase)) * 100)
    : (Math.max(bestE1rm, recentE1rm) >= targetE1rm ? 100 : 0);

  const isAchieved = !!goal.achievedDate || (targetE1rm > 0 && Math.max(bestE1rm, recentE1rm) >= targetE1rm);
  const progressPct = isAchieved ? 100 : Math.max(0, Math.min(100, rawPct));

  const targetHeadline = goal.goalType === 'SETS_REPS'
    ? `${goal.targetSets || 3} × ${goal.targetReps || 8} @ ${bw ? (goal.targetWeightKg === 0 ? 'BW' : `BW+${fmt(goal.targetWeightKg)}kg`) : `${fmt(goal.targetWeightKg)}kg`}`
    : `${fmt(targetE1rm)} kg 1RM`;

  const step = (exInfo(name).category === 'Arms' || name.toLowerCase().includes('raise')) ? 1.25 : 2.5;
  let nextSets = 3, nextReps = 5, nextWeight = round((currentWorkingE1rm / (1 + 5 / 30)) * 0.97, step);
  if (goal.goalType === 'SETS_REPS') {
    nextSets = goal.targetSets || 3;
    nextReps = goal.targetReps || 8;
    const baseW = round(currentWorkingE1rm / (1 + nextReps / 30), step);
    nextWeight = Math.min(goal.targetWeightKg || baseW, Math.max(step, baseW));
  }
  const nextSessionSummary = `${nextSets} × ${nextReps} @ ${bw && nextWeight === 0 ? 'BW' : fmt(nextWeight) + 'kg'}`;

  // 4-Phase milestones
  const phases = [
    { title: 'Phase 1: Accumulation', prescription: `${nextSets} × ${nextReps + 2} @ ${fmt(Math.max(0, nextWeight - step * 2))}kg` },
    { title: 'Phase 2: Intensification', prescription: `${nextSets} × ${nextReps} @ ${fmt(nextWeight)}kg` },
    { title: 'Phase 3: Realization', prescription: `${nextSets} × ${Math.max(3, nextReps - 2)} @ ${fmt(nextWeight + step)}kg` },
    { title: 'Phase 4: Goal Attempt', prescription: targetHeadline },
  ];

  return {
    goal,
    name,
    bw,
    category: exInfo(name).category || 'Other',
    bestE1rm: round(bestE1rm),
    targetE1rm,
    targetHeadline,
    progressPct,
    isAchieved,
    statusLabel: isAchieved ? 'Achieved 🏆' : (progressPct >= 80 ? 'On Track' : 'Building'),
    nextSets,
    nextReps,
    nextWeight,
    nextSessionSummary,
    phases,
  };
}

function renderGoals() {
  const allGoals = S.goals || [];
  const evals = allGoals.map((g) => evaluateGoalDesktop(g));

  const achievedCount = evals.filter((e) => e.isAchieved).length;
  const activeCount = evals.filter((e) => !e.isAchieved && e.goal.enabled !== false).length;

  $('#goals-count-all').textContent = evals.length;
  $('#goals-count-active').textContent = activeCount;
  $('#goals-count-achieved').textContent = achievedCount;

  const filtered = evals.filter((e) => {
    if (activeGoalFilterTab === 'active') return !e.isAchieved && e.goal.enabled !== false;
    if (activeGoalFilterTab === 'achieved') return e.isAchieved;
    return true;
  });

  const host = $('#goals-list');
  if (!filtered.length) {
    host.innerHTML = `
      <div class="empty">
        ${allGoals.length ? 'No goals match this filter.' : 'No strength goals set yet! Click <b>+ New Goal</b> above to set a 1RM or Sets × Reps target.'}
      </div>
    `;
    return;
  }

  host.innerHTML = filtered.map((e) => `
    <div class="goal-card ${e.isAchieved ? 'achieved' : ''}" data-id="${e.goal.id}">
      <div class="goal-card-top">
        <div class="goal-card-name">
          <span>${esc(e.name)}</span>
          <span class="tag">${esc(e.category)}</span>
          ${e.bw ? '<span class="tag" style="color:var(--pr)">BW</span>' : ''}
        </div>
        <div class="goal-card-target">${esc(e.targetHeadline)}</div>
      </div>

      <div class="goal-card-meta">
        <div>
          <span class="goal-pill ${e.isAchieved ? 'achieved' : (e.goal.enabled === false ? 'paused' : '')}">
            ${e.goal.enabled === false ? 'Paused' : esc(e.statusLabel)}
          </span>
        </div>
        <div class="muted small font-bold">
          Best: ${e.bestE1rm}kg • ${e.progressPct}%
        </div>
      </div>

      <div class="goal-progress-bar">
        <div class="goal-progress-fill ${e.isAchieved ? 'achieved' : ''}" style="width:${e.progressPct}%;"></div>
      </div>

      <div class="goal-card-foot">
        <div class="goal-next-summary">${e.isAchieved ? 'Goal Reached! 🏆' : `Next: ${esc(e.nextSessionSummary)}`}</div>
        <div class="goal-actions-group">
          ${!e.isAchieved ? `<button class="primary compact train-goal-btn" data-id="${e.goal.id}">▶ Train</button>` : ''}
          <button class="ghost compact toggle-goal-expand">Details ▾</button>
        </div>
      </div>

      <div class="goal-expanded-body hidden">
        <div class="small font-bold uppercase muted">Prescribed Working Sets</div>
        <div style="font-size:13px;color:var(--text);">${e.nextSets} sets × ${e.nextReps} reps @ ${e.bw && e.nextWeight === 0 ? 'BW' : `${fmt(e.nextWeight)}kg`}</div>

        <div class="small font-bold uppercase muted" style="margin-top:6px;">Milestones</div>
        <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(180px, 1fr));gap:6px;">
          ${e.phases.map((p) => `
            <div style="background:var(--bg);border:1px solid var(--border);border-radius:6px;padding:6px 8px;font-size:11.5px;">
              <div style="font-weight:700;color:var(--text);">${esc(p.title)}</div>
              <div style="color:var(--accent);">${esc(p.prescription)}</div>
            </div>
          `).join('')}
        </div>

        <div class="btn-row" style="margin-top:10px;">
          <button class="ghost compact inspect-report-btn" data-id="${e.goal.id}">📊 Full Report</button>
          <button class="ghost compact edit-goal-btn" data-id="${e.goal.id}">Edit</button>
          <button class="ghost compact toggle-goal-btn" data-id="${e.goal.id}">${e.goal.enabled === false ? 'Resume' : 'Pause'}</button>
          <button class="danger compact delete-goal-btn" data-id="${e.goal.id}">Delete</button>
        </div>
      </div>
    </div>
  `).join('');

  bindGoalEvents(evals);
}

function bindGoalEvents(evals) {
  const host = $('#goals-list');

  // Filter tabs
  $$('.goal-tab').forEach((t) => {
    t.onclick = () => {
      $$('.goal-tab').forEach((x) => x.classList.remove('active'));
      t.classList.add('active');
      activeGoalFilterTab = t.dataset.tab;
      renderGoals();
    };
  });

  // Toggle expand
  host.querySelectorAll('.toggle-goal-expand').forEach((btn) => {
    btn.onclick = (e) => {
      const card = e.target.closest('.goal-card');
      const body = card.querySelector('.goal-expanded-body');
      const isHidden = body.classList.toggle('hidden');
      btn.textContent = isHidden ? 'Details ▾' : 'Hide ▴';
    };
  });

  // Train button
  host.querySelectorAll('.train-goal-btn').forEach((btn) => {
    btn.onclick = () => {
      const ev = evals.find((x) => x.goal.id === btn.dataset.id);
      if (!ev) return;
      const plan = {
        title: `${ev.name} Goal Session`,
        notes: `Target: ${ev.targetHeadline}`,
        exercises: [{
          exerciseName: ev.name,
          category: ev.category,
          prescribedSets: Array.from({ length: ev.nextSets }, () => ({
            weight: ev.nextWeight || '',
            reps: ev.nextReps || 8,
            notes: '',
          })),
        }],
      };
      showView('log', plan);
    };
  });

  // Actions
  host.querySelectorAll('.inspect-report-btn').forEach((btn) => {
    btn.onclick = () => openExerciseGoalReportModal(btn.dataset.id);
  });

  host.querySelectorAll('.edit-goal-btn').forEach((btn) => {
    btn.onclick = () => {
      const ev = evals.find((x) => x.goal.id === btn.dataset.id);
      if (ev) openGoalModal(ev.goal);
    };
  });

  host.querySelectorAll('.toggle-goal-btn').forEach((btn) => {
    btn.onclick = async () => {
      const state = await window.api.toggleGoalEnabled(btn.dataset.id);
      setState(state);
      renderGoals();
    };
  });

  host.querySelectorAll('.delete-goal-btn').forEach((btn) => {
    btn.onclick = async () => {
      if (confirm('Delete this goal?')) {
        const state = await window.api.deleteGoal(btn.dataset.id);
        setState(state);
        renderGoals();
        toast('Goal deleted');
      }
    };
  });
}

function initGoalsModule() {
  $('#goals-add-btn').addEventListener('click', () => openGoalModal(null));
  $('#goals-all-report').addEventListener('click', openAllGoalsMasterReportModal);
  $('#progress-open-goals').addEventListener('click', () => showView('goals'));
}

async function openGoalModal(existingGoal = null) {
  const exercises = S.exercises || [];
  const exNames = exercises.map((e) => e.name).sort();

  const isSetsReps = existingGoal?.goalType === 'SETS_REPS';
  const defaultEx = existingGoal?.exerciseName || exNames[0] || 'Bench Press';

  const res = await modal({
    title: existingGoal ? 'Edit Strength Goal' : 'Create Strength Goal',
    wide: true,
    html: `
      <div style="display:flex;gap:12px;margin-bottom:12px;">
        <label style="flex:1;">Exercise
          <input type="text" id="m-goal-ex" list="exercise-list" value="${esc(defaultEx)}" ${existingGoal ? 'disabled' : ''} />
        </label>
        <label style="width:180px;">Goal Mode
          <select id="m-goal-mode">
            <option value="E1RM" ${!isSetsReps ? 'selected' : ''}>Estimated 1RM</option>
            <option value="SETS_REPS" ${isSetsReps ? 'selected' : ''}>Sets × Reps @ Weight</option>
          </select>
        </label>
      </div>

      <div id="m-mode-1rm" class="${isSetsReps ? 'hidden' : ''}">
        <label>Target 1-Rep Max (kg)
          <input type="number" step="0.5" id="m-goal-1rm" value="${existingGoal?.targetE1rmKg || 100}" />
        </label>
      </div>

      <div id="m-mode-sets-reps" class="${!isSetsReps ? 'hidden' : ''}" style="display:grid;grid-template-columns:1fr 1fr 1.4fr;gap:10px;">
        <label>Sets<input type="number" id="m-goal-sets" value="${existingGoal?.targetSets || 3}" min="1" max="15" /></label>
        <label>Reps<input type="number" id="m-goal-reps" value="${existingGoal?.targetReps || 8}" min="1" max="50" /></label>
        <label>Weight (kg)<input type="number" step="0.5" id="m-goal-weight" value="${existingGoal?.targetWeightKg || 80}" /></label>
      </div>

      <label style="margin-top:12px;">Notes / Focus (Optional)
        <input type="text" id="m-goal-notes" value="${esc(existingGoal?.notes || '')}" placeholder="e.g. Pause bench at bottom, hit before summer" />
      </label>
    `,
    okText: existingGoal ? 'Save Goal' : 'Create Goal',
  });

  if (res) {
    const exName = res.querySelector('#m-goal-ex').value.trim();
    const mode = res.querySelector('#m-goal-mode').value;
    const notes = res.querySelector('#m-goal-notes').value.trim();

    const targetE1rmKg = Number(res.querySelector('#m-goal-1rm').value) || 0;
    const targetSets = Number(res.querySelector('#m-goal-sets').value) || 3;
    const targetReps = Number(res.querySelector('#m-goal-reps').value) || 8;
    const targetWeightKg = Number(res.querySelector('#m-goal-weight').value) || 0;

    const goalObj = {
      ...(existingGoal || {}),
      exerciseName: exName,
      goalType: mode,
      targetE1rmKg,
      targetSets,
      targetReps,
      targetWeightKg,
      notes,
    };

    const state = await window.api.saveGoal(goalObj);
    setState(state);
    renderGoals();
    toast('Goal saved!', 'success');
  }
}

function openExerciseGoalReportModal(goalId) {
  const g = (S.goals || []).find((x) => x.id === goalId);
  if (!g) return;
  const ev = evaluateGoalDesktop(g);
  const reports = g.sessionReports || [];

  const historyRows = reports.map((r) => `
    <tr>
      <td>${shortDate(r.date)}</td>
      <td>${r.sessionTopWeight}kg × ${r.sessionTopReps}</td>
      <td>${r.sessionBestE1rm}kg</td>
      <td>${r.workingSetsCount}</td>
      <td>${r.progressPct}%</td>
      <td>${r.achievedGoal ? '🏆 Hit!' : 'In progress'}</td>
    </tr>
  `).join('');

  modal({
    title: `📊 Goal Report: ${ev.name}`,
    wide: true,
    html: `
      <div class="stats" style="grid-template-columns:repeat(4,1fr);margin-bottom:14px;">
        <div class="stat"><div class="lbl">Target</div><div class="val" style="color:var(--accent);font-size:17px;">${esc(ev.targetHeadline)}</div></div>
        <div class="stat"><div class="lbl">Current Best</div><div class="val">${ev.bestE1rm}kg</div></div>
        <div class="stat"><div class="lbl">Progress</div><div class="val" style="color:${ev.isAchieved ? 'var(--pr)' : 'var(--up)'}">${ev.progressPct}%</div></div>
        <div class="stat"><div class="lbl">Status</div><div class="val" style="font-size:17px;">${esc(ev.statusLabel)}</div></div>
      </div>

      <div class="card" style="margin-bottom:14px;">
        <div class="small font-bold uppercase muted" style="margin-bottom:6px;">Next Session Prescription</div>
        <div style="font-size:14px;font-weight:700;color:var(--text);">${esc(ev.nextSessionSummary)}</div>
      </div>

      <div class="card">
        <div class="small font-bold uppercase muted" style="margin-bottom:8px;">Logged Goal Sessions (${reports.length})</div>
        <table class="table">
          <thead>
            <tr><th>Date</th><th>Top Set</th><th>E1RM</th><th>Sets</th><th>Progress</th><th>Status</th></tr>
          </thead>
          <tbody>${historyRows || '<tr><td colspan="6" class="muted">No goal sessions logged yet.</td></tr>'}</tbody>
        </table>
      </div>
    `,
    okText: 'Close',
    cancelText: false,
  });
}

function openAllGoalsMasterReportModal() {
  const allGoals = S.goals || [];
  const evals = allGoals.map((g) => evaluateGoalDesktop(g));

  const rows = evals.map((e) => `
    <div class="bw-entry-row" style="padding:10px 14px;margin-bottom:8px;">
      <div style="flex:1;">
        <span style="font-weight:700;font-size:15px;color:var(--text);">${esc(e.name)}</span>
        <span class="tag" style="margin-left:8px;">${esc(e.targetHeadline)}</span>
      </div>
      <div style="display:flex;align-items:center;gap:12px;">
        <span class="goal-pill ${e.isAchieved ? 'achieved' : ''}">${esc(e.statusLabel)}</span>
        <span style="font-weight:700;color:var(--accent);">${e.progressPct}%</span>
        <button class="ghost compact m-view-single-report" data-id="${e.goal.id}">View Report</button>
      </div>
    </div>
  `).join('');

  const m = $('#modal');
  $('#modal-title').textContent = `📊 Master All-Goals Report (${evals.length})`;
  $('#modal-body').innerHTML = `
    <div style="max-height:400px;overflow-y:auto;">
      ${rows || '<div class="muted">No goals configured yet.</div>'}
    </div>
  `;
  $('.modal-box', m).classList.add('large');
  $('#modal-ok').classList.add('hidden');
  $('#modal-cancel').textContent = 'Close';
  m.classList.remove('hidden');

  $$('.m-view-single-report', m).forEach((b) => {
    b.onclick = () => {
      m.classList.add('hidden');
      openExerciseGoalReportModal(b.dataset.id);
    };
  });
}

// ---------------------------------------------------------------- EXERCISES CATALOG VIEW
function renderExercisesCatalog() {
  const allEx = S.exercises || [];
  const categories = ['All', ...new Set(allEx.map((e) => e.category || 'Other').sort())];

  const chipsHost = $('#exercises-category-chips');
  const activeCat = chipsHost.dataset.cat || 'All';

  chipsHost.innerHTML = categories.map((c) => `
    <button class="chip ${c === activeCat ? 'active' : ''}" style="${c === activeCat ? 'background:var(--accent);color:#140d07;font-weight:700;' : ''}">${esc(c)}</button>
  `).join('');

  chipsHost.querySelectorAll('.chip').forEach((chip) => {
    chip.onclick = () => {
      chipsHost.dataset.cat = chip.textContent.trim();
      renderExercisesCatalog();
    };
  });

  const q = ($('#exercises-search-input').value || '').toLowerCase().trim();
  const filtered = allEx.filter((e) => {
    const matchCat = activeCat === 'All' || (e.category || 'Other').toLowerCase() === activeCat.toLowerCase();
    const matchQ = !q || e.name.toLowerCase().includes(q) || (e.description || '').toLowerCase().includes(q);
    return matchCat && matchQ;
  });

  const listHost = $('#exercises-catalog-list');
  listHost.innerHTML = filtered.map((e) => `
    <div class="ex-cat-card">
      <div>
        <div class="head">
          <span class="title">${esc(e.name)}</span>
          <div>
            <span class="tag">${esc(e.category || 'Other')}</span>
            ${e.bodyweight ? '<span class="tag" style="color:var(--pr)">BW</span>' : ''}
          </div>
        </div>
        <div class="desc" style="margin-top:6px;">${esc(e.description || 'Standard kinetic strength exercise.')}</div>
      </div>
      <div class="foot">
        <button class="ghost compact jump-ex-progress" data-name="${esc(e.name)}">↗ Progress</button>
        <button class="ghost compact edit-ex-btn" data-name="${esc(e.name)}">Edit</button>
      </div>
    </div>
  `).join('');

  listHost.querySelectorAll('.jump-ex-progress').forEach((b) => {
    b.onclick = () => showView('progress', b.dataset.name);
  });

  listHost.querySelectorAll('.edit-ex-btn').forEach((b) => {
    b.onclick = () => openEditExerciseModal(b.dataset.name);
  });
}

function initExercisesModule() {
  $('#exercises-search-input').addEventListener('input', renderExercisesCatalog);
  $('#exercises-add-btn').addEventListener('click', openAddExerciseModal);
}

async function openAddExerciseModal() {
  const res = await modal({
    title: 'Add Custom Exercise',
    html: `
      <label>Exercise Name<input type="text" id="m-ex-name" placeholder="e.g. Incline Dumbbell Press" /></label>
      <label>Category
        <select id="m-ex-cat">
          <option value="Chest">Chest</option>
          <option value="Back">Back</option>
          <option value="Legs">Legs</option>
          <option value="Shoulders">Shoulders</option>
          <option value="Arms">Arms</option>
          <option value="Core">Core</option>
          <option value="Other">Other</option>
        </select>
      </label>
      <label class="check" style="margin-top:10px;">
        <input type="checkbox" id="m-ex-bw" /> Bodyweight exercise (uses total-load E1RM)
      </label>
      <label style="margin-top:10px;">Description / Kinetic Benefit (Optional)
        <input type="text" id="m-ex-desc" placeholder="e.g. Upper chest clavicular head overload" />
      </label>
    `,
    okText: 'Add Exercise',
  });

  if (res) {
    const name = res.querySelector('#m-ex-name').value.trim();
    const category = res.querySelector('#m-ex-cat').value;
    const bodyweight = res.querySelector('#m-ex-bw').checked;
    const description = res.querySelector('#m-ex-desc').value.trim();
    if (name) {
      const state = await window.api.addExercise({ name, category, bodyweight, description });
      setState(state);
      renderExercisesCatalog();
      toast(`Exercise "${name}" added!`, 'success');
    }
  }
}

async function openEditExerciseModal(exerciseName) {
  const ex = exInfo(exerciseName);
  const res = await modal({
    title: `Edit Exercise: ${ex.name}`,
    html: `
      <label>Exercise Name (renaming cascades to all historical sets & goals)
        <input type="text" id="m-ex-name" value="${esc(ex.name)}" />
      </label>
      <label>Category
        <select id="m-ex-cat">
          ${['Chest', 'Back', 'Legs', 'Shoulders', 'Arms', 'Core', 'Other'].map((c) => `<option value="${c}" ${c === ex.category ? 'selected' : ''}>${c}</option>`).join('')}
        </select>
      </label>
      <label class="check" style="margin-top:10px;">
        <input type="checkbox" id="m-ex-bw" ${ex.bodyweight ? 'checked' : ''} /> Bodyweight exercise (uses total-load E1RM)
      </label>
      <label style="margin-top:10px;">Description / Kinetic Benefit
        <input type="text" id="m-ex-desc" value="${esc(ex.description || '')}" />
      </label>
    `,
    okText: 'Save Changes',
  });

  if (res) {
    const newName = res.querySelector('#m-ex-name').value.trim();
    const category = res.querySelector('#m-ex-cat').value;
    const bodyweight = res.querySelector('#m-ex-bw').checked;
    const description = res.querySelector('#m-ex-desc').value.trim();
    if (newName) {
      const state = await window.api.updateExercise({
        oldName: ex.name,
        updated: { name: newName, category, bodyweight, description },
      });
      setState(state);
      renderExercisesCatalog();
      toast(`Exercise updated!`, 'success');
    }
  }
}

// ---------------------------------------------------------------- SETTINGS & BODY WEIGHT
function renderSettings() {
  // Google Sheets info
  const cfg = S.storage || {};
  const statusEl = $('#sheets-status');
  if (cfg.kind === 'sheets') {
    statusEl.innerHTML = `<span class="ok">Connected to Google Sheets</span> • <code>${esc(cfg.title || cfg.spreadsheetId)}</code>`;
  } else {
    statusEl.innerHTML = `<span class="warn">Using offline local storage</span>`;
  }
  $('#cfg-sheet').value = cfg.spreadsheetId || '';
  $('#cfg-key').value = cfg.keyFile || '';

  const emailEl = $('#cfg-email');
  if (cfg.serviceEmail) {
    emailEl.innerHTML = `Share your Google Sheet with: <code>${esc(cfg.serviceEmail)}</code> (Editor)`;
    emailEl.classList.remove('hidden');
  } else {
    emailEl.classList.add('hidden');
  }

  // Body weight tracker
  const bwList = S.bodyWeights || [];
  const latestBw = BodyWeightUtils.latestWeight();
  $('#bw-current-value').textContent = latestBw !== null ? `${latestBw} kg` : '-- kg';
  $('#bw-input-date').value = today();

  const sortedBw = [...bwList].sort((a, b) => toSortableIso(b.date).localeCompare(toSortableIso(a.date)));
  const bwHost = $('#bw-history-list');
  bwHost.innerHTML = sortedBw.map((b) => `
    <div class="bw-entry-row">
      <span class="bw-entry-date">${shortDate(b.date)}</span>
      <span class="bw-entry-weight">${b.weightKg} kg</span>
      <button class="icon del-bw-btn" data-date="${esc(b.date)}" title="Delete reading">✕</button>
    </div>
  `).join('') || '<div class="muted small">No body weight readings logged yet.</div>';

  bwHost.querySelectorAll('.del-bw-btn').forEach((btn) => {
    btn.onclick = async () => {
      const state = await window.api.deleteBodyWeight(btn.dataset.date);
      setState(state);
      renderSettings();
    };
  });

  // Data summary
  $('#data-summary').textContent = `${S.workouts.length} workouts • ${S.sets.length} sets • ${S.exercises.length} exercises`;
}

function initSettingsModule() {
  $('#bw-log-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const dVal = $('#bw-input-date').value;
    const kgVal = $('#bw-input-kg').value;
    if (!dVal || !kgVal) return;
    const state = await window.api.addBodyWeight({
      date: fmtDdMonYyyy(dVal),
      weightKg: Number(kgVal),
      source: 'manual',
    });
    setState(state);
    $('#bw-input-kg').value = '';
    renderSettings();
    toast(`Body weight logged: ${kgVal} kg`, 'success');
  });

  $('#cfg-browse').addEventListener('click', async () => {
    const file = await window.api.pickKeyFile();
    if (file) $('#cfg-key').value = file;
  });

  $('#cfg-connect').addEventListener('click', async () => {
    const spreadsheetId = $('#cfg-sheet').value.trim();
    const keyFile = $('#cfg-key').value.trim();
    await busy(async () => {
      const state = await window.api.connect({ spreadsheetId, keyFile });
      setState(state);
      toast('Connected to Google Sheets!', 'success');
    });
  });

  $('#cfg-disconnect').addEventListener('click', async () => {
    await busy(async () => {
      const state = await window.api.disconnect();
      setState(state);
      toast('Disconnected. Using local storage.', 'warn');
    });
  });

  $('#copy-local').addEventListener('click', async () => {
    await busy(async () => {
      const res = await window.api.copyLocalToSheets();
      setState(res.state);
      toast(`Copied ${res.added} workouts to Google Sheets!`, 'success');
    });
  });

  $('#clear-data').addEventListener('click', async () => {
    if (confirm('Clear all stored workouts and sets? This will keep exercises, goals, and programs.')) {
      await busy(async () => {
        const state = await window.api.clearAllData();
        setState(state);
        toast('Stored workouts cleared');
      });
    }
  });

  // External link handler
  $$('[data-ext]').forEach((a) => {
    a.addEventListener('click', (e) => {
      e.preventDefault();
      window.api.openExternal(a.dataset.ext);
    });
  });
}

// ---------------------------------------------------------------- SUGGEST A WORKOUT ENGINE
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

const SUGGEST_SPLITS = {
  PUSH: {
    title: 'Push',
    desc: 'Chest, Quads, Shoulders & Triceps',
    slots: [
      { name: 'Primary Compound Press', categoryHint: 'Chest', defaultSets: 3, defaultReps: 5, candidates: ['Bench Press', 'Incline Bench Press', 'Close Grip Bench Press', 'Paused Close Grip Bench Press'] },
      { name: 'Push Leg: Quad Overload', categoryHint: 'Legs', defaultSets: 3, defaultReps: 8, candidates: ['Leg Press', 'Bulgarian Split Squat', 'Hack Squat', 'Squat', 'Front Squat', 'Leg Extension'] },
      { name: 'Vertical / Shoulder Press', categoryHint: 'Shoulders', defaultSets: 3, defaultReps: 6, candidates: ['Overhead Press', 'Seated Dumbbell Press', 'Shoulder Press', 'Incline Dumbbell Press'] },
      { name: 'Secondary Chest & Push Accessory', categoryHint: 'Chest', defaultSets: 3, defaultReps: 8, candidates: ['Dips', 'Incline Chest Press (Machine)', 'Incline Dumbbell Press', 'Dumbbell Fly', 'Weighted Push Up'] },
      { name: 'Delts & Triceps Finisher', categoryHint: 'Shoulders', defaultSets: 3, defaultReps: 10, candidates: ['Lateral Raise', 'Overhead Tricep Extension', 'Cable Tricep Pushdown', 'Skull Crusher'] },
    ],
  },
  PULL: {
    title: 'Pull',
    desc: 'Back, Rear Delts, Posterior Chain & Biceps',
    slots: [
      { name: 'Primary Vertical Pull', categoryHint: 'Back', defaultSets: 3, defaultReps: 6, candidates: ['Chin Ups', 'Pull Ups', 'Wide Grip Pull Ups', 'Lat Pulldown'] },
      { name: 'Heavy Horizontal Row', categoryHint: 'Back', defaultSets: 3, defaultReps: 6, candidates: ['Pendlay Row', 'Chest Supported Dumbbell Row', 'Barbell Row', 'Dumbbell Row', 'Cable Row'] },
      { name: 'Pull Leg: Hip Hinge & Posterior Chain', categoryHint: 'Legs', defaultSets: 3, defaultReps: 8, candidates: ['Good Morning', 'Hyperextension', 'Romanian Deadlift', 'Deadlift', 'Trap Bar Deadlift', 'Leg Curl'] },
      { name: 'Rear Delts & Upper Back Stretch', categoryHint: 'Back', defaultSets: 3, defaultReps: 12, candidates: ['Band Face Pull', 'Rear Delt Raise', 'Cable Rear Delt Fly', 'Dumbbell Pullover', 'Snatch Grip High Pull'] },
      { name: 'Biceps & Grip Finisher', categoryHint: 'Arms', defaultSets: 3, defaultReps: 10, candidates: ['Fat Gripz Hammer Curl', 'Barbell Curl', 'EZ Bar Preacher Curl', 'Reverse Grip Curl', 'Incline Fat Gripz Hammer Curl'] },
    ],
  },
  FB: {
    title: 'Full Body',
    desc: 'Legs, Push, Pull & Arms',
    slots: [
      { name: 'Primary Lower Compound', categoryHint: 'Legs', defaultSets: 3, defaultReps: 5, candidates: ['Squat', 'Leg Press', 'Deadlift', 'Romanian Deadlift', 'Bulgarian Split Squat'] },
      { name: 'Primary Upper Push', categoryHint: 'Chest', defaultSets: 3, defaultReps: 5, candidates: ['Bench Press', 'Overhead Press', 'Incline Bench Press', 'Dips'] },
      { name: 'Primary Upper Pull', categoryHint: 'Back', defaultSets: 3, defaultReps: 6, candidates: ['Pull Ups', 'Chin Ups', 'Pendlay Row', 'Lat Pulldown', 'Barbell Row'] },
      { name: 'Secondary Lower: Quad or Posterior Chain', categoryHint: 'Legs', defaultSets: 3, defaultReps: 8, candidates: ['Bulgarian Split Squat', 'Leg Press', 'Hyperextension', 'Good Morning', 'Leg Extension', 'Leg Curl', 'Romanian Deadlift'] },
      { name: 'Shoulders & Arms Overload', categoryHint: 'Shoulders', defaultSets: 3, defaultReps: 10, candidates: ['Lateral Raise', 'Band Face Pull', 'Rear Delt Raise', 'Fat Gripz Hammer Curl', 'Overhead Tricep Extension', 'Cable Tricep Pushdown', 'Barbell Curl'] },
    ],
  },
};

let activeSuggestSplit = 'PUSH';
let suggestExerciseCount = 5;
let suggestShuffleSeed = 0;
let suggestSlotOffsets = {};

function analyzeMuscleVolume() {
  const targets = { Chest: 10, Back: 12, Shoulders: 9, Legs: 10, Arms: 9, Core: 4 };
  const validSets = S.sets.filter((s) => (s.reps || 0) > 0);
  let latestMs = Date.now();
  for (const s of validSets) {
    const t = toDate(s.date).getTime();
    if (!Number.isNaN(t) && t > latestMs) latestMs = t;
  }
  const dayMs = 86400000;
  const counts7 = {};
  const counts14 = {};
  for (const s of validSets) {
    const cat = exInfo(s.exercise).category || 'Other';
    const t = toDate(s.date).getTime();
    if (!Number.isNaN(t) && t > 0) {
      const diffDays = Math.max(0, (latestMs - t) / dayMs);
      if (diffDays <= 7) counts7[cat] = (counts7[cat] || 0) + 1;
      if (diffDays <= 14) counts14[cat] = (counts14[cat] || 0) + 1;
    }
  }
  const list = Object.entries(targets).map(([category, targetWeeklySets]) => {
    const setsLast7Days = counts7[category] || 0;
    const setsLast14Days = counts14[category] || 0;
    const ratio = Math.min(1.5, setsLast7Days / targetWeeklySets);
    return {
      category,
      setsLast7Days,
      setsLast14Days,
      targetWeeklySets,
      deficitRatio: 1 - Math.min(1, ratio),
    };
  });
  const byCat = Object.fromEntries(list.map((m) => [m.category, m]));
  return { list, byCat, refMs: latestMs };
}

function buildSuggestedExerciseItem(slot, candidateOffset, alreadyPicked, volumeInfo) {
  const available = slot.candidates.filter((n) => !alreadyPicked.has(n.toLowerCase()));
  const pool = available.length ? available : slot.candidates;

  const ranked = [...pool].sort((a, b) => scoreCandidate(b) - scoreCandidate(a));

  function scoreCandidate(name) {
    const cat = exInfo(name).category || slot.categoryHint;
    const volDeficitBonus = (volumeInfo.byCat[cat]?.deficitRatio ?? 0.5) * 10;
    const benefitBonus = EXERCISE_BENEFITS[name] ? 4.0 : 0.0;
    const pts = sessionsFor(name);
    if (!pts.length) return 8.0 + volDeficitBonus + benefitBonus;

    const lastPt = pts[pts.length - 1];
    const peakE1rm = Math.max(...pts.map((p) => p.e1rm || 0));
    const currE1rm = lastPt?.e1rm || 0;
    const daysSince = lastPt ? Math.max(0, Math.min(60, (volumeInfo.refMs - lastPt.t) / 86400000)) : 30;
    const plateauGapBonus = peakE1rm > 0 && currE1rm < peakE1rm
      ? Math.min(15, ((peakE1rm - currE1rm) / peakE1rm) * 25)
      : 5.0;
    const recencyBonus = daysSince < 2 ? -20 : (daysSince >= 3 && daysSince <= 18 ? 12 : 6);
    return (Math.min(25, pts.length) * 0.8) + plateauGapBonus + recencyBonus + volDeficitBonus + benefitBonus;
  }

  const chosenName = ranked[candidateOffset % ranked.length];
  const cat = exInfo(chosenName).category || slot.categoryHint;
  const bw = isBW(chosenName);
  const sessions = sessionsFor(chosenName);
  const lastSession = sessions[sessions.length - 1] || null;
  const prevSession = sessions.length >= 2 ? sessions[sessions.length - 2] : null;
  const benefitText = EXERCISE_BENEFITS[chosenName] || exInfo(chosenName).description || '';

  if (!lastSession) {
    return {
      slotTitle: slot.name,
      exerciseName: chosenName,
      category: cat,
      isBodyweight: bw,
      lastSummary: 'No prior sessions logged',
      lastDate: 'First Time',
      targetSummary: `${slot.defaultSets} × ${slot.defaultReps} reps`,
      reasonTag: benefitText ? `✨ New Exercise Suggestion: ${benefitText}` : `🎯 New stimulus for ${cat} (${slot.defaultSets} × ${slot.defaultReps})`,
      prescribedSets: Array.from({ length: slot.defaultSets }, () => ({ weight: bw ? 0 : '', reps: slot.defaultReps, notes: '' })),
    };
  }

  const validLastSets = lastSession.sets.filter((s) => (s.reps || 0) > 0);
  const baseWeight = lastSession.top ?? 0;
  let baseReps = slot.defaultReps;
  if (lastSession.best && lastSession.best.reps) baseReps = lastSession.best.reps;
  else if (validLastSets.length) baseReps = validLastSets[0].reps || slot.defaultReps;

  const numSets = Math.max(3, Math.min(4, validLastSets.length || slot.defaultSets));
  const readyForJump = baseWeight > 0 && (
    (baseReps >= 8 && slot.defaultReps <= 6) ||
    (baseReps >= 12) ||
    (prevSession && (lastSession.e1rm || 0) > (prevSession.e1rm || 0) && baseReps >= slot.defaultReps + 1)
  );

  let targetWeight, targetReps, overloadNote;
  if (bw && baseWeight <= 0) {
    targetWeight = 0;
    targetReps = Math.max(slot.defaultReps, baseReps + 1);
    overloadNote = `Aim for +1 rep per set over last session (${baseReps} → ${targetReps} reps)`;
  } else if (readyForJump) {
    const inc = (cat === 'Arms' || (cat === 'Shoulders' && chosenName.toLowerCase().includes('raise'))) ? 1.25 : 2.5;
    targetWeight = Math.round((baseWeight + inc) * 2) / 2;
    targetReps = Math.max(slot.defaultReps, baseReps - 2);
    overloadNote = `Weight progression: +${fmt(targetWeight - baseWeight)}kg (${numSets} × ${targetReps} @ ${fmt(targetWeight)}kg)`;
  } else {
    targetWeight = baseWeight;
    targetReps = Math.min(15, baseReps + 1);
    overloadNote = `Rep progression: +1 rep at ${bw && targetWeight === 0 ? 'BW' : `${fmt(targetWeight)}kg`} (${numSets} × ${targetReps})`;
  }

  const peakE1rm = Math.max(...sessions.map((s) => s.e1rm || 0));
  const currE1rm = lastSession.e1rm || 0;
  const targetE1rm = BodyWeightUtils.calculateE1rm(chosenName, today(), targetWeight, targetReps);
  const volStat = volumeInfo.byCat[cat];
  const benefitSuffix = benefitText ? ` • Benefit: ${benefitText}` : '';

  let reasonTag;
  if (peakE1rm > 0 && currE1rm < peakE1rm * 0.94) {
    reasonTag = `🔧 Needs Improvement: Current E1RM (${fmt(currE1rm)}kg) is below your ${fmt(peakE1rm)}kg PR${benefitSuffix}`;
  } else if (targetE1rm > peakE1rm && peakE1rm > 0) {
    reasonTag = `🏆 PR Opportunity: Hitting ${bw && targetWeight === 0 ? 'BW' : `${fmt(targetWeight)}kg`} × ${targetReps} sets a new E1RM PR (${fmt(round(targetE1rm))}kg)!${benefitSuffix}`;
  } else if (volStat && volStat.setsLast7Days < volStat.targetWeeklySets / 2) {
    reasonTag = `📊 Weekly Volume Gap: ${cat} has only ${volStat.setsLast7Days}/${volStat.targetWeeklySets} sets in the last 7 days${benefitSuffix}`;
  } else {
    reasonTag = `📈 Progressive Overload: Build on ${shortDate(lastSession.date)} (${overloadNote})${benefitSuffix}`;
  }

  const targetSummary = bw && targetWeight === 0
    ? `${numSets} × ${targetReps} @ BW`
    : `${numSets} × ${targetReps} @ ${bw ? `BW+${fmt(targetWeight)}kg` : `${fmt(targetWeight)}kg`}`;

  return {
    slotTitle: slot.name,
    exerciseName: chosenName,
    category: cat,
    isBodyweight: bw,
    lastSummary: summarize(chosenName, validLastSets),
    lastDate: shortDate(lastSession.date),
    targetSummary,
    reasonTag,
    prescribedSets: Array.from({ length: numSets }, () => ({
      weight: targetWeight === 0 && !bw ? '' : targetWeight,
      reps: targetReps,
      notes: '',
    })),
  };
}

function renderSuggest() {
  const volumeInfo = analyzeMuscleVolume();
  const pushDeficit = ((volumeInfo.byCat.Chest?.deficitRatio || 0) + (volumeInfo.byCat.Shoulders?.deficitRatio || 0)) / 2;
  const pullDeficit = volumeInfo.byCat.Back?.deficitRatio || 0;
  const legsDeficit = volumeInfo.byCat.Legs?.deficitRatio || 0;
  let recommendedSplit = 'PUSH';
  if (legsDeficit >= pushDeficit && legsDeficit >= pullDeficit) recommendedSplit = 'FB';
  else if (pullDeficit >= pushDeficit) recommendedSplit = 'PULL';

  $('#suggest-sub').textContent = `Recommended today: ${SUGGEST_SPLITS[recommendedSplit].title} • Based on 7-day muscle volume & PR/plateau analysis.`;

  // 1. Controls bar: Split Selector + 4 / 5 Exercises Selector
  const ctrlHost = $('#suggest-controls');
  ctrlHost.innerHTML = `
    <div class="suggest-controls-bar">
      <div class="suggest-controls-left">
        <div class="seg" id="suggest-split-seg">
          ${['PUSH', 'PULL', 'FB'].map((k) => {
            const isRec = k === recommendedSplit;
            const active = activeSuggestSplit === k;
            return `<button class="${active ? 'active' : ''}" data-split="${k}">${SUGGEST_SPLITS[k].title}${isRec && !active ? ' •' : ''}</button>`;
          }).join('')}
        </div>
        <span class="muted small">${SUGGEST_SPLITS[activeSuggestSplit].desc}</span>
      </div>
      <div class="suggest-controls-right">
        <span class="muted small font-bold">Session Size:</span>
        <div class="seg" id="suggest-count-seg">
          <button class="${suggestExerciseCount === 4 ? 'active' : ''}" data-count="4">4 Lifts</button>
          <button class="${suggestExerciseCount === 5 ? 'active' : ''}" data-count="5">5 Lifts</button>
        </div>
      </div>
    </div>
  `;

  ctrlHost.querySelectorAll('#suggest-split-seg button[data-split]').forEach((b) => {
    b.onclick = () => {
      activeSuggestSplit = b.dataset.split;
      suggestSlotOffsets = {};
      renderSuggest();
    };
  });

  ctrlHost.querySelectorAll('#suggest-count-seg button[data-count]').forEach((b) => {
    b.onclick = () => {
      suggestExerciseCount = Number(b.dataset.count);
      suggestSlotOffsets = {};
      renderSuggest();
    };
  });

  // 2. 7-Day Muscle Volume Card (eliminates blank row)
  const volHost = $('#suggest-volume');
  volHost.innerHTML = `
    <div class="suggest-vol-head">
      <span class="muted small font-bold uppercase">7-Day Muscle Volume Targets</span>
      <span class="muted small">Working sets completed in last 7 days</span>
    </div>
    <div class="suggest-vol-grid">
      ${volumeInfo.list.map((m) => {
        const pct = Math.min(100, Math.round((m.setsLast7Days / m.targetWeeklySets) * 100));
        const colorClass = pct >= 85 ? 'vol-ok' : (pct >= 45 ? 'vol-mid' : 'vol-low');
        return `
          <div class="suggest-vol-item">
            <div class="suggest-vol-label">
              <span>${esc(m.category)}</span>
              <span class="muted">${m.setsLast7Days}/${m.targetWeeklySets}</span>
            </div>
            <div class="suggest-vol-bar">
              <div class="suggest-vol-fill ${colorClass}" style="width:${pct}%"></div>
            </div>
          </div>
        `;
      }).join('')}
    </div>
  `;

  // 3. Build Active Slots (4 or 5 lifts) with deduplication
  const splitDef = SUGGEST_SPLITS[activeSuggestSplit];
  const allSlots = splitDef.slots;
  const activeSlots = suggestExerciseCount === 4
    ? (activeSuggestSplit === 'FB'
        ? [allSlots[0], allSlots[1], allSlots[2], allSlots[3]]
        : [allSlots[0], allSlots[1], allSlots[2], allSlots[4]])
    : allSlots;

  const picked = new Set();
  const generatedExercises = activeSlots.map((slot, sIdx) => {
    const offset = (suggestSlotOffsets[sIdx] || 0) + suggestShuffleSeed;
    const item = buildSuggestedExerciseItem(slot, offset, picked, volumeInfo);
    picked.add(item.exerciseName.toLowerCase());
    return item;
  });

  $('#suggest-load').textContent = `▶ Start ${splitDef.title} Session (${generatedExercises.length} Lifts)`;

  const listHost = $('#suggest-list');
  listHost.innerHTML = generatedExercises.map((item, sIdx) => `
    <div class="card suggest-ex-card" style="margin-bottom:10px;">
      <div class="suggest-ex-top">
        <div>
          <span class="muted small uppercase font-bold">${sIdx + 1}. ${esc(item.slotTitle)}</span>
          <div class="suggest-ex-title">
            <span>${esc(item.exerciseName)}</span>
            <span class="tag">${esc(item.category)}</span>
            ${item.isBodyweight ? '<span class="tag" style="color:var(--pr)">BW</span>' : ''}
          </div>
        </div>
        <div class="suggest-ex-right">
          <div style="text-align:right;">
            <div style="font-size:14px;font-weight:700;color:var(--accent);">Target: ${esc(item.targetSummary)}</div>
            <div class="muted small">${item.lastDate === 'First Time' ? 'First time' : `Last (${esc(item.lastDate)}): ${esc(item.lastSummary)}`}</div>
          </div>
          <button class="ghost compact suggest-swap-btn" data-slot="${sIdx}" title="Swap exercise alternative">↻ Swap</button>
        </div>
      </div>
      <div class="desc muted small" style="margin-top:6px;">${esc(item.reasonTag)}</div>
    </div>
  `).join('');

  listHost.querySelectorAll('.suggest-swap-btn').forEach((btn) => {
    btn.onclick = () => {
      const sIdx = Number(btn.dataset.slot);
      suggestSlotOffsets[sIdx] = (suggestSlotOffsets[sIdx] || 0) + 1;
      renderSuggest();
    };
  });

  $('#suggest-shuffle').onclick = () => {
    suggestShuffleSeed += 1;
    suggestSlotOffsets = {};
    renderSuggest();
    toast('Shuffled exercise variations');
  };

  $('#suggest-load').onclick = () => {
    const plan = {
      title: `${splitDef.title} Workout`,
      notes: `Auto-designed ${splitDef.title} session (${splitDef.desc})`,
      exercises: generatedExercises.map((item) => ({
        exerciseName: item.exerciseName,
        category: item.category,
        prescribedSets: item.prescribedSets,
      })),
    };
    showView('log', plan);
  };
}

// ---------------------------------------------------------------- HISTORY & DETAIL
function renderHistory() {
  const q = ($('#history-search').value || '').toLowerCase().trim();
  const filtered = idx.workouts.filter((w) => {
    if (!q) return true;
    if (w.title.toLowerCase().includes(q) || (w.notes || '').toLowerCase().includes(q) || shortDate(w.date).toLowerCase().includes(q)) return true;
    const sets = idx.byW.get(w.id) || [];
    return sets.some((s) => s.exercise.toLowerCase().includes(q));
  });

  $('#history-sub').textContent = `${filtered.length} workout session${filtered.length === 1 ? '' : 's'}`;

  const host = $('#history-list');
  if (!filtered.length) {
    host.innerHTML = '<div class="empty">No workouts found.</div>';
    return;
  }

  host.innerHTML = filtered.map((w) => {
    const sets = idx.byW.get(w.id) || [];
    const grouped = groupByExercise(sets);
    const dt = toDate(w.date);

    const exLines = [...grouped.entries()].map(([name, sList]) => `
      <div class="w-ex">
        <span class="nm" data-jump-progress="${esc(name)}">${esc(name)}</span>
        <span class="st">${summarize(name, sList)}</span>
      </div>
    `).join('');

    return `
      <div class="card w-card" data-w-id="${w.id}">
        <div class="w-date">
          <div class="d">${dt.getDate()}</div>
          <div class="dw">${MONTHS_SHORT[dt.getMonth()]} ${dt.getFullYear()}</div>
        </div>
        <div class="w-body">
          <div class="w-title">${esc(w.title || 'Workout')}</div>
          ${w.notes ? `<div class="w-notes">${esc(w.notes)}</div>` : ''}
          <div class="w-exercises">${exLines}</div>
        </div>
        <div class="w-actions">
          <button class="ghost compact w-repeat-btn" data-id="${w.id}">Repeat</button>
          <button class="ghost compact w-inspect-btn" data-id="${w.id}">Details</button>
        </div>
      </div>
    `;
  }).join('');

  host.querySelectorAll('.w-card').forEach((c) => {
    c.onclick = (e) => {
      if (e.target.closest('button') || e.target.closest('[data-jump-progress]')) return;
      showView('workout-detail', c.dataset.wId);
    };
  });

  host.querySelectorAll('.w-inspect-btn').forEach((b) => {
    b.onclick = () => showView('workout-detail', b.dataset.id);
  });

  host.querySelectorAll('.w-repeat-btn').forEach((b) => {
    b.onclick = () => {
      const w = idx.workouts.find((x) => x.id === b.dataset.id);
      if (!w) return;
      const sets = idx.byW.get(w.id) || [];
      const grouped = groupByExercise(sets);
      const plan = {
        title: w.title,
        notes: `Repeat of ${shortDate(w.date)}`,
        exercises: [...grouped.entries()].map(([name, sList]) => ({
          exerciseName: name,
          category: exInfo(name).category,
          prescribedSets: sList.map((s) => ({
            weight: s.weight,
            reps: s.reps,
            notes: s.notes || '',
          })),
        })),
      };
      showView('log', plan);
    };
  });

  host.querySelectorAll('[data-jump-progress]').forEach((el) => {
    el.onclick = () => showView('progress', el.dataset.jumpProgress);
  });
}

function renderWorkoutDetail(workoutId) {
  const w = idx.workouts.find((x) => x.id === workoutId);
  if (!w) return showView('history');

  $('#workout-detail-heading').textContent = w.title || 'Workout Session';
  $('#workout-detail-sub').textContent = `${shortDate(w.date)} ${w.notes ? `• ${w.notes}` : ''}`;

  $('#workout-detail-back').onclick = () => showView('history');

  const sets = idx.byW.get(w.id) || [];
  const grouped = groupByExercise(sets);

  let totalVol = 0;
  for (const s of sets) {
    if (s.weight && s.reps) totalVol += s.weight * s.reps;
  }

  $('#workout-detail-stats').innerHTML = `
    <div class="stat"><div class="lbl">Date</div><div class="val" style="font-size:18px;">${shortDate(w.date)}</div></div>
    <div class="stat"><div class="lbl">Exercises</div><div class="val">${grouped.size}</div></div>
    <div class="stat"><div class="lbl">Total Sets</div><div class="val">${sets.length}</div></div>
    <div class="stat"><div class="lbl">Total Volume</div><div class="val">${Math.round(totalVol)} kg</div></div>
  `;

  $('#workout-detail-exercises').innerHTML = [...grouped.entries()].map(([name, sList]) => {
    const bw = isBW(name);
    const tableRows = sList.map((s) => {
      const eVal = (s.weight !== null && s.reps)
        ? Math.round(BodyWeightUtils.calculateE1rm(name, w.date, s.weight, s.reps) * 10) / 10
        : '–';
      return `
        <tr>
          <td>${s.setNo || 1}</td>
          <td>${bw ? (s.weight === 0 ? 'BW' : `BW+${s.weight}kg`) : `${s.weight ?? '–'}kg`}</td>
          <td>${s.reps ?? '–'}</td>
          <td>${eVal}k</td>
          <td>${esc(s.notes || '')}</td>
        </tr>
      `;
    }).join('');

    return `
      <div class="card detail-ex-card">
        <div class="detail-ex-head">
          <div class="detail-ex-title">
            <span class="ex-name" data-jump-progress="${esc(name)}">${esc(name)}</span>
            <span class="tag">${esc(exInfo(name).category)}</span>
            ${bw ? '<span class="tag" style="color:var(--pr)">BW</span>' : ''}
          </div>
          <div class="detail-summary-badge">${summarize(name, sList)}</div>
        </div>
        <table class="detail-sets-table">
          <thead><tr><th>Set</th><th>Weight</th><th>Reps</th><th>Est 1RM</th><th>Notes</th></tr></thead>
          <tbody>${tableRows}</tbody>
        </table>
      </div>
    `;
  }).join('');

  $('#workout-detail-repeat').onclick = () => {
    const plan = {
      title: w.title,
      notes: `Repeat of ${shortDate(w.date)}`,
      exercises: [...grouped.entries()].map(([name, sList]) => ({
        exerciseName: name,
        category: exInfo(name).category,
        prescribedSets: sList.map((s) => ({ weight: s.weight, reps: s.reps, notes: s.notes || '' })),
      })),
    };
    showView('log', plan);
  };

  $('#workout-detail-delete').onclick = async () => {
    if (confirm('Delete this workout session permanently?')) {
      const state = await window.api.deleteWorkout(w.id);
      setState(state);
      showView('history');
      toast('Workout deleted');
    }
  };

  $$('[data-jump-progress]').forEach((el) => {
    el.onclick = () => showView('progress', el.dataset.jumpProgress);
  });
}

// ---------------------------------------------------------------- PROGRESS & CHARTING
let progressChartInstance = null;

function renderProgressOverview() {
  $('#progress-heading').textContent = 'Progress';
  $('#progress-sub').textContent = 'Track strength curves and estimated 1RMs over time.';
  $('#progress-back').classList.add('hidden');
  $('#progress-detail').classList.add('hidden');
  $('#progress-overview').classList.remove('hidden');

  const exList = S.exercises || [];
  const withSessions = exList.map((e) => {
    const sess = sessionsFor(e.name);
    const best = sess.reduce((m, s) => (s.e1rm && (!m || s.e1rm > m.e1rm) ? s : m), null);
    return { ...e, sessionsCount: sess.length, bestE1rm: best?.e1rm || 0 };
  }).filter((e) => e.sessionsCount > 0)
    .sort((a, b) => b.sessionsCount - a.sessionsCount);

  $('#progress-overview').innerHTML = `
    <div class="grid">
      ${withSessions.map((e) => `
        <div class="card lift-card" data-ex="${esc(e.name)}">
          <div class="top">
            <span class="nm">${esc(e.name)}</span>
            <span class="tag">${esc(e.category)}</span>
          </div>
          <div class="big">${e.bestE1rm ? `${e.bestE1rm}<small>kg 1RM</small>` : `${e.sessionsCount}<small>sessions</small>`}</div>
          <div class="muted small" style="margin-top:4px;">${e.sessionsCount} session${e.sessionsCount === 1 ? '' : 's'} logged</div>
        </div>
      `).join('')}
    </div>
  `;

  $$('#progress-overview .lift-card').forEach((c) => {
    c.onclick = () => renderProgressDetail(c.dataset.ex);
  });
}

function renderProgressDetail(exerciseName) {
  $('#progress-overview').classList.add('hidden');
  $('#progress-detail').classList.remove('hidden');
  $('#progress-back').classList.remove('hidden');
  $('#progress-back').onclick = renderProgressOverview;

  const ex = exInfo(exerciseName);
  $('#progress-heading').textContent = ex.name;
  $('#progress-sub').textContent = `${ex.category} • ${ex.bodyweight ? 'Bodyweight (Total Load Calculated)' : 'Barbell / Dumbbell Lift'}`;

  const sess = sessionsFor(ex.name);
  const bestE1rm = sess.reduce((m, s) => Math.max(m, s.e1rm || 0), 0);
  const bestW = sess.reduce((m, s) => Math.max(m, s.top || 0), 0);
  const totalVol = sess.reduce((m, s) => m + (s.volume || 0), 0);

  $('#progress-stats').innerHTML = `
    <div class="stat"><div class="lbl">Best Est. 1RM</div><div class="val" style="color:var(--pr);">${bestE1rm ? `${bestE1rm} kg` : '–'}</div></div>
    <div class="stat"><div class="lbl">Top Weight</div><div class="val">${bestW ? `${bestW} kg` : '–'}</div></div>
    <div class="stat"><div class="lbl">Total Sessions</div><div class="val">${sess.length}</div></div>
    <div class="stat"><div class="lbl">Total Volume</div><div class="val">${Math.round(totalVol)} kg</div></div>
  `;

  renderProgressChart(ex.name, sess);

  // Sessions table
  const tableRows = [...sess].reverse().map((s) => `
    <tr>
      <td>${shortDate(s.date)}</td>
      <td>${summarize(ex.name, s.sets)}</td>
      <td>${s.e1rm ? `${s.e1rm} kg` : '–'}</td>
      <td>${s.top !== null ? `${s.top} kg` : '–'}</td>
      <td>${s.volume ? `${Math.round(s.volume)} kg` : '–'}</td>
    </tr>
  `).join('');

  $('#progress-table').innerHTML = `
    <thead><tr><th>Date</th><th>Sets Summary</th><th>Est 1RM</th><th>Top W</th><th>Volume</th></tr></thead>
    <tbody>${tableRows || '<tr><td colspan="5" class="muted">No sessions recorded yet.</td></tr>'}</tbody>
  `;
}

function renderProgressChart(exerciseName, sessions) {
  const canvas = $('#progress-chart');
  if (!canvas) return;
  if (progressChartInstance) progressChartInstance.destroy();

  const labels = sessions.map((s) => shortDate(s.date));
  const dataPoints = sessions.map((s) => s.e1rm || s.top || 0);

  progressChartInstance = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: 'Est. 1RM (kg)',
        data: dataPoints,
        borderColor: COLORS.accent,
        backgroundColor: 'rgba(255, 122, 69, 0.1)',
        borderWidth: 2.5,
        pointBackgroundColor: COLORS.accent,
        pointRadius: 4,
        tension: 0.25,
        fill: true,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: '#171a21',
          borderColor: '#2a2f3a',
          borderWidth: 1,
          titleColor: '#e8eaf0',
          bodyColor: '#ffd166',
          callbacks: {
            label: (ctx) => `Est. 1RM: ${ctx.parsed.y} kg`,
          },
        },
      },
      scales: {
        x: {
          grid: { color: 'rgba(42, 47, 58, 0.4)' },
          ticks: { color: '#8b93a7', maxTicksLimit: 8 },
        },
        y: {
          grid: { color: 'rgba(42, 47, 58, 0.4)' },
          ticks: { color: '#8b93a7' },
        },
      },
    },
  });
}

// ---------------------------------------------------------------- RENDER ALL ON STATE CHANGE
function renderAll() {
  // Update datalists
  $('#exercise-list').innerHTML = S.exercises.map((e) => `<option value="${esc(e.name)}">${esc(e.category || '')}</option>`).join('');

  // Update recent chips in logger
  const recent = [...new Set(idx.workouts.flatMap((w) => (idx.byW.get(w.id) || []).map((s) => s.exercise)))].slice(0, 10);
  $('#recent-chips').innerHTML = recent.map((n) => `<button class="chip add-chip" data-name="${esc(n)}">+ ${esc(n)}</button>`).join('');
  $$('#recent-chips .add-chip').forEach((c) => {
    c.onclick = () => addExerciseToLog(c.dataset.name);
  });

  // Storage badge
  const b = $('#storage-badge');
  if (S.storage.kind === 'sheets') {
    b.className = 'storage-badge sheets';
    b.textContent = `Google Sheets (${S.storage.title || 'Connected'})`;
  } else {
    b.className = 'storage-badge local';
    b.textContent = 'Local Offline JSON';
  }

  // Active view refresh
  const activeViewEl = $('.view.active');
  if (activeViewEl) {
    const vName = activeViewEl.id.replace('view-', '');
    if (vName === 'history') renderHistory();
    else if (vName === 'program') renderProgramView();
    else if (vName === 'suggest') renderSuggest();
    else if (vName === 'goals') renderGoals();
    else if (vName === 'exercises') renderExercisesCatalog();
    else if (vName === 'settings') renderSettings();
  }
}

// ---------------------------------------------------------------- INITIALIZATION
window.addEventListener('DOMContentLoaded', async () => {
  // Setup keyboard shortcuts
  window.addEventListener('keydown', (e) => {
    if (e.ctrlKey || e.metaKey) {
      if (e.key === '1') { e.preventDefault(); showView('log'); }
      else if (e.key === '2') { e.preventDefault(); showView('program'); }
      else if (e.key === '3') { e.preventDefault(); showView('suggest'); }
      else if (e.key === '4') { e.preventDefault(); showView('history'); }
      else if (e.key === '5') { e.preventDefault(); showView('progress'); }
      else if (e.key === '6') { e.preventDefault(); showView('goals'); }
      else if (e.key === '7') { e.preventDefault(); showView('exercises'); }
      else if (e.key === '8') { e.preventDefault(); showView('settings'); }
      else if (e.key === 's' || e.key === 'S') {
        if ($('#view-log').classList.contains('active')) {
          e.preventDefault();
          saveWorkoutFromLog();
        }
      }
    }
  });

  // Sidebar navigation buttons
  $$('.nav-btn').forEach((b) => {
    b.addEventListener('click', () => showView(b.dataset.view));
  });

  $('#history-search').addEventListener('input', renderHistory);
  $('#progress-input').addEventListener('input', (e) => {
    const val = e.target.value.trim();
    if (S.exercises.some((x) => x.name.toLowerCase() === val.toLowerCase())) {
      renderProgressDetail(val);
      e.target.value = '';
    }
  });

  initLog();
  initProgramModule();
  initGoalsModule();
  initExercisesModule();
  initSettingsModule();

  // Load initial backend state
  try {
    const state = await window.api.state();
    setState(state);
  } catch (err) {
    console.error('Initial state load failed:', err);
    toast(err.message, 'error');
  }
});
