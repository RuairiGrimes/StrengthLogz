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
  up: css('--up') || '#4cc38a',
  down: css('--down') || '#e5484d',
  pr: css('--pr') || '#ffd166',
  accent: css('--accent') || '#ff7a45',
  cutBlue: css('--blue') || '#60a5fa',
  maingainGreen: '#4cc38a',
  muted: css('--muted') || '#989da8',
  border: css('--border') || 'rgba(237, 237, 239, 0.08)',
};

const TrainingModeClient = {
  CUT_RETENTION_FLOOR_RATIO: 0.95,
  CUT_RETENTION_FLOOR_PCT: 95,
  MODES: {
    BULK: {
      id: 'BULK',
      label: 'Bulk',
      shortLabel: 'Bulk',
      cssClass: 'mode-bulk',
      color: '#ff7a45',
      tagline: 'Prioritize absolute strength gains, higher weekly volume & progressive overload',
      bullets: [
        'Prioritizes absolute strength & progressive overload',
        'Higher weekly muscle volume targets (~120%)',
        'Tracks healthy lean-bulk weight gain (+0.15 to +0.50 kg/wk)',
        'Suggests 3–4 working sets and brisk weight jumps',
      ],
    },
    MAINGAIN: {
      id: 'MAINGAIN',
      label: 'Maingain',
      shortLabel: 'Maingain',
      cssClass: 'mode-maingain',
      color: '#34d399',
      tagline: 'Maintain strength with steady micro-progression & balanced volume',
      bullets: [
        'Balanced strength gains & sustainable recovery',
        'Standard weekly volume targets (baseline 100%)',
        'Rewards steady body weight (±0.20 kg/wk) and clean progression',
        'Even scoring across volume, strength, cadence, and balance',
      ],
    },
    CUT: {
      id: 'CUT',
      label: 'Cut',
      shortLabel: 'Cut',
      cssClass: 'mode-cut',
      color: '#60a5fa',
      tagline: 'Protect strength retention, emphasize relative strength (× BW & DOTS) & manage fatigue',
      bullets: [
        'Focuses on strength retention (≥ 95% E1RM) & Relative Strength (× BW)',
        'Recovery-friendly volume (~75%) to protect muscle without burnout',
        'Replaces harsh red loss alerts on expected < 5% dips with calm Retention cues',
        'Celebrates Relative Strength PRs & DOTS gains while weight trends down',
      ],
    },
  },
  normalize(raw) {
    const clean = String(raw || '').trim().toUpperCase();
    return Object.prototype.hasOwnProperty.call(this.MODES, clean) ? clean : 'MAINGAIN';
  },
  meta(raw) {
    return this.MODES[this.normalize(raw)];
  },
  weeklyMuscleTargets(raw) {
    const m = this.normalize(raw);
    if (m === 'BULK') return { Chest: 12, Back: 14, Legs: 12, Shoulders: 10, Arms: 10, Core: 4 };
    if (m === 'CUT') return { Chest: 7, Back: 8, Legs: 7, Shoulders: 6, Arms: 6, Core: 4 };
    return { Chest: 10, Back: 12, Legs: 10, Shoulders: 9, Arms: 9, Core: 4 };
  },
  isExpectedCutDip(raw, currVal, prevVal, currRel = 0, prevRel = 0) {
    if (this.normalize(raw) !== 'CUT') return false;
    if (!(prevVal > 0 && currVal > 0 && currVal < prevVal)) return false;
    return currVal >= prevVal * this.CUT_RETENTION_FLOOR_RATIO || (prevRel > 0 && currRel >= prevRel - 0.01);
  },
  classifyBodyWeightPace(raw, weeklyRateKg) {
    const m = this.normalize(raw);
    const r = Number(weeklyRateKg) || 0;
    if (m === 'BULK') {
      if (r >= 0.15 && r <= 0.50) return { status: 'On-pace lean bulk', isOnTarget: true, detail: 'Controlled surplus (+0.15 to +0.50 kg/wk) ideal for strength & growth.' };
      if (r > 0.75) return { status: 'Fast surplus — watch fat gain', isOnTarget: false, detail: 'Gaining faster than +0.75 kg/wk; consider trimming surplus slightly.' };
      if (r > 0.50) return { status: 'Brisk surplus', isOnTarget: false, detail: 'Slightly above lean bulk pace (+0.15 to +0.50 kg/wk).' };
      if (r < -0.10) return { status: 'Weight dropping in Bulk mode', isOnTarget: false, detail: 'Body weight is trending down; fuel recovery to support overload.' };
      return { status: 'Slow/maintenance surplus', isOnTarget: true, detail: 'Near maintenance; push intake slightly if E1RMs stall.' };
    }
    if (m === 'CUT') {
      if (r >= -0.85 && r <= -0.25) return { status: 'On-pace sustainable cut', isOnTarget: true, detail: 'Steady fat-loss pace (-0.25 to -0.85 kg/wk) ideal for preserving muscle & strength.' };
      if (r < -1.10) return { status: 'Aggressive deficit — watch recovery', isOnTarget: false, detail: 'Losing faster than -1.1 kg/wk; keep intensity high and manage fatigue.' };
      if (r < -0.85) return { status: 'Brisk deficit', isOnTarget: true, detail: 'Noticeable deficit (-0.85 to -1.10 kg/wk) — prioritize retention & sleep.' };
      if (r > 0.15) return { status: 'Weight trending up in Cut mode', isOnTarget: false, detail: 'Scale weight rose slightly over the window; check weekly average trend.' };
      return { status: 'Mild cut / recomposition pace', isOnTarget: true, detail: 'Slow loss or steady weight — supportive for strength retention and relative ×BW.' };
    }
    if (r >= -0.20 && r <= 0.20) return { status: 'Steady maingain weight', isOnTarget: true, detail: 'Holding within ±0.20 kg/wk — ideal for steady recomp and micro-gains.' };
    if (r > 0.20) return { status: 'Slight surplus drift', isOnTarget: false, detail: 'Trending above +0.20 kg/wk; tighten intake or switch to Bulk.' };
    return { status: 'Slight deficit drift', isOnTarget: false, detail: 'Trending below -0.20 kg/wk; add recovery calories or switch to Cut.' };
  },
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
  savedSummaries: [],
  trainingMode: 'MAINGAIN',
  trainingModeUpdatedAt: 0,
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
    savedSummaries: st.savedSummaries || [],
    trainingMode: TrainingModeClient.normalize(st.trainingMode),
    trainingModeUpdatedAt: Number(st.trainingModeUpdatedAt) || 0,
    activeProgramId: st.activeProgramId || (st.programs && st.programs[0] ? st.programs[0].id : null),
  };
  buildIndex();
  renderAll();
}

async function switchTrainingMode(mode) {
  const next = TrainingModeClient.normalize(mode);
  if (next === TrainingModeClient.normalize(S.trainingMode)) return;
  await busy(async () => {
    const state = await window.api.setTrainingMode(next);
    setState(state);
    toast(`Switched to ${TrainingModeClient.meta(next).label} mode`, 'success');
  });
}

function openModePickerModal() {
  const curr = TrainingModeClient.normalize(S.trainingMode);
  const { body, close } = openCustomModal({
    title: 'Select Training Phase',
    html: `
      <p class="muted small" style="margin-top:0;">Goals, workout suggestions, progress charts, PR badges, and 14-day summaries adapt immediately.</p>
      <div id="m-mode-options" style="display:flex;flex-direction:column;gap:8px;">
        ${['BULK', 'MAINGAIN', 'CUT'].map((k) => {
          const meta = TrainingModeClient.MODES[k];
          const active = curr === k;
          return `
            <button type="button" class="mode-seg-btn ${active ? 'active' : ''}" data-mode="${k}" style="align-items:flex-start;text-align:left;padding:12px 14px;">
              <span style="font-size:14px;font-weight:700;">${esc(meta.label)} Mode ${active ? '✓' : ''}</span>
              <span class="sub" style="font-size:12px;">${esc(meta.tagline)}</span>
            </button>
          `;
        }).join('')}
      </div>
    `,
    cancelText: 'Close',
  });
  body.querySelectorAll('[data-mode]').forEach((btn) => {
    btn.addEventListener('click', async () => {
      const m = btn.dataset.mode;
      close();
      await switchTrainingMode(m);
    });
  });
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
    const hasBwData = bw || (S.bodyWeights || []).length > 0;
    const bwKg = hasBwData ? BodyWeightUtils.weightOn(sess.date) : null;
    const e1rmVal = e1rm === null ? null : round(e1rm);
    const rel = (e1rmVal !== null && bwKg > 0) ? Math.round((e1rmVal / bwKg) * 100) / 100 : null;
    return {
      ...sess,
      t: toDate(sess.date).getTime(),
      title: w ? w.title : '',
      e1rm: e1rmVal,
      bwKg,
      rel,
      best,
      top,
      volume: bw ? (totalReps || null) : (volume || null),
      maxReps,
      totalReps,
    };
  });
  const sorted = out.sort((a, b) => a.t - b.t);
  let bestE = 0, bestW = 0, bestRel = 0;
  for (const s of sorted) {
    s.isE1rmPr = s.e1rm !== null && s.e1rm > bestE + 0.05;
    s.isWeightPr = s.top !== null && s.top > bestW;
    s.isRelPr = s.rel !== null && bestRel > 0 && s.rel > bestRel + 0.004;
    if (s.e1rm !== null && s.e1rm > bestE) bestE = s.e1rm;
    if (s.top !== null && s.top > bestW) bestW = s.top;
    if (s.rel !== null && s.rel > bestRel) bestRel = s.rel;
  }
  return sorted;
}

function syncProgressRelButton(exerciseName) {
  const btn = $('#progress-open-rel');
  if (!btn) return;
  const pts = exerciseName ? buildRelativeStrengthDatapoints(exerciseName) : [];
  if (!pts.length) {
    btn.classList.add('hidden');
    return;
  }
  btn.classList.remove('hidden');
  const latest = pts[pts.length - 1];
  btn.innerHTML = `Relative strength <span class="rel-badge">${latest.rel.toFixed(2)}×</span>`;
}

function metricsFor(name) {
  const bw = isBW(name);
  syncProgressRelButton(name);
  return {
    e1rm: { label: bw ? 'Est. total 1RM' : 'Est. 1RM', unit: 'kg', get: (s) => s.e1rm },
    top: { label: bw ? 'Top added weight' : 'Top weight', unit: 'kg', get: (s) => s.top },
    volume: { label: bw ? 'Total reps' : 'Volume', unit: bw ? 'reps' : 'kg', get: (s) => s.volume },
    reps: { label: 'Max reps', unit: 'reps', get: (s) => s.maxReps },
  };
}

function defaultMetric(name, sessions) {
  const m = metricsFor(name);
  const order = ['e1rm', 'top', 'reps', 'volume'];
  return order.find((k) => m[k] && sessions.some((s) => m[k].get(s) !== null)) || 'e1rm';
}

const REL_MONTH_MAP = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

function relToIso(d) {
  if (!d) return '';
  const s = String(d).trim();
  if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
  const m = /^(\d{1,2})-([A-Za-z]{3})-(\d{4})$/.exec(s);
  if (m) {
    const mon = REL_MONTH_MAP[m[2].toLowerCase()];
    if (mon) return `${m[3]}-${mon}-${m[1].padStart(2, '0')}`;
  }
  const parsed = new Date(s);
  return Number.isNaN(parsed.getTime()) ? s : parsed.toISOString().slice(0, 10);
}

function relResolveBwEntry(dateStr, entries) {
  const sorted = (entries || [])
    .filter((e) => Number(e.weightKg) > 0)
    .map((e) => ({ ...e, iso: relToIso(e.date), kg: Number(e.weightKg) }))
    .filter((e) => e.iso)
    .sort((a, b) => a.iso.localeCompare(b.iso));
  if (!sorted.length) return null;
  const targetIso = relToIso(dateStr);
  let prior = null;
  for (const e of sorted) {
    if (e.iso <= targetIso) prior = e;
    else break;
  }
  if (prior) return { entry: prior, hasExactOrPrior: true };
  return { entry: sorted[0], hasExactOrPrior: false };
}

function buildRelativeStrengthDatapoints(exerciseName) {
  if (!exerciseName) return [];
  const bw = isBW(exerciseName);
  const bwEntries = S.bodyWeights || [];
  if (!bw && !bwEntries.length) return [];

  const sessions = sessionsFor(exerciseName);
  const points = [];
  let peakRel = 0;
  let firstRel = null;

  for (const s of sessions) {
    const validSets = (s.sets || []).filter((st) => Number(st.reps) > 0);
    if (!validSets.length || !(s.e1rm > 0)) continue;

    const resolved = relResolveBwEntry(s.date, bwEntries);
    const bwKg = resolved ? resolved.entry.kg : BodyWeightUtils.weightOn(s.date);
    if (!(bwKg > 0)) continue;

    let bestSet = validSets[0];
    let bestSetE1rm = -1;
    for (const st of validSets) {
      const est = BodyWeightUtils.calculateE1rm(exerciseName, s.date, st.weight, st.reps);
      if (est > bestSetE1rm) {
        bestSetE1rm = est;
        bestSet = st;
      }
    }
    const wNum = Number(bestSet.weight) || 0;
    const topSetSummary = bw
      ? (wNum > 0 ? `BW+${fmt(wNum)}kg × ${bestSet.reps}` : `BW × ${bestSet.reps}`)
      : `${fmt(wNum)}kg × ${bestSet.reps}`;

    const e1rm = round(s.e1rm, 0.1);
    const rel = Math.round((e1rm / bwKg) * 1000) / 1000;
    const prev = points.length ? points[points.length - 1] : null;
    if (firstRel === null) firstRel = rel;

    const isRelPr = rel > peakRel + 0.004;
    if (rel > peakRel) peakRel = rel;

    const delta = prev ? Math.round((rel - prev.rel) * 1000) / 1000 : null;
    const bwDelta = prev ? Math.round((bwKg - prev.bwKg) * 10) / 10 : null;
    const pctVsFirst = firstRel > 0 ? Math.round(((rel - firstRel) / firstRel) * 1000) / 10 : 0;
    const isBodyweightShift = !!(prev && bwDelta !== null && Math.abs(bwDelta) >= 0.05 && Math.abs(e1rm - prev.e1rm) < 0.05);

    points.push({
      workoutId: s.workoutId,
      date: s.date,
      iso: relToIso(s.date),
      t: s.t,
      title: s.title || '',
      e1rm,
      topSetSummary,
      bwKg: Math.round(bwKg * 10) / 10,
      bwDate: resolved ? resolved.entry.date : null,
      hasExactOrPriorBw: resolved ? resolved.hasExactOrPrior : false,
      rel,
      delta,
      bwDelta,
      pctVsFirst,
      isRelPr,
      isBodyweightShift,
    });
  }
  return points;
}

let relChartInstance = null;

function openRelativeStrengthModal(exerciseName) {
  const allPoints = buildRelativeStrengthDatapoints(exerciseName);
  const keyOf = (p) => String(p.workoutId || p.iso || '');
  const state = {
    rangeDays: 0,
    prsOnly: false,
    showBwLine: true,
    selectedKey: allPoints.length ? keyOf(allPoints[allPoints.length - 1]) : null,
  };

  const { body } = openCustomModal({
    title: `${exerciseName} — Relative strength`,
    large: true,
    cancelText: 'Close',
    onClose: () => {
      if (relChartInstance) {
        relChartInstance.destroy();
        relChartInstance = null;
      }
    },
    html: '<div id="rel-modal-root"></div>',
  });

  const root = body.querySelector('#rel-modal-root');

  const getFiltered = () => {
    let list = allPoints;
    if (state.rangeDays > 0 && allPoints.length) {
      const todayDay = epochDay(today());
      list = allPoints.filter((p) => todayDay - epochDay(p.iso) <= state.rangeDays);
    }
    if (state.prsOnly) list = list.filter((p) => p.isRelPr);
    return list;
  };

  const bindToolbar = () => {
    root.querySelectorAll('[data-rel-range]').forEach((btn) => {
      btn.onclick = () => {
        state.rangeDays = Number(btn.dataset.relRange);
        render();
      };
    });
    const prBtn = root.querySelector('#rel-toggle-pr');
    if (prBtn) {
      prBtn.onclick = () => {
        state.prsOnly = !state.prsOnly;
        render();
      };
    }
    const bwBtn = root.querySelector('#rel-toggle-bw');
    if (bwBtn) {
      bwBtn.onclick = () => {
        state.showBwLine = !state.showBwLine;
        render();
      };
    }
  };

  const render = () => {
    if (relChartInstance) {
      relChartInstance.destroy();
      relChartInstance = null;
    }

    if (!allPoints.length) {
      root.innerHTML = '<div class="empty">Log a body weight reading and at least one working set to plot relative strength (× BW).</div>';
      return;
    }

    const rangeButtons = [
      { d: 90, lbl: '3M' },
      { d: 182, lbl: '6M' },
      { d: 365, lbl: '1Y' },
      { d: 0, lbl: 'All' },
    ].map((r) => `<button type="button" class="${state.rangeDays === r.d ? 'active' : ''}" data-rel-range="${r.d}">${r.lbl}</button>`).join('');

    const toolbarHtml = `
      <div class="rel-modal-toolbar">
        <div class="rel-toggle-row">
          <button type="button" id="rel-toggle-pr" class="rel-pill-btn pr ${state.prsOnly ? 'active' : ''}">PRs only</button>
          <button type="button" id="rel-toggle-bw" class="rel-pill-btn ${state.showBwLine ? 'active' : ''}">BW line</button>
        </div>
        <div class="seg">${rangeButtons}</div>
      </div>
    `;

    const visible = getFiltered();
    if (!visible.length) {
      root.innerHTML = `
        ${toolbarHtml}
        <div class="empty">No sessions match this filter.</div>
      `;
      bindToolbar();
      return;
    }

    const chartPts = visible;
    const peak = chartPts.reduce((best, p) => (!best || p.rel > best.rel ? p : best), null);
    const first = chartPts[0];
    const latest = chartPts[chartPts.length - 1];
    const gain = latest.rel - first.rel;
    const gainPct = first.rel > 0 ? (gain / first.rel) * 100 : 0;

    if (!chartPts.some((p) => keyOf(p) === state.selectedKey)) {
      state.selectedKey = keyOf(latest);
    }
    const selectedIdx = chartPts.findIndex((p) => keyOf(p) === state.selectedKey);
    const selected = selectedIdx >= 0 ? chartPts[selectedIdx] : latest;
    const selectedKey = keyOf(selected);

    const kpiHtml = `
      <div class="rel-modal-kpis">
        <div class="stat">
          <div class="lbl">Peak ratio</div>
          <div class="val" style="color:var(--pr);">${peak.rel.toFixed(2)}×</div>
          <div class="sub">${shortDate(peak.date)} • ${fmt(peak.e1rm)}kg @ ${peak.bwKg.toFixed(1)}kg</div>
        </div>
        <div class="stat">
          <div class="lbl">Latest</div>
          <div class="val" style="color:var(--blue);">${latest.rel.toFixed(2)}×</div>
          <div class="sub">${latest.delta !== null ? `${latest.delta >= 0 ? '+' : ''}${latest.delta.toFixed(2)}× vs prev` : shortDate(latest.date)}</div>
        </div>
        <div class="stat">
          <div class="lbl">Range gain</div>
          <div class="val" style="color:${gain >= 0 ? 'var(--up)' : 'var(--down)'};">${gain >= 0 ? '+' : ''}${gain.toFixed(2)}×</div>
          <div class="sub">${gainPct >= 0 ? '+' : ''}${gainPct.toFixed(1)}% since ${shortDate(first.date)}</div>
        </div>
      </div>
    `;

    const bwSourceNote = selected.bwDate
      ? (selected.bwDate === selected.date || relToIso(selected.bwDate) === selected.iso
        ? `BW logged ${shortDate(selected.bwDate)}`
        : `BW carried from ${shortDate(selected.bwDate)}`)
      : `Default ${selected.bwKg.toFixed(1)}kg BW`;

    const selectedBanner = `
      <div class="rel-detail-note">
        <div>
          <b>${shortDate(selected.date)}</b>
          ${selected.isRelPr ? '<span class="rel-badge" style="margin-left:6px;background:rgba(255,209,102,0.16);color:var(--pr);">Rel PR</span>' : ''}
          <span class="muted small" style="margin-left:8px;">${esc(selected.topSetSummary)} • E1RM ${fmt(selected.e1rm)}kg ÷ ${selected.bwKg.toFixed(1)}kg = <b>${selected.rel.toFixed(2)}× BW</b></span>
        </div>
        <div class="muted small">${esc(bwSourceNote)}${selected.isBodyweightShift ? ' • Driven by BW change' : ''}</div>
      </div>
    `;

    const rowsHtml = [...chartPts].reverse().map((p) => {
      const pKey = keyOf(p);
      const isSel = pKey === selectedKey;
      const deltaStr = p.delta === null ? '—' : `${p.delta > 0 ? '+' : ''}${p.delta.toFixed(2)}×`;
      const deltaColor = p.delta === null ? 'var(--muted)' : (p.delta > 0.004 ? 'var(--up)' : (p.delta < -0.004 ? 'var(--down)' : 'var(--muted)'));
      const bwNoteTitle = p.bwDate ? `BW source: ${shortDate(p.bwDate)}` : 'Default fallback BW';
      return `
        <tr class="${isSel ? 'rel-row-selected' : ''}" data-rel-key="${esc(pKey)}">
          <td>${shortDate(p.date)}${p.isRelPr ? '<span class="rel-pr-dot" title="Relative strength PR"></span>' : ''}</td>
          <td>${esc(p.topSetSummary)}</td>
          <td class="num">${fmt(p.e1rm)} kg</td>
          <td class="num" title="${esc(bwNoteTitle)}">${p.bwKg.toFixed(1)} kg${p.bwDate && relToIso(p.bwDate) !== p.iso ? '*' : ''}</td>
          <td class="num" style="font-weight:700;color:${p.isRelPr ? 'var(--pr)' : 'var(--text)'};">${p.rel.toFixed(2)}×</td>
          <td class="num" style="color:${deltaColor};font-weight:600;">${deltaStr}</td>
        </tr>
      `;
    }).join('');

    root.innerHTML = `
      ${kpiHtml}
      ${toolbarHtml}
      <div class="rel-chart-wrap"><canvas id="rel-modal-canvas"></canvas></div>
      ${selectedBanner}
      <div class="rel-table-wrap">
        <table class="rel-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Top set</th>
              <th class="num">E1RM</th>
              <th class="num">BW</th>
              <th class="num">× BW</th>
              <th class="num">Delta</th>
            </tr>
          </thead>
          <tbody>${rowsHtml}</tbody>
        </table>
      </div>
    `;

    const canvas = root.querySelector('#rel-modal-canvas');
    const relValues = chartPts.map((p) => p.rel);
    const minRel = Math.min(...relValues);
    const maxRel = Math.max(...relValues);
    const padRel = Math.max(0.08, (maxRel - minRel) * 0.22);

    const datasets = [{
      label: 'Relative strength (× BW)',
      data: relValues,
      yAxisID: 'y',
      borderColor: COLORS.cutBlue,
      backgroundColor: 'rgba(96, 165, 250, 0.10)',
      borderWidth: 2.2,
      tension: 0.22,
      fill: true,
      pointRadius: chartPts.map((p) => (keyOf(p) === selectedKey ? 6 : (p.isRelPr ? 5 : 3.2))),
      pointHoverRadius: 7,
      pointBackgroundColor: chartPts.map((p) => (keyOf(p) === selectedKey ? COLORS.accent : (p.isRelPr ? COLORS.pr : COLORS.cutBlue))),
      pointBorderColor: chartPts.map((p) => (keyOf(p) === selectedKey ? '#ffffff' : (p.isRelPr ? COLORS.pr : COLORS.cutBlue))),
      pointBorderWidth: chartPts.map((p) => (keyOf(p) === selectedKey ? 2 : 1)),
    }];

    if (state.showBwLine) {
      datasets.push({
        label: 'Body weight (kg)',
        data: chartPts.map((p) => p.bwKg),
        yAxisID: 'y1',
        borderColor: 'rgba(255, 209, 102, 0.55)',
        borderDash: [4, 4],
        borderWidth: 1.5,
        pointRadius: 0,
        tension: 0.2,
        fill: false,
      });
    }

    relChartInstance = new Chart(canvas, {
      type: 'line',
      data: {
        labels: chartPts.map((p) => shortDate(p.date)),
        datasets,
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: false,
        onClick: (_evt, elements) => {
          if (elements && elements.length) {
            const pt = chartPts[elements[0].index];
            const k = pt ? keyOf(pt) : null;
            if (k && k !== state.selectedKey) {
              state.selectedKey = k;
              render();
            }
          }
        },
        plugins: {
          legend: {
            display: state.showBwLine,
            labels: { color: COLORS.muted, boxWidth: 12, font: { size: 11 } },
          },
          tooltip: {
            backgroundColor: '#14161a',
            borderColor: 'rgba(237, 237, 239, 0.14)',
            borderWidth: 1,
            titleColor: '#ededef',
            bodyColor: '#ededef',
            callbacks: {
              label: (ctx) => {
                const p = chartPts[ctx.dataIndex];
                if (ctx.dataset.yAxisID === 'y1') return `Body weight: ${p.bwKg.toFixed(1)} kg`;
                return `${p.rel.toFixed(2)}× BW (${fmt(p.e1rm)} kg ÷ ${p.bwKg.toFixed(1)} kg)`;
              },
            },
          },
        },
        scales: {
          x: {
            ticks: { color: COLORS.muted, maxTicksLimit: 8, font: { size: 11 } },
            grid: { color: 'rgba(237, 237, 239, 0.05)' },
          },
          y: {
            position: 'left',
            suggestedMin: Math.max(0, minRel - padRel),
            suggestedMax: maxRel + padRel,
            ticks: { color: COLORS.cutBlue, callback: (v) => `${Number(v).toFixed(2)}×`, font: { size: 11 } },
            grid: { color: 'rgba(237, 237, 239, 0.06)' },
          },
          y1: {
            display: state.showBwLine,
            position: 'right',
            ticks: { color: COLORS.muted, callback: (v) => `${Number(v).toFixed(1)}kg`, font: { size: 11 } },
            grid: { drawOnChartArea: false },
          },
        },
      },
    });

    bindToolbar();
    root.querySelectorAll('tr[data-rel-key]').forEach((tr) => {
      tr.onclick = () => {
        state.selectedKey = tr.dataset.relKey;
        render();
      };
    });
  };

  render();
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

    const onDocKey = (e) => { if (e.key === 'Escape') close(null); };
    const close = (val) => {
      m.classList.add('hidden');
      box.classList.remove('wide', 'large');
      ok.onclick = null;
      cancel.onclick = null;
      $('#modal-x').onclick = null;
      m.onclick = null;
      m.onkeydown = null;
      document.removeEventListener('keydown', onDocKey);
      resolve(val);
    };

    ok.onclick = () => close($('#modal-body'));
    cancel.onclick = () => close(null);
    $('#modal-x').onclick = () => close(null);
    m.onclick = (e) => { if (e.target === m) close(null); };
    document.addEventListener('keydown', onDocKey);
    m.onkeydown = (e) => {
      if (e.key === 'Enter' && e.target.tagName !== 'TEXTAREA') {
        e.preventDefault();
        close($('#modal-body'));
      }
    };
  });
}

function openCustomModal({ title, html, cancelText = 'Close', wide = false, large = false, onClose = null }) {
  const m = $('#modal');
  const box = $('.modal-box', m);
  $('#modal-title').textContent = title;
  $('#modal-body').innerHTML = html;

  box.classList.toggle('wide', !!wide);
  box.classList.toggle('large', !!large);

  const ok = $('#modal-ok');
  ok.classList.add('hidden');
  ok.onclick = null;

  const cancel = $('#modal-cancel');
  cancel.textContent = cancelText;

  m.classList.remove('hidden');

  const onDocKey = (e) => { if (e.key === 'Escape') close(); };
  const close = () => {
    m.classList.add('hidden');
    box.classList.remove('wide', 'large');
    cancel.onclick = null;
    $('#modal-x').onclick = null;
    m.onclick = null;
    m.onkeydown = null;
    document.removeEventListener('keydown', onDocKey);
    if (typeof onClose === 'function') onClose();
  };

  cancel.onclick = close;
  $('#modal-x').onclick = close;
  m.onclick = (e) => { if (e.target === m) close(); };
  document.addEventListener('keydown', onDocKey);

  return { m, body: $('#modal-body'), close };
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
  } else if (name === 'bodyweight') {
    renderBodyWeightDashboard();
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

function computeSetRadarLabel(exName, workoutDate, w, r, histPeakE1rm, histPeakRel) {
  if (r === 0 || (r !== null && r <= 0)) {
    return '<span class="err" style="font-size:10px;margin-left:4px;">FAILED (0r)</span>';
  }
  if (!r || w === null || w === undefined) return '';
  const e1rm = Math.round(BodyWeightUtils.calculateE1rm(exName, workoutDate, w, r) * 10) / 10;
  if (!(e1rm > 0)) return '';
  const mode = TrainingModeClient.normalize(S.trainingMode);
  const hasBw = (S.bodyWeights || []).length > 0;
  const bwKg = hasBw ? BodyWeightUtils.weightOn(workoutDate) : 0;
  const rel = bwKg > 0 ? Math.round((e1rm / bwKg) * 100) / 100 : 0;

  if (histPeakE1rm > 0 && e1rm > histPeakE1rm + 0.1) {
    return '<span class="badge-rel-pr" style="margin-left:4px;">PR</span>';
  }
  if (hasBw && histPeakRel > 0 && rel > histPeakRel + 0.004) {
    return `<span class="badge-rel-pr" style="margin-left:4px;">Rel PR (${rel.toFixed(2)}×)</span>`;
  }
  if (mode === 'CUT' && histPeakE1rm > 0 && e1rm >= histPeakE1rm * TrainingModeClient.CUT_RETENTION_FLOOR_RATIO) {
    const pct = Math.round((e1rm / histPeakE1rm) * 100);
    return `<span class="delta-retained" style="font-size:10px;margin-left:4px;">${pct}% held</span>`;
  }
  return '';
}

function renderLogExercises() {
  const host = $('#log-exercises');
  if (!logExercises.length) {
    host.innerHTML = '<div class="empty">No exercises added yet. Pick one below to start logging.</div>';
    return;
  }

  const workoutDate = $('#log-date').value || today();
  const hasBw = (S.bodyWeights || []).length > 0;
  const bwTodayKg = hasBw ? BodyWeightUtils.weightOn(workoutDate) : 0;

  host.innerHTML = logExercises.map((ex, exIdx) => {
    const info = exInfo(ex.name);
    const bw = isBW(ex.name);
    const activeGoal = getActiveGoalForExercise(ex.name);
    const prevSessions = sessionsFor(ex.name);
    const last = prevSessions[prevSessions.length - 1];
    const histPeakE1rm = prevSessions.reduce((m, s) => Math.max(m, s.e1rm || 0), 0);
    const histPeakRel = prevSessions.reduce((m, s) => Math.max(m, s.rel || 0), 0);
    const hint = last ? `Last (${shortDate(last.date)}): <b>${summarize(ex.name, last.sets)}</b>` : 'First time logging this lift';

    let bestCardE1rm = 0;
    const setsRows = ex.sets.map((s, sIdx) => {
      const e1rm = (s.reps && s.reps > 0 && s.weight !== null)
        ? Math.round(BodyWeightUtils.calculateE1rm(ex.name, workoutDate, s.weight, s.reps) * 10) / 10
        : null;
      if (e1rm && e1rm > bestCardE1rm) bestCardE1rm = e1rm;
      const radar = computeSetRadarLabel(ex.name, workoutDate, s.weight, s.reps, histPeakE1rm, histPeakRel);

      return `
        <tr data-ex="${exIdx}" data-set="${sIdx}" data-peak-e1rm="${histPeakE1rm}" data-peak-rel="${histPeakRel}">
          <td class="n">${sIdx + 1}</td>
          <td class="w"><input type="number" step="0.5" class="set-w" value="${s.weight ?? ''}" placeholder="${bw ? '+kg' : 'kg'}" /></td>
          <td class="r"><input type="number" step="1" class="set-r" value="${s.reps ?? ''}" placeholder="reps" /></td>
          <td class="e1rm">${e1rm ? `${e1rm}k` : '–'}${radar}</td>
          <td class="x"><button class="icon del-set" title="Delete set">✕</button></td>
        </tr>
      `;
    }).join('');

    const bestRelStr = bestCardE1rm > 0 && (hasBw || bw) && bwTodayKg > 0
      ? ` • ${(bestCardE1rm / bwTodayKg).toFixed(2)}× BW`
      : (bestCardE1rm > 0 && bw ? ` • ${(bestCardE1rm / BodyWeightUtils.weightOn(workoutDate)).toFixed(2)}× BW` : '');

    return `
      <div class="card ex-card" data-ex="${exIdx}">
        <div class="ex-head">
          <span class="ex-name" data-jump-progress="${esc(ex.name)}">${esc(ex.name)}</span>
          <span class="tag">${esc(info.category || 'Other')}</span>
          ${bw ? '<span class="tag" style="color:var(--pr);border-color:rgba(255,209,102,0.4)">BW</span>' : ''}
          ${activeGoal ? `<button class="primary compact goal-progress-btn" data-goal-ex="${esc(ex.name)}" style="margin-left:8px;font-size:11px;padding:3px 8px;">Goal progress</button>` : ''}
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
        <div class="ex-foot" style="display:flex;justify-content:space-between;align-items:center;">
          <button class="ghost compact add-set">+ Add set</button>
          ${bestCardE1rm > 0 ? `<span class="muted small">Best E1RM: <b>${bestCardE1rm} kg</b>${bestRelStr}</span>` : ''}
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
      const peakE = Number(tr.dataset.peakE1rm) || 0;
      const peakRel = Number(tr.dataset.peakRel) || 0;
      const dateStr = $('#log-date').value || today();
      const radar = computeSetRadarLabel(logExercises[exIdx].name, dateStr, w, r, peakE, peakRel);
      if (r && r > 0 && w !== null) {
        const eVal = Math.round(BodyWeightUtils.calculateE1rm(logExercises[exIdx].name, dateStr, w, r) * 10) / 10;
        e1rmEl.innerHTML = `${eVal}k${radar}`;
      } else {
        e1rmEl.innerHTML = `–${radar}`;
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

    toast('Workout saved', 'success');

    // If any goal was updated in this session, surface the Exercise Goal Report
    if (res.updatedGoals && res.updatedGoals.length > 0) {
      const topGoal = res.updatedGoals[0];
      toast(`Goal progress logged for ${topGoal.exerciseName}`, 'success');
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

  const { m, close } = openCustomModal({
    title: `Manage Programs (${progs.length})`,
    html,
    cancelText: 'Close',
    wide: true,
  });

  $('#m-new-prog-sub').onclick = () => {
    close();
    openCreateProgramDialog();
  };

  $$('.m-switch-prog', m).forEach((b) => {
    b.onclick = async () => {
      close();
      const state = await window.api.setActiveProgramId(b.dataset.id);
      setState(state);
      selectedProgramDayIdx = 0;
      renderProgramView();
    };
  });

  $$('.m-dup-prog', m).forEach((b) => {
    b.onclick = async () => {
      close();
      const state = await window.api.duplicateProgram(b.dataset.id);
      setState(state);
      renderProgramView();
      toast('Program duplicated!', 'success');
    };
  });

  $$('.m-ren-prog', m).forEach((b) => {
    b.onclick = async () => {
      close();
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
        close();
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

  const activeProgId = activeProg.id;
  const { m } = openCustomModal({
    title: `Add Exercise to ${day.dayName}`,
    html: `
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
    `,
    cancelText: 'Done',
    large: true,
  });

  setTimeout(() => { const s = $('#m-ex-search'); if (s) s.focus(); }, 50);

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
      row.onclick = async () => {
        const exName = row.dataset.name;
        const currProg = (S.programs || []).find((p) => p.id === activeProgId);
        const currDay = currProg?.days?.[selectedProgramDayIdx];
        if (!currProg || !currDay) return;
        currDay.exercises = currDay.exercises || [];
        currDay.exercises.push({
          exerciseName: exName,
          category: exInfo(exName).category || 'Other',
          targetSets,
          targetReps,
        });
        await saveCurrentProgramDays(currProg, currProg.days);
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

  const { m, close } = openCustomModal({
    title: 'Load Workout Day from Program',
    html: `<div style="max-height:360px;overflow-y:auto;">${rows}</div>`,
    cancelText: 'Cancel',
    wide: true,
  });

  $$('.load-prog-day-item', m).forEach((item) => {
    item.onclick = () => {
      close();
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
  const mode = TrainingModeClient.normalize(S.trainingMode);
  const name = goal.exerciseName || goal.exercise;
  const bw = isBW(name);
  const sessions = sessionsFor(name);
  const nowMs = sessions.length ? Math.max(...sessions.map((s) => s.t), Date.now()) : Date.now();
  const ninetyDaysMs = 90 * 86400000;
  let recentSessions = sessions.filter((s) => (nowMs - s.t) >= 0 && (nowMs - s.t) <= ninetyDaysMs).slice(-4);
  if (!recentSessions.length && sessions.length) recentSessions = sessions.slice(-1);

  const createdIso = goal.createdDate ? toSortableIso(goal.createdDate) : '';
  const sessionsSinceStart = createdIso
    ? sessions.filter((s) => toSortableIso(s.date) >= createdIso)
    : [];

  // Anchor to sessions logged since goal start, or manual starting baseline, or recent sessions
  const activeSessions = sessionsSinceStart.length
    ? sessionsSinceStart
    : (goal.baselineE1rmKg > 0 ? [] : recentSessions);

  const recentBestE1rm = recentSessions.reduce((m, s) => (s.e1rm && s.e1rm > m ? s.e1rm : m), 0);
  const activeBestE1rm = activeSessions.reduce((m, s) => (s.e1rm && s.e1rm > m ? s.e1rm : m), 0);
  const latestActive = activeSessions[activeSessions.length - 1] || sessions[sessions.length - 1] || null;
  const latestE1rm = latestActive?.e1rm || recentBestE1rm;

  const currentE1rm = sessionsSinceStart.length
    ? Math.max(activeBestE1rm, goal.baselineE1rmKg || 0)
    : (goal.baselineE1rmKg > 0 ? goal.baselineE1rmKg : recentBestE1rm);

  const bwKg = BodyWeightUtils.weightOn(today());
  const currentRelBw = bwKg > 0 && currentE1rm > 0 ? Math.round((currentE1rm / bwKg) * 100) / 100 : 0;
  const peakAllE1rm = Math.max(recentBestE1rm, goal.baselineE1rmKg || 0, currentE1rm);
  const retentionPct = peakAllE1rm > 0 && latestE1rm > 0
    ? Math.min(115, Math.round((latestE1rm / peakAllE1rm) * 100))
    : 100;

  let targetE1rm;
  if (goal.goalType === 'SETS_REPS') {
    targetE1rm = round(BodyWeightUtils.calculateE1rm(name, today(), goal.targetWeightKg || 0, Math.max(1, goal.targetReps || 8)));
  } else if (goal.goalType === 'RELATIVE_BW') {
    targetE1rm = round((Number(goal.targetRelativeBw) || 1.25) * bwKg);
  } else {
    targetE1rm = round(goal.targetE1rmKg || 0);
  }

  let isAchieved = !!goal.achievedDate;
  if (!isAchieved && activeSessions.length) {
    if (goal.goalType === 'SETS_REPS') {
      const tw = goal.targetWeightKg || 0;
      const tr = goal.targetReps || 8;
      const ts = goal.targetSets || 3;
      const hitSetsReps = activeSessions.some((sess) =>
        sess.sets.filter((s) => (s.reps || 0) >= tr && Number(s.weight || 0) >= tw - 0.01).length >= ts
      );
      isAchieved = hitSetsReps || (targetE1rm > 0 && activeBestE1rm >= targetE1rm + 2.5);
    } else if (goal.goalType === 'RELATIVE_BW') {
      const tgtRel = Number(goal.targetRelativeBw) || 1.25;
      isAchieved = activeSessions.some((s) => (s.rel || 0) >= tgtRel - 0.004) || (targetE1rm > 0 && activeBestE1rm >= targetE1rm - 0.05);
    } else {
      isAchieved = targetE1rm > 0 && activeBestE1rm >= targetE1rm - 0.05;
    }
  }

  const rawRatio = targetE1rm > 0 ? Math.round((currentE1rm / targetE1rm) * 100) : 0;
  const progressPct = isAchieved ? 100 : Math.max(0, Math.min(99, rawRatio));

  const targetHeadline = goal.goalType === 'SETS_REPS'
    ? `${goal.targetSets || 3} × ${goal.targetReps || 8} @ ${bw ? (goal.targetWeightKg === 0 ? 'BW' : `BW+${fmt(goal.targetWeightKg)}kg`) : `${fmt(goal.targetWeightKg)}kg`}`
    : (goal.goalType === 'RELATIVE_BW'
      ? `${(Number(goal.targetRelativeBw) || 1.25).toFixed(2)}× BW (${fmt(targetE1rm)}kg)`
      : `${fmt(targetE1rm)} kg 1RM`);

  let statusLabel;
  let statusColor;
  if (isAchieved) {
    statusLabel = 'Achieved';
    statusColor = 'var(--pr)';
  } else if (mode === 'CUT') {
    if (retentionPct >= TrainingModeClient.CUT_RETENTION_FLOOR_PCT) {
      statusLabel = 'Strength retained';
      statusColor = '#60a5fa';
    } else {
      statusLabel = 'Retention watch';
      statusColor = 'var(--accent-2)';
    }
  } else if (progressPct >= 80) {
    statusLabel = 'On track';
    statusColor = 'var(--up)';
  } else {
    statusLabel = 'Building';
    statusColor = '#60a5fa';
  }

  // Prescribe weights scaled to current working strength & training mode
  const workingBaseE1rm = currentE1rm > 0 ? currentE1rm : Math.max(20, targetE1rm * 0.78);
  const step = (exInfo(name).category === 'Arms' || name.toLowerCase().includes('raise') || workingBaseE1rm < 45) ? 1.25 : 2.5;
  const calcLoadFor = (e1rmVal, repsCount) => {
    const total = e1rmVal / (1 + repsCount / 30);
    const ext = bw ? Math.max(0, total - bwKg) : total;
    return bw && ext <= 0 ? 0 : Math.max(step, round(ext, step));
  };

  let nextSets = mode === 'CUT' ? 2 : (mode === 'BULK' ? 4 : 3);
  let nextReps = mode === 'CUT' ? 5 : 5;
  const targetFactor = mode === 'CUT' ? 0.94 : (mode === 'BULK' ? 0.98 : 0.96);
  let nextWeight = calcLoadFor(workingBaseE1rm * targetFactor, nextReps);
  if (goal.goalType === 'SETS_REPS') {
    nextSets = goal.targetSets || (mode === 'CUT' ? 2 : 3);
    nextReps = goal.targetReps || 8;
    const wCap = goal.targetWeightKg > 0 ? goal.targetWeightKg : 9999;
    nextWeight = Math.min(wCap, calcLoadFor(workingBaseE1rm, nextReps));
  }
  const nextSessionSummary = `${nextSets} × ${nextReps} @ ${bw && nextWeight === 0 ? 'BW' : `${bw ? 'BW+' : ''}${fmt(nextWeight)}kg`}`;

  const phases = [0.25, 0.5, 0.75, 1.0].map((frac, idx) => {
    const titles = mode === 'CUT'
      ? ['Block 1: Anchor Top Sets', 'Block 2: Hold Bar Speed', 'Block 3: Peak Retention', 'Block 4: Target Standard']
      : ['Phase 1: Accumulation', 'Phase 2: Strength Transition', 'Phase 3: Heavy Overload', 'Phase 4: Goal Attempt'];
    const phaseReps = idx < 3 ? [8, 6, 4][idx] : (goal.goalType === 'SETS_REPS' ? nextReps : 3);
    const phaseSets = idx === 3 && goal.goalType === 'SETS_REPS' ? nextSets : (mode === 'CUT' ? 2 : 3);
    const span = Math.max(2.5, targetE1rm - workingBaseE1rm);
    const phaseE1rm = workingBaseE1rm + span * frac;
    const phaseW = (idx === 3 && goal.goalType === 'SETS_REPS' && goal.targetWeightKg > 0)
      ? goal.targetWeightKg
      : calcLoadFor(phaseE1rm, phaseReps);
    return {
      title: titles[idx],
      prescription: idx === 3 ? targetHeadline : `${phaseSets} × ${phaseReps} @ ${bw && phaseW === 0 ? 'BW' : `${bw ? 'BW+' : ''}${fmt(phaseW)}kg`}`,
    };
  });

  return {
    goal,
    mode,
    name,
    bw,
    category: exInfo(name).category || 'Other',
    bestE1rm: round(currentE1rm),
    currentRelBw,
    retentionPct,
    targetE1rm,
    targetHeadline,
    progressPct,
    isAchieved,
    statusLabel,
    statusColor,
    nextSets,
    nextReps,
    nextWeight,
    nextSessionSummary,
    phases,
  };
}

function renderGoals() {
  const mode = TrainingModeClient.normalize(S.trainingMode);
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

  const bannerHtml = mode === 'MAINGAIN' ? '' : `
    <div class="mode-banner ${mode === 'CUT' ? 'mode-cut' : 'mode-bulk'}">
      ${mode === 'CUT'
        ? '<b>Cut phase lens:</b> Holding ≥95% E1RM counts as <i>Strength retained</i>. Relative (× BW) goals automatically scale kg targets as your body weight drops.'
        : '<b>Bulk phase lens:</b> Surplus phase active — pushing progressive overload and peak E1RM PR milestones.'}
    </div>
  `;

  const host = $('#goals-list');
  if (!filtered.length) {
    host.innerHTML = `${bannerHtml}
      <div class="empty">
        ${allGoals.length ? 'No goals match this filter.' : 'No strength goals set yet. Click <b>+ New goal</b> above to set a 1RM, Relative ×BW, or Sets × Reps target.'}
      </div>
    `;
    return;
  }

  host.innerHTML = bannerHtml + filtered.map((e) => `
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
          <span class="goal-pill ${e.isAchieved ? 'achieved' : (e.goal.enabled === false ? 'paused' : '')}" style="${!e.isAchieved && e.goal.enabled !== false ? `color:${e.statusColor};border-color:${e.statusColor};` : ''}">
            ${e.goal.enabled === false ? 'Paused' : esc(e.statusLabel)}
          </span>
        </div>
        <div class="muted small font-bold">
          ${e.goal.goalType === 'RELATIVE_BW' && e.currentRelBw > 0
            ? `Curr: ${e.currentRelBw.toFixed(2)}× BW • ${e.progressPct}%`
            : (mode === 'CUT' && e.currentRelBw > 0
              ? `${e.currentRelBw.toFixed(2)}× BW • ${e.retentionPct}% held`
              : `Current: ${e.bestE1rm}kg • ${e.progressPct}%`)}
        </div>
      </div>

      <div class="goal-progress-bar">
        <div class="goal-progress-fill ${e.isAchieved ? 'achieved' : ''}" style="width:${e.progressPct}%;${!e.isAchieved && mode === 'CUT' ? 'background:linear-gradient(90deg,#60a5fa,#3ddc97);' : ''}"></div>
      </div>

      <div class="goal-card-foot">
        <div class="goal-next-summary">${e.isAchieved ? 'Goal reached' : `Next: ${esc(e.nextSessionSummary)}`}</div>
        <div class="goal-actions-group">
          ${!e.isAchieved ? `<button class="primary compact train-goal-btn" data-id="${e.goal.id}">Train</button>` : ''}
          <button class="ghost compact toggle-goal-expand">Details ▾</button>
        </div>
      </div>

      <div class="goal-expanded-body hidden">
        <div class="small font-bold muted">Prescribed working sets (${esc(TrainingModeClient.meta(mode).shortLabel)})</div>
        <div style="font-size:13px;color:var(--text);">${e.nextSets} sets × ${e.nextReps} reps @ ${e.bw && e.nextWeight === 0 ? 'BW' : `${fmt(e.nextWeight)}kg`}</div>

        <div class="small font-bold muted" style="margin-top:6px;">Milestones</div>
        <div style="display:grid;grid-template-columns:repeat(auto-fit, minmax(180px, 1fr));gap:6px;">
          ${e.phases.map((p) => `
            <div style="background:var(--bg);border:1px solid var(--border);border-radius:6px;padding:6px 8px;font-size:11.5px;">
              <div style="font-weight:700;color:var(--text);">${esc(p.title)}</div>
              <div style="color:var(--accent);">${esc(p.prescription)}</div>
            </div>
          `).join('')}
        </div>

        <div class="btn-row" style="margin-top:10px;">
          <button class="ghost compact inspect-report-btn" data-id="${e.goal.id}">Full report</button>
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
  const activePhase = TrainingModeClient.normalize(S.trainingMode);
  const exercises = S.exercises || [];
  const exNames = exercises.map((e) => e.name).sort();

  const initModeType = existingGoal?.goalType === 'SETS_REPS'
    ? 'SETS_REPS'
    : (existingGoal?.goalType === 'RELATIVE_BW' ? 'RELATIVE_BW' : 'E1RM');
  const defaultEx = existingGoal?.exerciseName || exNames[0] || 'Bench Press';
  const currBwKg = BodyWeightUtils.weightOn(today());

  const recentBaselineFor = (exName) => {
    const sess = sessionsFor(exName);
    const latest = sess[sess.length - 1] || null;
    if (!latest) return { w: 0, r: 5, e1rm: 0, date: '' };
    const topW = latest.top ?? 0;
    const topR = latest.best?.reps || latest.sets.find((s) => (s.reps || 0) > 0)?.reps || 5;
    return { w: topW, r: topR, e1rm: latest.e1rm || 0, date: shortDate(latest.date) };
  };

  const initRec = recentBaselineFor(defaultEx);
  const initCurrW = existingGoal?.baselineWeightKg > 0 ? existingGoal.baselineWeightKg : initRec.w;
  const initCurrR = existingGoal?.baselineReps > 0 ? existingGoal.baselineReps : initRec.r;
  const initCurrE = existingGoal?.baselineE1rmKg > 0 ? existingGoal.baselineE1rmKg : initRec.e1rm;
  const initMult = activePhase === 'CUT' ? 1.0 : (activePhase === 'BULK' ? 1.1 : 1.075);
  const initRel = existingGoal?.targetRelativeBw > 0
    ? existingGoal.targetRelativeBw
    : (initCurrE > 0 && currBwKg > 0 ? Math.round(((initCurrE / currBwKg) * (activePhase === 'CUT' ? 1.05 : 1.12)) * 20) / 20 : 1.25);

  const pModal = modal({
    title: existingGoal ? 'Edit Strength Goal' : `Create Strength Goal (${TrainingModeClient.meta(activePhase).shortLabel})`,
    wide: true,
    html: `
      <div style="display:flex;gap:12px;margin-bottom:12px;">
        <label style="flex:1;">Exercise
          <input type="text" id="m-goal-ex" list="exercise-list" value="${esc(defaultEx)}" ${existingGoal ? 'disabled' : ''} />
        </label>
        <label style="width:210px;">Goal Mode
          <select id="m-goal-mode">
            <option value="E1RM" ${initModeType === 'E1RM' ? 'selected' : ''}>Estimated 1RM (kg)</option>
            <option value="RELATIVE_BW" ${initModeType === 'RELATIVE_BW' ? 'selected' : ''}>Relative Strength (× BW)</option>
            <option value="SETS_REPS" ${initModeType === 'SETS_REPS' ? 'selected' : ''}>Sets × Reps @ Weight</option>
          </select>
        </label>
      </div>

      <div style="background:var(--bg);border:1px solid var(--border);border-radius:10px;padding:10px 12px;margin-bottom:12px;">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
          <span class="small font-bold uppercase muted">Current Strength (Starting Point)</span>
          <span id="m-goal-recent-hint" class="muted small"></span>
        </div>
        <div style="display:grid;grid-template-columns:1fr 0.8fr 1fr;gap:10px;">
          <label>Curr Weight (kg)<input type="number" step="0.5" min="0" id="m-goal-curr-w" value="${initCurrW || ''}" placeholder="e.g. 80" /></label>
          <label>Curr Reps<input type="number" min="1" max="50" id="m-goal-curr-r" value="${initCurrR || 5}" /></label>
          <label>Starting 1RM (kg)<input type="number" step="0.5" min="0" id="m-goal-curr-e1rm" value="${initCurrE ? round(initCurrE) : ''}" placeholder="Auto or custom" /></label>
        </div>
      </div>

      <div id="m-mode-1rm" style="display:${initModeType === 'E1RM' ? 'block' : 'none'};">
        <label>Target 1-Rep Max (kg)
          <input type="number" step="0.5" id="m-goal-1rm" value="${existingGoal?.targetE1rmKg || (initCurrE ? round(initCurrE * initMult, 2.5) : 100)}" />
        </label>
      </div>

      <div id="m-mode-rel" style="display:${initModeType === 'RELATIVE_BW' ? 'block' : 'none'};">
        <label>Target Multiple (× Body Weight)
          <input type="number" step="0.05" min="0.2" max="5" id="m-goal-rel-bw" value="${Number(initRel).toFixed(2)}" />
        </label>
        <div class="muted small" style="margin-top:4px;">Scales automatically with your logged body weight (currently ${f1(currBwKg)} kg → ~<b id="m-goal-rel-kg">${round(initRel * currBwKg, 0.5)} kg</b>).</div>
      </div>

      <div id="m-mode-sets-reps" style="display:${initModeType === 'SETS_REPS' ? 'grid' : 'none'};grid-template-columns:1fr 1fr 1.4fr;gap:10px;">
        <label>Target Sets<input type="number" id="m-goal-sets" value="${existingGoal?.targetSets || (activePhase === 'CUT' ? 2 : 3)}" min="1" max="15" /></label>
        <label>Target Reps<input type="number" id="m-goal-reps" value="${existingGoal?.targetReps || (activePhase === 'CUT' ? 5 : 8)}" min="1" max="50" /></label>
        <label>Target Weight (kg)<input type="number" step="0.5" id="m-goal-weight" value="${existingGoal?.targetWeightKg || (initCurrW ? round(initCurrW + (activePhase === 'CUT' ? 0 : 5), 2.5) : 80)}" /></label>
      </div>

      <label style="margin-top:12px;">Notes / Focus (Optional)
        <input type="text" id="m-goal-notes" value="${esc(existingGoal?.notes || '')}" placeholder="e.g. Hold top-set bar speed during cut" />
      </label>
    `,
    okText: existingGoal ? 'Save Goal' : 'Create Goal',
  });

  // Wire live interactive handlers inside the mounted modal
  const updateRecentHint = (rec) => {
    const el = $('#m-goal-recent-hint');
    if (el) {
      el.textContent = rec.date ? `Latest (${rec.date}): ${rec.w}kg × ${rec.r} (${round(rec.e1rm)}kg E1RM)` : 'No recent workouts logged';
    }
  };
  updateRecentHint(initRec);

  const recalcCurrE1rm = () => {
    const exName = ($('#m-goal-ex')?.value || defaultEx).trim();
    const wStr = $('#m-goal-curr-w')?.value;
    const w = Number(wStr);
    const r = Math.max(1, Number($('#m-goal-curr-r')?.value) || 1);
    if (wStr !== '' && !Number.isNaN(w) && (w > 0 || isBW(exName))) {
      $('#m-goal-curr-e1rm').value = round(BodyWeightUtils.calculateE1rm(exName, today(), w, r));
    }
  };

  $('#m-goal-curr-w')?.addEventListener('input', recalcCurrE1rm);
  $('#m-goal-curr-r')?.addEventListener('input', recalcCurrE1rm);
  $('#m-goal-rel-bw')?.addEventListener('input', (e) => {
    const mult = Number(e.target.value) || 0;
    const lbl = $('#m-goal-rel-kg');
    if (lbl) lbl.textContent = `${round(mult * currBwKg, 0.5)} kg`;
  });
  $('#m-goal-mode')?.addEventListener('change', (e) => {
    const v = e.target.value;
    $('#m-mode-1rm').style.display = v === 'E1RM' ? 'block' : 'none';
    $('#m-mode-rel').style.display = v === 'RELATIVE_BW' ? 'block' : 'none';
    $('#m-mode-sets-reps').style.display = v === 'SETS_REPS' ? 'grid' : 'none';
  });

  if (!existingGoal) {
    $('#m-goal-ex')?.addEventListener('change', (e) => {
      const exName = e.target.value.trim();
      const rec = recentBaselineFor(exName);
      updateRecentHint(rec);
      $('#m-goal-curr-w').value = rec.w || '';
      $('#m-goal-curr-r').value = rec.r || 5;
      $('#m-goal-curr-e1rm').value = rec.e1rm ? round(rec.e1rm) : '';
      if (rec.e1rm > 0) $('#m-goal-1rm').value = round(rec.e1rm * initMult, 2.5);
      if (rec.w > 0) $('#m-goal-weight').value = round(rec.w + (activePhase === 'CUT' ? 0 : 5), 2.5);
    });
  }

  const res = await pModal;

  if (res) {
    const exName = res.querySelector('#m-goal-ex').value.trim();
    const mode = res.querySelector('#m-goal-mode').value;
    const notes = res.querySelector('#m-goal-notes').value.trim();

    const baselineWeightKg = Number(res.querySelector('#m-goal-curr-w').value) || 0;
    const baselineReps = Math.max(1, Number(res.querySelector('#m-goal-curr-r').value) || 5);
    const enteredCurrE1rm = Number(res.querySelector('#m-goal-curr-e1rm').value) || 0;
    const baselineE1rmKg = enteredCurrE1rm > 0
      ? enteredCurrE1rm
      : (baselineWeightKg > 0 ? round(BodyWeightUtils.calculateE1rm(exName, today(), baselineWeightKg, baselineReps)) : 0);

    const targetE1rmKg = Number(res.querySelector('#m-goal-1rm').value) || 0;
    const targetRelativeBw = Number(res.querySelector('#m-goal-rel-bw').value) || 1.25;
    const targetSets = Number(res.querySelector('#m-goal-sets').value) || 3;
    const targetReps = Number(res.querySelector('#m-goal-reps').value) || 8;
    const targetWeightKg = Number(res.querySelector('#m-goal-weight').value) || 0;

    const targetChanged = existingGoal && (
      existingGoal.goalType !== mode
      || (mode === 'E1RM'
        ? Number(existingGoal.targetE1rmKg) !== targetE1rmKg
        : (mode === 'RELATIVE_BW'
          ? Number(existingGoal.targetRelativeBw) !== targetRelativeBw
          : (Number(existingGoal.targetSets) !== targetSets || Number(existingGoal.targetReps) !== targetReps || Number(existingGoal.targetWeightKg) !== targetWeightKg)))
    );

    const goalObj = {
      ...(existingGoal || {}),
      exerciseName: exName,
      goalType: mode,
      targetE1rmKg,
      targetRelativeBw,
      targetSets,
      targetReps,
      targetWeightKg,
      baselineWeightKg,
      baselineReps,
      baselineE1rmKg,
      enabled: existingGoal ? existingGoal.enabled !== false : true,
      createdDate: existingGoal?.createdDate || fmtDdMonYyyy(today()),
      achievedDate: targetChanged ? null : (existingGoal?.achievedDate || null),
      notes,
    };

    const state = await window.api.saveGoal(goalObj);
    setState(state);
    renderGoals();
    toast('Goal saved!', 'success');
  }
}

function openGoalProgressModal(g) {
  if (!g) return;
  const ev = evaluateGoalDesktop(g);
  const meta = TrainingModeClient.meta(ev.mode);
  const { body, close } = openCustomModal({
    title: `${ev.name} • Live goal progress`,
    large: true,
    cancelText: 'Close',
    html: `
      <div class="stats" style="grid-template-columns:repeat(4,1fr);margin-bottom:14px;">
        <div class="stat"><div class="lbl">Phase lens</div><div class="val" style="color:${meta.color};font-size:17px;">${esc(meta.shortLabel)}</div></div>
        <div class="stat"><div class="lbl">Target</div><div class="val" style="color:var(--accent);font-size:16px;">${esc(ev.targetHeadline)}</div></div>
        <div class="stat"><div class="lbl">${ev.mode === 'CUT' ? 'Retention' : 'Progress'}</div><div class="val" style="color:${esc(ev.statusColor)};font-size:17px;">${ev.mode === 'CUT' ? `${ev.retentionPct}% held` : `${ev.progressPct}%`}</div></div>
        <div class="stat"><div class="lbl">Rel. strength</div><div class="val">${ev.currentRelBw > 0 ? `${ev.currentRelBw.toFixed(2)}× BW` : '–'}</div></div>
      </div>
      <div class="card" style="margin-bottom:12px;">
        <div class="small font-bold muted" style="margin-bottom:6px;">Recommended today (${esc(meta.shortLabel)} phase)</div>
        <div style="font-size:15px;font-weight:700;color:var(--text);">${esc(ev.nextSessionSummary)}</div>
        <div class="muted small" style="margin-top:6px;">${ev.mode === 'CUT'
          ? 'Keep top-set intensity sharp and stop 1–2 reps shy of failure to protect strength without extra fatigue.'
          : (ev.mode === 'BULK'
            ? 'Push top sets with controlled form and aim for progressive weight or rep overload.'
            : 'Match or add 1 clean rep over your previous session while maintaining crisp bar speed.')}</div>
      </div>
      <div class="btn-row" style="justify-content:flex-end;">
        <button type="button" id="m-gp-full" class="primary compact">View full goal history</button>
      </div>
    `,
  });
  body.querySelector('#m-gp-full')?.addEventListener('click', () => {
    close();
    openExerciseGoalReportModal(g.id);
  });
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
      <td>${r.achievedGoal ? 'Achieved' : 'In progress'}</td>
    </tr>
  `).join('');

  modal({
    title: `Goal report: ${ev.name}`,
    wide: true,
    html: `
      <div class="stats" style="grid-template-columns:repeat(4,1fr);margin-bottom:14px;">
        <div class="stat"><div class="lbl">Target</div><div class="val" style="color:var(--accent);font-size:17px;">${esc(ev.targetHeadline)}</div></div>
        <div class="stat"><div class="lbl">${ev.mode === 'CUT' ? 'Rel. strength' : 'Current best'}</div><div class="val">${ev.mode === 'CUT' && ev.currentRelBw > 0 ? `${ev.currentRelBw.toFixed(2)}× BW` : `${ev.bestE1rm}kg`}</div></div>
        <div class="stat"><div class="lbl">${ev.mode === 'CUT' ? 'Retention' : 'Progress'}</div><div class="val" style="color:${ev.isAchieved ? 'var(--pr)' : esc(ev.statusColor)}">${ev.mode === 'CUT' ? `${ev.retentionPct}% held` : `${ev.progressPct}%`}</div></div>
        <div class="stat"><div class="lbl">Status</div><div class="val" style="font-size:16px;color:${esc(ev.statusColor)};">${esc(ev.statusLabel)}</div></div>
      </div>

      <div class="card" style="margin-bottom:14px;">
        <div class="small font-bold muted" style="margin-bottom:6px;">Next session prescription</div>
        <div style="font-size:14px;font-weight:700;color:var(--text);">${esc(ev.nextSessionSummary)}</div>
      </div>

      <div class="card">
        <div class="small font-bold muted" style="margin-bottom:8px;">Logged goal sessions (${reports.length})</div>
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
        <span class="goal-pill ${e.isAchieved ? 'achieved' : ''}" style="${!e.isAchieved ? `color:${e.statusColor};border-color:${e.statusColor};` : ''}">${esc(e.statusLabel)}</span>
        <span style="font-weight:700;color:var(--accent);">${e.progressPct}%</span>
        <button class="ghost compact m-view-single-report" data-id="${e.goal.id}">View Report</button>
      </div>
    </div>
  `).join('');

  const { m, close } = openCustomModal({
    title: `All goals report (${evals.length})`,
    html: `
      <div style="max-height:400px;overflow-y:auto;">
        ${rows || '<div class="muted">No goals configured yet.</div>'}
      </div>
    `,
    cancelText: 'Close',
    large: true,
  });

  $$('.m-view-single-report', m).forEach((b) => {
    b.onclick = () => {
      close();
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
  // Training Phase Mode
  const mode = TrainingModeClient.normalize(S.trainingMode);
  const meta = TrainingModeClient.meta(mode);
  const pill = $('#settings-mode-pill');
  if (pill) {
    pill.textContent = meta.shortLabel;
    pill.className = `mode-pill ${meta.cssClass}`;
  }
  $$('#settings-mode-seg button[data-mode]').forEach((b) => {
    b.classList.toggle('active', b.dataset.mode === mode);
  });
  const detailsEl = $('#settings-mode-details');
  if (detailsEl) {
    detailsEl.innerHTML = `
      <div class="tagline" style="color:${meta.color};">${esc(meta.tagline)}</div>
      <ul>${meta.bullets.map((b) => `<li>${esc(b)}</li>`).join('')}</ul>
    `;
  }

  // Google Sheets info
  const cfg = S.storage || {};
  const statusEl = $('#sheets-status');
  if (cfg.kind === 'sheets') {
    statusEl.innerHTML = `<span class="ok">Connected to Google Sheets</span> • <code>${esc(cfg.title || cfg.spreadsheetId)}</code>${cfg.error ? ` <span class="err">(${esc(cfg.error)})</span>` : ''}`;
  } else {
    statusEl.innerHTML = `<span class="warn">Using offline local storage</span>${cfg.error ? ` • <span class="err">${esc(cfg.error)}</span>` : ''}`;
  }
  $('#cfg-sheet').value = cfg.spreadsheetId || '';
  $('#cfg-key').value = cfg.keyFile || '';
  if ($('#cfg-sync-now')) $('#cfg-sync-now').disabled = cfg.kind !== 'sheets';

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
  $('#bw-current-value').textContent = latestBw !== null ? `${f1(latestBw)} kg` : '-- kg';
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
      await busy(async () => {
        const state = await window.api.deleteBodyWeight(btn.dataset.date);
        setState(state);
        renderSettings();
      });
    };
  });

  // Data summary
  $('#data-summary').textContent = `${S.workouts.length} workouts • ${S.sets.length} sets • ${S.exercises.length} exercises • ${(S.bodyWeights || []).length} weights • ${(S.goals || []).length} goals • ${(S.programs || []).length} programs • ${(S.savedSummaries || []).length} summaries`;
}

function initSettingsModule() {
  $('#sidebar-mode-badge')?.addEventListener('click', openModePickerModal);
  $$('#settings-mode-seg button[data-mode]').forEach((btn) => {
    btn.addEventListener('click', () => switchTrainingMode(btn.dataset.mode));
  });

  $('#bw-log-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const dVal = $('#bw-input-date').value;
    const kgVal = $('#bw-input-kg').value;
    if (!dVal || !kgVal) return;
    await busy(async () => {
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

  $('#cfg-sync-now').addEventListener('click', async () => {
    if (S.storage.kind !== 'sheets') return toast('Connect Google Sheets first', 'error');
    await busy(async () => {
      const state = await window.api.syncNow();
      setState(state);
      if (state.storage?.error) {
        toast(state.storage.error, 'error');
      } else {
        toast('Synced with Google Sheets!', 'success');
      }
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
  const mode = TrainingModeClient.normalize(S.trainingMode);
  const targets = TrainingModeClient.weeklyMuscleTargets(mode);
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
  return { list, byCat, refMs: latestMs, mode };
}

function buildSuggestedExerciseItem(slot, slotIndex, candidateOffset, alreadyPicked, volumeInfo) {
  const mode = volumeInfo.mode || TrainingModeClient.normalize(S.trainingMode);
  const available = slot.candidates.filter((n) => !alreadyPicked.has(n.toLowerCase()));
  const pool = available.length ? available : slot.candidates;

  const ranked = [...pool].sort((a, b) => scoreCandidate(b) - scoreCandidate(a));

  function scoreCandidate(name) {
    const cat = exInfo(name).category || slot.categoryHint;
    const volScale = mode === 'BULK' ? 11.5 : 10;
    const volDeficitBonus = (volumeInfo.byCat[cat]?.deficitRatio ?? 0.5) * volScale;
    const benefitBonus = EXERCISE_BENEFITS[name] ? 4.0 : 0.0;
    const pts = sessionsFor(name);
    if (!pts.length) return 8.0 + volDeficitBonus + benefitBonus;

    const lastPt = pts[pts.length - 1];
    const peakE1rm = Math.max(...pts.map((p) => p.e1rm || 0));
    const currE1rm = lastPt?.e1rm || 0;
    const daysSince = lastPt ? Math.max(0, Math.min(60, (volumeInfo.refMs - lastPt.t) / 86400000)) : 30;
    const plateauGapBonus = mode === 'CUT'
      ? (peakE1rm > 0 && currE1rm >= peakE1rm * TrainingModeClient.CUT_RETENTION_FLOOR_RATIO ? 6.0 : 3.0)
      : (peakE1rm > 0 && currE1rm < peakE1rm ? Math.min(15, ((peakE1rm - currE1rm) / peakE1rm) * 25) : 5.0);
    const recencyBonus = daysSince < 2 ? -20 : (daysSince >= 3 && daysSince <= 18 ? 12 : 6);
    const relBonus = mode === 'CUT' && isBW(name) ? 2.0 : 0;
    return (Math.min(25, pts.length) * 0.8) + plateauGapBonus + recencyBonus + volDeficitBonus + benefitBonus + relBonus;
  }

  const chosenName = ranked[candidateOffset % ranked.length];
  const cat = exInfo(chosenName).category || slot.categoryHint;
  const bw = isBW(chosenName);
  const sessions = sessionsFor(chosenName);
  const lastSession = sessions[sessions.length - 1] || null;
  const prevSession = sessions.length >= 2 ? sessions[sessions.length - 2] : null;
  const benefitText = EXERCISE_BENEFITS[chosenName] || exInfo(chosenName).description || '';

  const numSets = mode === 'CUT'
    ? (slotIndex >= 3 ? 2 : 3)
    : (mode === 'BULK' ? (slotIndex < 3 ? 4 : 3) : 3);

  if (!lastSession) {
    return {
      slotTitle: slot.name,
      exerciseName: chosenName,
      category: cat,
      isBodyweight: bw,
      lastSummary: 'No prior sessions logged',
      lastDate: 'First Time',
      targetSummary: `${numSets} × ${slot.defaultReps} reps`,
      reasonTag: benefitText ? `New exercise: ${benefitText}` : `New stimulus for ${cat} (${numSets} × ${slot.defaultReps})`,
      prescribedSets: Array.from({ length: numSets }, () => ({ weight: bw ? 0 : '', reps: slot.defaultReps, notes: '' })),
    };
  }

  const validLastSets = lastSession.sets.filter((s) => (s.reps || 0) > 0);
  const baseWeight = lastSession.top ?? 0;
  let baseReps = slot.defaultReps;
  if (lastSession.best && lastSession.best.reps) baseReps = lastSession.best.reps;
  else if (validLastSets.length) baseReps = validLastSets[0].reps || slot.defaultReps;

  const readyForJump = baseWeight > 0 && (
    mode === 'CUT'
      ? (baseReps >= 12)
      : (mode === 'BULK'
        ? ((baseReps >= 8) || (prevSession && (lastSession.e1rm || 0) >= (prevSession.e1rm || 0) && baseReps >= slot.defaultReps))
        : ((baseReps >= 8 && slot.defaultReps <= 6) || (baseReps >= 12) || (prevSession && (lastSession.e1rm || 0) > (prevSession.e1rm || 0) && baseReps >= slot.defaultReps + 1)))
  );

  let targetWeight, targetReps, overloadNote;
  if (bw && baseWeight <= 0) {
    targetWeight = 0;
    targetReps = mode === 'CUT' ? Math.max(slot.defaultReps, baseReps) : Math.max(slot.defaultReps, baseReps + 1);
    overloadNote = mode === 'CUT'
      ? `Cut Retain: Match ${targetReps} clean reps across ${numSets} sets`
      : `Aim for +1 rep per set over last session (${baseReps} → ${targetReps} reps)`;
  } else if (readyForJump) {
    const inc = (cat === 'Arms' || (cat === 'Shoulders' && chosenName.toLowerCase().includes('raise'))) ? 1.25 : 2.5;
    targetWeight = Math.round((baseWeight + inc) * 2) / 2;
    targetReps = Math.max(slot.defaultReps, baseReps - 2);
    overloadNote = `${mode === 'CUT' ? 'Cut micro-jump' : 'Weight progression'}: +${fmt(targetWeight - baseWeight)}kg (${numSets} × ${targetReps} @ ${fmt(targetWeight)}kg)`;
  } else if (mode === 'CUT') {
    targetWeight = baseWeight;
    targetReps = Math.max(slot.defaultReps, baseReps);
    overloadNote = `Cut Retain: Hold ${fmt(targetWeight)}kg × ${targetReps}`;
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
  if (mode === 'CUT') {
    if (slotIndex >= 3) {
      reasonTag = `Low-fatigue volume: ${numSets} clean sets for ${cat} to protect recovery (${overloadNote})${benefitSuffix}`;
    } else if (bw || (targetE1rm > peakE1rm && peakE1rm > 0)) {
      reasonTag = `Relative strength: ${overloadNote} — high payoff during weight loss${benefitSuffix}`;
    } else {
      reasonTag = `Strength retention: ${overloadNote} to hold ≥95% E1RM${benefitSuffix}`;
    }
  } else if (mode === 'BULK') {
    if (targetE1rm > peakE1rm && peakE1rm > 0) {
      reasonTag = `PR opportunity: Hitting ${bw && targetWeight === 0 ? 'BW' : `${fmt(targetWeight)}kg`} × ${targetReps} sets a new E1RM PR (${fmt(round(targetE1rm))}kg)${benefitSuffix}`;
    } else if (volStat && volStat.setsLast7Days < volStat.targetWeeklySets * 0.7) {
      reasonTag = `Hypertrophy volume: ${cat} has ${volStat.setsLast7Days}/${volStat.targetWeeklySets} sets this week — ${numSets} sets scheduled${benefitSuffix}`;
    } else {
      reasonTag = `Surplus overload: Build on ${shortDate(lastSession.date)} (${overloadNote})${benefitSuffix}`;
    }
  } else {
    if (peakE1rm > 0 && currE1rm < peakE1rm * 0.94) {
      reasonTag = `Needs focus: Current E1RM (${fmt(currE1rm)}kg) is below your ${fmt(peakE1rm)}kg PR${benefitSuffix}`;
    } else if (targetE1rm > peakE1rm && peakE1rm > 0) {
      reasonTag = `PR opportunity: Hitting ${bw && targetWeight === 0 ? 'BW' : `${fmt(targetWeight)}kg`} × ${targetReps} sets a new E1RM PR (${fmt(round(targetE1rm))}kg)${benefitSuffix}`;
    } else if (volStat && volStat.setsLast7Days < volStat.targetWeeklySets / 2) {
      reasonTag = `Weekly volume gap: ${cat} has only ${volStat.setsLast7Days}/${volStat.targetWeeklySets} sets in the last 7 days${benefitSuffix}`;
    } else {
      reasonTag = `Progressive overload: Build on ${shortDate(lastSession.date)} (${overloadNote})${benefitSuffix}`;
    }
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

  const phaseMeta = TrainingModeClient.meta(volumeInfo.mode);
  $('#suggest-sub').textContent = `Recommended today: ${SUGGEST_SPLITS[recommendedSplit].title} • ${phaseMeta.shortLabel} targets (${phaseMeta.tagline})`;

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
      <span class="muted small font-bold uppercase">7-Day Muscle Volume Targets (${esc(phaseMeta.shortLabel)})</span>
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
    const item = buildSuggestedExerciseItem(slot, sIdx, offset, picked, volumeInfo);
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

  const mode = TrainingModeClient.normalize(S.trainingMode);
  $('#workout-detail-exercises').innerHTML = [...grouped.entries()].map(([name, sList]) => {
    const bw = isBW(name);
    const allSessions = sessionsFor(name);
    const sessIdx = allSessions.findIndex((x) => x.workoutId === w.id);
    const currSess = sessIdx >= 0 ? allSessions[sessIdx] : null;
    const prevSess = sessIdx > 0 ? allSessions[sessIdx - 1] : null;
    const e1rmDelta = currSess && prevSess && currSess.e1rm && prevSess.e1rm
      ? Math.round((currSess.e1rm - prevSess.e1rm) * 10) / 10
      : null;
    const isPr = currSess && (currSess.isE1rmPr || currSess.isWeightPr);
    const isRelPr = currSess && currSess.isRelPr && !isPr;
    const isHeldDip = e1rmDelta !== null && e1rmDelta < 0 && prevSess && TrainingModeClient.isExpectedCutDip(
      mode, currSess.e1rm, prevSess.e1rm, currSess.rel || 0, prevSess.rel || 0
    );
    const deltaHtml = e1rmDelta && e1rmDelta !== 0
      ? `<span class="delta ${e1rmDelta > 0 ? 'up' : (isHeldDip ? 'delta-retained' : 'down')}" style="margin-left:6px;">${e1rmDelta > 0 ? '+' : ''}${e1rmDelta}kg${isHeldDip ? ' (held)' : ''}</span>`
      : '';

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
            ${isPr ? '<span class="badge-rel-pr">PR</span>' : (isRelPr && mode === 'CUT' ? '<span class="badge-rel-pr">Rel PR</span>' : '')}
          </div>
          <div class="detail-summary-badge">${summarize(name, sList)}${deltaHtml}</div>
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
const progressState = { exercise: null, metric: null, range: 0 };

function renderProgressOverview() {
  const mode = TrainingModeClient.normalize(S.trainingMode);
  const meta = TrainingModeClient.meta(mode);
  progressState.exercise = null;
  $('#progress-heading').textContent = 'Progress';
  $('#progress-sub').textContent = mode === 'CUT'
    ? 'Cut Phase: highlighting Relative Strength (× BW) and ≥95% E1RM retention.'
    : (mode === 'BULK'
      ? 'Bulk Phase: tracking peak E1RM gains and progressive overload.'
      : 'Track strength curves and estimated 1RMs over time.');
  $('#progress-back').classList.add('hidden');
  $('#progress-detail').classList.add('hidden');
  $('#progress-overview').classList.remove('hidden');

  const exList = S.exercises || [];
  const withSessions = exList.map((e) => {
    const sess = sessionsFor(e.name);
    const best = sess.reduce((m, s) => (s.e1rm && (!m || s.e1rm > m.e1rm) ? s : m), null);
    const bestRel = sess.reduce((m, s) => (s.rel && s.rel > m ? s.rel : m), 0);
    return { ...e, sessionsCount: sess.length, bestE1rm: best?.e1rm || 0, bestRel };
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
          <div class="big" style="${mode === 'CUT' && e.bestRel > 0 ? `color:${meta.color};` : ''}">
            ${mode === 'CUT' && e.bestRel > 0
              ? `${e.bestRel.toFixed(2)}<small>× BW</small>`
              : (e.bestE1rm ? `${e.bestE1rm}<small>kg 1RM</small>` : `${e.sessionsCount}<small>sessions</small>`)}
          </div>
          <div class="muted small" style="margin-top:4px;">${e.sessionsCount} session${e.sessionsCount === 1 ? '' : 's'} logged${mode === 'CUT' && e.bestE1rm ? ` • Peak ${e.bestE1rm} kg` : ''}</div>
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
  if (progressState.exercise !== ex.name) {
    progressState.exercise = ex.name;
    progressState.metric = null;
  }
  $('#progress-heading').textContent = ex.name;
  $('#progress-sub').textContent = `${ex.category} • ${ex.bodyweight ? 'Bodyweight (Total Load Calculated)' : 'Barbell / Dumbbell Lift'}`;

  const mode = TrainingModeClient.normalize(S.trainingMode);
  const sess = sessionsFor(ex.name);
  const bestE1rm = sess.reduce((m, s) => Math.max(m, s.e1rm || 0), 0);
  const bestW = sess.reduce((m, s) => Math.max(m, s.top || 0), 0);
  const bestRel = sess.reduce((m, s) => Math.max(m, s.rel || 0), 0);
  const latestE1rm = sess.length ? (sess[sess.length - 1].e1rm || 0) : 0;
  const totalVol = sess.reduce((m, s) => m + (s.volume || 0), 0);
  const retPct = bestE1rm > 0 && latestE1rm > 0 ? Math.min(115, Math.round((latestE1rm / bestE1rm) * 100)) : 0;

  $('#progress-stats').innerHTML = `
    <div class="stat"><div class="lbl">Best Est. 1RM</div><div class="val" style="color:var(--pr);">${bestE1rm ? `${bestE1rm} kg` : '–'}</div></div>
    <div class="stat"><div class="lbl">${mode === 'CUT' && bestRel > 0 ? 'Peak Rel. ×BW' : 'Top Weight'}</div><div class="val" style="${mode === 'CUT' && bestRel > 0 ? 'color:#60a5fa;' : ''}">${mode === 'CUT' && bestRel > 0 ? `${bestRel.toFixed(2)}× BW` : (bestW ? `${bestW} kg` : '–')}</div></div>
    <div class="stat"><div class="lbl">${mode === 'CUT' && bestE1rm > 0 ? 'Strength Held' : 'Total Sessions'}</div><div class="val" style="${mode === 'CUT' && bestE1rm > 0 ? `color:${retPct >= 95 ? 'var(--up)' : '#60a5fa'};` : ''}">${mode === 'CUT' && bestE1rm > 0 ? `${retPct}%` : sess.length}</div></div>
    <div class="stat"><div class="lbl">${ex.bodyweight ? 'Total Reps' : 'Total Volume'}</div><div class="val">${Math.round(totalVol)} ${ex.bodyweight ? 'reps' : 'kg'}</div></div>
  `;

  const mMap = metricsFor(ex.name);
  const availKeys = Object.keys(mMap).filter((k) => sess.some((s) => mMap[k].get(s) !== null));
  const metricKeys = availKeys.length ? availKeys : Object.keys(mMap);
  if (!progressState.metric || !metricKeys.includes(progressState.metric)) {
    const def = defaultMetric(ex.name, sess);
    progressState.metric = metricKeys.includes(def) ? def : metricKeys[0];
  }

  const metricSeg = $('#metric-seg');
  metricSeg.innerHTML = metricKeys.map((k) => `
    <button data-metric="${k}" class="${progressState.metric === k ? 'active' : ''}">${esc(mMap[k].label)}</button>
  `).join('');
  metricSeg.querySelectorAll('button').forEach((b) => {
    b.onclick = () => {
      progressState.metric = b.dataset.metric;
      renderProgressDetail(ex.name);
    };
  });

  $$('#range-seg button').forEach((b) => {
    b.classList.toggle('active', Number(b.dataset.range) === progressState.range);
    b.onclick = () => {
      progressState.range = Number(b.dataset.range);
      renderProgressDetail(ex.name);
    };
  });

  const todayDay = epochDay(today());
  const chartSessions = progressState.range === 0
    ? sess
    : sess.filter((s) => todayDay - epochDay(s.date) <= progressState.range);

  renderProgressChart(ex.name, chartSessions, progressState.metric, sess);

  const showRelCol = sess.some((s) => s.rel !== null);
  const revSess = [...sess].reverse();
  const tableRows = revSess.map((s, rIdx) => {
    const older = revSess[rIdx + 1] || null;
    const eDelta = older && s.e1rm && older.e1rm ? Math.round((s.e1rm - older.e1rm) * 10) / 10 : null;
    const heldDip = eDelta !== null && eDelta < 0 && older && TrainingModeClient.isExpectedCutDip(
      mode, s.e1rm, older.e1rm, s.rel || 0, older.rel || 0
    );
    const prBadge = (s.isE1rmPr || s.isWeightPr)
      ? '<span class="badge-rel-pr">PR</span>'
      : (s.isRelPr && mode === 'CUT' ? '<span class="badge-rel-pr">Rel PR</span>' : '');
    const eDeltaHtml = eDelta !== null && eDelta !== 0
      ? ` <span class="delta ${eDelta > 0 ? 'up' : (heldDip ? 'delta-retained' : 'down')}">${eDelta > 0 ? '+' : ''}${eDelta}k${heldDip ? ' (held)' : ''}</span>`
      : '';
    return `
      <tr>
        <td>${shortDate(s.date)} ${prBadge}</td>
        <td>${summarize(ex.name, s.sets)}</td>
        <td>${s.e1rm ? `${s.e1rm} kg` : '–'}${eDeltaHtml}</td>
        <td>${s.top !== null ? `${s.top} kg` : '–'}</td>
        ${showRelCol ? `<td>${s.rel !== null ? `${s.rel.toFixed(2)}×` : '–'}</td>` : ''}
        <td>${s.volume ? `${Math.round(s.volume)} ${ex.bodyweight ? 'reps' : 'kg'}` : '–'}</td>
      </tr>
    `;
  }).join('');

  $('#progress-table').innerHTML = `
    <thead><tr><th>Date</th><th>Sets Summary</th><th>Est 1RM</th><th>Top W</th>${showRelCol ? '<th>Rel. ×BW</th>' : ''}<th>${ex.bodyweight ? 'Reps' : 'Volume'}</th></tr></thead>
    <tbody>${tableRows || `<tr><td colspan="${showRelCol ? 6 : 5}" class="muted">No sessions recorded yet.</td></tr>`}</tbody>
  `;
}

function renderProgressChart(exerciseName, sessions, metricKey = 'e1rm', allSessions = sessions) {
  const canvas = $('#progress-chart');
  if (!canvas) return;
  if (progressChartInstance) { progressChartInstance.destroy(); progressChartInstance = null; }

  const mMap = metricsFor(exerciseName);
  const mDef = mMap[metricKey] || mMap.e1rm;

  // Determine all-time PR session IDs for this metric across full history
  const prIds = new Set();
  let runningMax = -Infinity;
  for (const s of allSessions) {
    const v = mDef.get(s);
    if (v !== null && v > runningMax) {
      runningMax = v;
      prIds.add(s.workoutId);
    }
  }

  const mode = TrainingModeClient.normalize(S.trainingMode);
  const pts = sessions.filter((s) => mDef.get(s) !== null);
  const labels = pts.map((s) => shortDate(s.date));
  const dataPoints = pts.map((s) => mDef.get(s));
  const pointColors = pts.map((s, i) => {
    if (prIds.has(s.workoutId)) return COLORS.pr;
    if (i > 0) {
      const prev = dataPoints[i - 1];
      if (dataPoints[i] > prev) return COLORS.up;
      if (dataPoints[i] < prev) {
        const expectedCutDip = metricKey !== 'rel' && TrainingModeClient.isExpectedCutDip(
          mode, dataPoints[i], prev, s.rel || 0, pts[i - 1].rel || 0
        );
        return expectedCutDip ? COLORS.cutBlue : COLORS.down;
      }
    }
    return COLORS.accent;
  });

  const isRel = metricKey === 'rel';

  progressChartInstance = new Chart(canvas, {
    type: 'line',
    data: {
      labels,
      datasets: [{
        label: `${mDef.label} (${mDef.unit})`,
        data: dataPoints,
        borderColor: COLORS.accent,
        backgroundColor: mode === 'CUT' ? 'rgba(96, 165, 250, 0.10)' : 'rgba(255, 122, 69, 0.1)',
        borderWidth: 2.5,
        pointBackgroundColor: pointColors,
        pointBorderColor: pointColors,
        pointRadius: pts.length > 60 ? 2.5 : 4,
        tension: 0.25,
        fill: true,
        segment: {
          borderColor: (ctx) => {
            const i0 = ctx.p0DataIndex;
            const i1 = ctx.p1DataIndex;
            const v0 = dataPoints[i0];
            const v1 = dataPoints[i1];
            if (v1 > v0) return COLORS.up;
            if (v1 < v0) {
              const heldDip = !isRel && TrainingModeClient.isExpectedCutDip(
                mode, v1, v0, pts[i1]?.rel || 0, pts[i0]?.rel || 0
              );
              return heldDip ? COLORS.cutBlue : COLORS.down;
            }
            return COLORS.accent;
          },
        },
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
            label: (ctx) => {
              const p = pts[ctx.dataIndex];
              if (isRel && p) {
                return `Rel. Strength: ${p.rel.toFixed(2)}× BW (E1RM ${p.e1rm} kg ÷ BW ${f1(p.bwKg)} kg)`;
              }
              return `${mDef.label}: ${ctx.parsed.y} ${mDef.unit}`;
            },
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
          ticks: {
            color: '#8b93a7',
            callback: (v) => (isRel ? `${Number(v).toFixed(2)}×` : v),
          },
        },
      },
    },
  });
}

// ---------------------------------------------------------------- BODY WEIGHT & STRENGTH DASHBOARD
const LB_PER_KG = 2.20462;
const BWD = {
  range: 90,
  unit: (typeof localStorage !== 'undefined' && localStorage.getItem('bwd-unit')) || 'kg',
  avg: true,
  mode: 'lift',
  lift: null,
  sex: (typeof localStorage !== 'undefined' && localStorage.getItem('bwd-sex')) || 'male',
  returnView: 'settings',
};
const bwdCharts = { weight: null, strength: null };

const f1 = (n) => (Math.round(n * 10) / 10).toFixed(1);
const f2 = (n) => (Math.round(n * 100) / 100).toFixed(2);
const bwdDisp = (kg) => (BWD.unit === 'lb' ? kg * LB_PER_KG : kg);

function epochDay(d) {
  const iso = toSortableIso(d);
  if (!iso) return 0;
  const [y, m, dd] = iso.split('-').map(Number);
  return Math.floor(Date.UTC(y, m - 1, dd) / 86400000);
}

/** Chronological readings (one per date) with a trailing 7-calendar-day moving average. */
function bwdSeries() {
  const byIso = new Map();
  for (const b of S.bodyWeights || []) {
    const iso = toSortableIso(b.date);
    if (iso && Number(b.weightKg) > 0) byIso.set(iso, b);
  }
  const sorted = [...byIso.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([, b]) => b);
  const days = sorted.map((b) => epochDay(b.date));
  return sorted.map((b, i) => {
    let sum = 0;
    let n = 0;
    for (let j = i; j >= 0 && days[i] - days[j] <= 6; j--) { sum += Number(sorted[j].weightKg); n++; }
    return { date: b.date, iso: toSortableIso(b.date), day: days[i], kg: Number(b.weightKg), avg7: n ? sum / n : Number(b.weightKg), source: b.source || 'manual' };
  });
}

const SQUAT_NAMES = new Set(['squat', 'back squat', 'low bar squat', 'high bar squat', 'barbell squat', 'barbell back squat']);
const BENCH_NAMES = new Set(['bench press', 'flat bench press', 'barbell bench press', 'flat barbell bench press']);
const DEADLIFT_NAMES = new Set(['deadlift', 'conventional deadlift', 'sumo deadlift', 'barbell deadlift']);

function classifyLift(name) {
  const n = String(name || '').trim().toLowerCase();
  if (SQUAT_NAMES.has(n)) return 'squat';
  if (BENCH_NAMES.has(n)) return 'bench';
  if (DEADLIFT_NAMES.has(n)) return 'deadlift';
  return null;
}

const DOTS_MALE = [-307.75076, 24.0900756, -0.1918759221, 0.0007391293, -0.000001093];
const DOTS_FEMALE = [-57.96288, 13.6175032, -0.1126655495, 0.0005158568, -0.0000010706];

function computeDots(totalKg, bwKg, male) {
  if (!(totalKg > 0) || !(bwKg > 0)) return 0;
  const bw = Math.min(210, Math.max(40, bwKg));
  const c = male ? DOTS_MALE : DOTS_FEMALE;
  const denom = c[0] + c[1] * bw + c[2] * bw ** 2 + c[3] * bw ** 3 + c[4] * bw ** 4;
  return denom > 0 ? (totalKg * 500) / denom : 0;
}

/** DOTS timeline: best E1RM per big lift over the trailing 90 days, paired with body weight on that date. */
function buildDotsTimeline(male) {
  if (!(S.bodyWeights || []).length) return [];
  const wById = new Map(S.workouts.map((w) => [w.id, w]));
  const obs = [];
  for (const s of S.sets) {
    const lift = classifyLift(s.exercise);
    if (!lift || !s.reps || s.reps <= 0 || !s.weight || s.weight <= 0) continue;
    const date = (wById.get(s.workoutId) || {}).date || s.date;
    const iso = toSortableIso(date);
    if (!iso) continue;
    obs.push({ iso, day: epochDay(date), lift, e: epley(s.weight, s.reps) });
  }
  const dates = [...new Map(obs.map((o) => [o.iso, o.day])).entries()].sort((a, b) => a[0].localeCompare(b[0]));
  const out = [];
  for (const [iso, day] of dates) {
    const best = { squat: 0, bench: 0, deadlift: 0 };
    for (const o of obs) if (o.day <= day && day - o.day <= 90 && o.e > best[o.lift]) best[o.lift] = o.e;
    if (!best.squat || !best.bench || !best.deadlift) continue;
    const bw = BodyWeightUtils.weightOn(iso);
    const total = best.squat + best.bench + best.deadlift;
    out.push({ iso, day, ...best, total, bw, dots: computeDots(total, bw, male) });
  }
  return out;
}

/** Relative strength (E1RM / body weight on session date) for any exercise. */
function relativeSeries(name) {
  if (!(S.bodyWeights || []).length) return [];
  return sessionsFor(name)
    .filter((s) => s.e1rm)
    .map((s) => {
      const bw = BodyWeightUtils.weightOn(s.date);
      return { date: s.date, day: epochDay(s.date), e1rm: s.e1rm, bw, rel: bw > 0 ? s.e1rm / bw : 0 };
    })
    .filter((p) => p.rel > 0);
}

function bwdLineChart(canvas, labels, datasets, yFmt, tooltipLabel) {
  return new Chart(canvas, {
    type: 'line',
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: datasets.length > 1, labels: { color: '#8b93a7' } },
        tooltip: {
          backgroundColor: '#171a21', borderColor: '#2a2f3a', borderWidth: 1, titleColor: '#e8eaf0', bodyColor: '#ffd166',
          callbacks: { label: tooltipLabel },
        },
      },
      scales: {
        x: { grid: { color: 'rgba(42, 47, 58, 0.4)' }, ticks: { color: '#8b93a7', maxTicksLimit: 8 } },
        y: { grid: { color: 'rgba(42, 47, 58, 0.4)' }, ticks: { color: '#8b93a7', callback: yFmt } },
      },
    },
  });
}

function renderBodyWeightDashboard() {
  const series = bwdSeries();
  const todayDay = epochDay(today());
  const inRange = (day) => BWD.range === 0 || todayDay - day <= BWD.range;
  const visible = series.filter((p) => inRange(p.day));
  const unit = BWD.unit;

  // controls state
  $$('#bwd-unit-seg button').forEach((b) => b.classList.toggle('active', b.dataset.unit === BWD.unit));
  $$('#bwd-range-seg button').forEach((b) => b.classList.toggle('active', Number(b.dataset.range) === BWD.range));
  $$('#bwd-mode-seg button').forEach((b) => b.classList.toggle('active', b.dataset.mode === BWD.mode));
  $$('#bwd-sex-seg button').forEach((b) => b.classList.toggle('active', b.dataset.sex === BWD.sex));
  $('#bwd-avg').checked = BWD.avg;
  $('#bwd-lift-controls').classList.toggle('hidden', BWD.mode !== 'lift');
  $('#bwd-dots-controls').classList.toggle('hidden', BWD.mode !== 'dots');
  if (!$('#bwd-input-date').value) $('#bwd-input-date').value = today();
  const kgInp = $('#bwd-input-kg');
  if (kgInp) {
    kgInp.previousSibling.textContent = `Weight (${unit})`;
    kgInp.min = unit === 'lb' ? '66' : '30';
    kgInp.max = unit === 'lb' ? '660' : '300';
    kgInp.placeholder = unit === 'lb' ? 'e.g. 182.0' : 'e.g. 82.5';
  }

  const dots = buildDotsTimeline(BWD.sex === 'male');
  const peakDots = dots.reduce((m, d) => (!m || d.dots > m.dots ? d : m), null);

  // KPIs
  const mode = TrainingModeClient.normalize(S.trainingMode);
  const last = series[series.length - 1];
  const first = visible[0];
  const lastVis = visible[visible.length - 1];
  const delta = first && lastVis && visible.length >= 2 ? lastVis.kg - first.kg : null;
  const spanDays = first && lastVis ? lastVis.day - first.day : 0;
  const rate = delta !== null && spanDays >= 7 ? delta / (spanDays / 7) : null;
  const pace = rate !== null ? TrainingModeClient.classifyBodyWeightPace(mode, rate) : null;
  const changeColor = pace
    ? (pace.isOnTarget ? 'var(--up)' : (mode === 'CUT' && (delta || 0) <= 0 ? '#60a5fa' : 'var(--accent-2)'))
    : 'var(--text)';
  const sign = (n) => (n > 0 ? '+' : '');
  $('#bwd-kpis').innerHTML = `
    <div class="stat"><div class="lbl">Current</div><div class="val" style="color:var(--accent);">${last ? `${f1(bwdDisp(last.kg))} ${unit}` : '–'}</div><div class="sub">${last ? `7-day avg ${f1(bwdDisp(last.avg7))} ${unit}` : 'No readings yet'}</div></div>
    <div class="stat"><div class="lbl">Change</div><div class="val" style="color:${changeColor};">${delta !== null ? `${sign(delta)}${f1(bwdDisp(delta))} ${unit}` : '–'}</div><div class="sub">${rate !== null ? `${sign(rate)}${f2(bwdDisp(rate))} ${unit}/wk${pace ? ` • ${pace.status}` : ''}` : 'Needs 7+ days of data'}</div></div>
    <div class="stat"><div class="lbl">${peakDots ? 'Peak DOTS' : 'DOTS'}</div><div class="val" style="color:var(--pr);">${peakDots ? f1(peakDots.dots) : '–'}</div><div class="sub">${peakDots ? `Total ${f1(bwdDisp(peakDots.total))} ${unit}` : 'Needs squat + bench + deadlift'}</div></div>
  `;

  // Body weight chart
  if (bwdCharts.weight) { bwdCharts.weight.destroy(); bwdCharts.weight = null; }
  const readout = $('#bwd-weight-readout');
  if (!visible.length) {
    readout.textContent = series.length ? 'No readings in this range.' : 'No body weight readings yet. Log one below.';
  } else {
    readout.textContent = `${visible.length} reading${visible.length === 1 ? '' : 's'} • ${shortDate(visible[0].date)} → ${shortDate(lastVis.date)}`;
    const datasets = [{
      label: `Weight (${unit})`,
      data: visible.map((p) => +bwdDisp(p.kg).toFixed(2)),
      borderColor: COLORS.accent, backgroundColor: 'rgba(255, 122, 69, 0.1)',
      borderWidth: 2, pointRadius: visible.length > 60 ? 0 : 3, pointBackgroundColor: COLORS.accent, tension: 0.2, fill: true,
    }];
    if (BWD.avg) {
      datasets.push({
        label: '7-day average', data: visible.map((p) => +bwdDisp(p.avg7).toFixed(2)),
        borderColor: COLORS.pr, borderWidth: 3, pointRadius: 0, tension: 0.3, fill: false,
      });
    }
    bwdCharts.weight = bwdLineChart($('#bwd-weight-chart'), visible.map((p) => shortDate(p.date)), datasets,
      (v) => `${f1(v)}`, (ctx) => `${ctx.dataset.label}: ${f1(ctx.parsed.y)}`);
  }

  // Strength chart
  if (bwdCharts.strength) { bwdCharts.strength.destroy(); bwdCharts.strength = null; }
  const sRead = $('#bwd-strength-readout');
  if (BWD.mode === 'lift') {
    const names = S.exercises.map((e) => e.name);
    const withData = names.filter((n) => (idx.byE.get(n.toLowerCase()) || []).length);
    const preferred = withData.find((n) => classifyLift(n) === 'bench') || withData.find((n) => classifyLift(n)) || withData[0] || null;
    const name = BWD.lift || preferred;
    const liftInp = $('#bwd-lift-input');
    if (liftInp && document.activeElement !== liftInp) liftInp.value = name || '';
    if (!(S.bodyWeights || []).length) {
      sRead.textContent = 'Log your body weight to see strength relative to body weight.';
    } else if (!name) {
      sRead.textContent = 'Log some workouts to see relative strength.';
    } else {
      const pts = relativeSeries(name).filter((p) => inRange(p.day));
      if (!pts.length) {
        sRead.textContent = `No sessions for ${name} in this range.`;
      } else {
        const lp = pts[pts.length - 1];
        sRead.textContent = `${name} • latest ${shortDate(lp.date)}: E1RM ${f1(bwdDisp(lp.e1rm))} ${unit} ÷ BW ${f1(bwdDisp(lp.bw))} ${unit} = ${f2(lp.rel)}× BW`;
        bwdCharts.strength = bwdLineChart($('#bwd-strength-chart'), pts.map((p) => shortDate(p.date)), [{
          label: '× Body weight', data: pts.map((p) => +p.rel.toFixed(3)),
          borderColor: COLORS.accent, backgroundColor: 'rgba(255, 122, 69, 0.1)', borderWidth: 2.5, pointRadius: 3, pointBackgroundColor: COLORS.accent, tension: 0.25, fill: true,
        }], (v) => `${f2(v)}×`, (ctx) => {
          const p = pts[ctx.dataIndex];
          return `${f2(p.rel)}× BW (E1RM ${f1(bwdDisp(p.e1rm))} ${unit} @ ${f1(bwdDisp(p.bw))} ${unit})`;
        });
      }
    }
  } else {
    const pts = dots.filter((p) => inRange(p.day));
    if (!(S.bodyWeights || []).length) {
      sRead.textContent = 'Log your body weight to calculate DOTS.';
    } else if (!pts.length) {
      sRead.textContent = 'Log Squat, Bench Press and Deadlift (within 90 days of each other) to calculate DOTS.';
    } else {
      const lp = pts[pts.length - 1];
      sRead.textContent = `${shortDate(lp.iso)} • DOTS ${f1(lp.dots)} • Squat ${f1(bwdDisp(lp.squat))} + Bench ${f1(bwdDisp(lp.bench))} + Deadlift ${f1(bwdDisp(lp.deadlift))} = ${f1(bwdDisp(lp.total))} ${unit} @ BW ${f1(bwdDisp(lp.bw))} ${unit} (est. 1RMs, best of last 90 days)`;
      bwdCharts.strength = bwdLineChart($('#bwd-strength-chart'), pts.map((p) => shortDate(p.iso)), [{
        label: 'DOTS', data: pts.map((p) => +p.dots.toFixed(2)),
        borderColor: COLORS.pr, backgroundColor: 'rgba(255, 209, 102, 0.1)', borderWidth: 2.5, pointRadius: 3, pointBackgroundColor: COLORS.pr, tension: 0.25, fill: true,
      }], (v) => f1(v), (ctx) => {
        const p = pts[ctx.dataIndex];
        return `DOTS ${f1(p.dots)} (total ${f1(bwdDisp(p.total))} ${unit} @ ${f1(bwdDisp(p.bw))} ${unit})`;
      });
    }
  }

  // History
  const host = $('#bwd-history');
  host.innerHTML = [...series].reverse().map((p) => `
    <div class="bw-entry-row">
      <span class="bw-entry-date">${shortDate(p.date)}</span>
      <span class="bw-entry-weight">${f1(bwdDisp(p.kg))} ${unit} <span class="muted small">${esc(p.source)}</span></span>
      <button class="icon del-bwd-btn" data-date="${esc(p.date)}" title="Delete reading">✕</button>
    </div>
  `).join('') || '<div class="muted small">No body weight readings logged yet.</div>';
  host.querySelectorAll('.del-bwd-btn').forEach((btn) => {
    btn.onclick = async () => {
      if (!confirm('Delete this reading (also removed from Google Sheets)?')) return;
      await busy(async () => {
        setState(await window.api.deleteBodyWeight(btn.dataset.date));
        toast('Reading deleted');
      });
    };
  });
}

function initBodyWeightModule() {
  const open = (from) => { BWD.returnView = from; showView('bodyweight'); };
  $('#bw-open-dashboard').addEventListener('click', () => open('settings'));
  $('#progress-open-bw').addEventListener('click', () => open('progress'));
  $('#bwd-back').addEventListener('click', () => showView(BWD.returnView || 'settings'));

  $$('#bwd-unit-seg button').forEach((b) => b.addEventListener('click', () => {
    BWD.unit = b.dataset.unit;
    try { localStorage.setItem('bwd-unit', BWD.unit); } catch { /* ignore */ }
    renderBodyWeightDashboard();
  }));
  $$('#bwd-range-seg button').forEach((b) => b.addEventListener('click', () => { BWD.range = Number(b.dataset.range); renderBodyWeightDashboard(); }));
  $$('#bwd-mode-seg button').forEach((b) => b.addEventListener('click', () => { BWD.mode = b.dataset.mode; renderBodyWeightDashboard(); }));
  $$('#bwd-sex-seg button').forEach((b) => b.addEventListener('click', () => {
    BWD.sex = b.dataset.sex;
    try { localStorage.setItem('bwd-sex', BWD.sex); } catch { /* ignore */ }
    renderBodyWeightDashboard();
  }));
  $('#bwd-avg').addEventListener('change', (e) => { BWD.avg = e.target.checked; renderBodyWeightDashboard(); });

  const onLiftPick = (e) => {
    const val = e.target.value.trim();
    const match = S.exercises.find((x) => x.name.toLowerCase() === val.toLowerCase());
    if (match) {
      BWD.lift = match.name;
      renderBodyWeightDashboard();
    }
  };
  $('#bwd-lift-input').addEventListener('input', onLiftPick);
  $('#bwd-lift-input').addEventListener('change', onLiftPick);

  $('#bwd-log-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    const dVal = $('#bwd-input-date').value;
    const kgVal = $('#bwd-input-kg').value;
    if (!dVal || !kgVal) return;
    const kg = BWD.unit === 'lb' ? Number(kgVal) / LB_PER_KG : Number(kgVal);
    await busy(async () => {
      setState(await window.api.addBodyWeight({ date: fmtDdMonYyyy(dVal), weightKg: Math.round(kg * 10) / 10, source: 'manual' }));
      $('#bwd-input-kg').value = '';
      toast('Body weight logged', 'success');
    });
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

  // Training mode pill in sidebar
  const modePill = $('#sidebar-mode-badge');
  if (modePill) {
    const meta = TrainingModeClient.meta(S.trainingMode);
    modePill.className = `mode-pill ${meta.cssClass}`;
    modePill.textContent = `${meta.shortLabel} mode`;
  }

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
    else if (vName === 'progress') {
      if (progressState.exercise) renderProgressDetail(progressState.exercise);
      else renderProgressOverview();
    } else if (vName === 'goals') renderGoals();
    else if (vName === 'exercises') renderExercisesCatalog();
    else if (vName === 'bodyweight') renderBodyWeightDashboard();
    else if (vName === 'settings') renderSettings();
  }
}

// ---------------------------------------------------------------- IN-ENGINE 14-DAY SUMMARY & SAVED LOG
function summaryScoreColor(score) {
  if (score >= 80) return 'var(--up)';
  if (score >= 60) return 'var(--pr)';
  if (score >= 40) return 'var(--accent)';
  return 'var(--down)';
}

function fmtTonnage(kg) {
  const n = Number(kg || 0);
  return Math.abs(n) >= 1000 ? `${(n / 1000).toFixed(1)}t` : `${fmt(n)} kg`;
}

async function open14DaySummaryModal(initialTab = 'live') {
  let activeTab = initialTab;
  let expandedSavedId = null;
  let summary = await window.api.get14DaySummary({ isMale: BWD.sex === 'male' });

  const { body } = openCustomModal({
    title: '14-Day Training Summary',
    large: true,
    cancelText: 'Close',
    html: `
      <div class="summary-tabs" id="m-sum-tabs">
        <button type="button" class="summary-tab-btn" data-tab="live">Live Summary</button>
        <button type="button" class="summary-tab-btn" data-tab="log">Saved Log (<span id="m-sum-count">${(S.savedSummaries || []).length}</span>)</button>
      </div>
      <div id="m-sum-body"></div>
    `,
  });

  const tabsEl = body.querySelector('#m-sum-tabs');
  const countEl = body.querySelector('#m-sum-count');
  const contentEl = body.querySelector('#m-sum-body');

  const syncTabs = () => {
    countEl.textContent = String((S.savedSummaries || []).length);
    tabsEl.querySelectorAll('.summary-tab-btn').forEach((b) => {
      b.classList.toggle('active', b.dataset.tab === activeTab);
    });
  };

  function renderLiveTab() {
    const sumMode = TrainingModeClient.normalize(summary.trainingMode || S.trainingMode);
    const sumMeta = TrainingModeClient.meta(sumMode);
    const color = summaryScoreColor(summary.overallScore);
    const volStr = fmtTonnage(summary.totalVolumeKg);
    const volSub = summary.volumeDeltaPct !== null && summary.volumeDeltaPct !== undefined
      ? `${summary.volumeDeltaPct > 0 ? '+' : ''}${summary.volumeDeltaPct}% vs prior 14d`
      : (summary.priorVolumeKg > 0 ? `Prior: ${fmtTonnage(summary.priorVolumeKg)}` : 'Baseline block');

    const sessSub = summary.programAdherencePct !== null && summary.programAdherencePct !== undefined
      ? `${summary.programAdherencePct}% of ${summary.programName || 'program'}`
      : `${summary.sessionsPerWeek}/wk • ${summary.activeDaysCount} active days`;

    const setsSub = summary.failedSetsCount > 0
      ? `${summary.avgSetsPerWorkout}/sess • ${summary.failedSetsCount} failed excl.`
      : `${summary.avgSetsPerWorkout}/sess • ${summary.avgRepsPerSet} reps/set`;

    const bwVal = summary.hasBodyWeightData && summary.endBodyWeightKg !== null
      ? `${summary.endBodyWeightKg} kg`
      : '—';
    const bwParts = [];
    if (summary.bodyWeightDeltaKg !== null && summary.bodyWeightDeltaKg !== undefined) {
      bwParts.push(`${summary.bodyWeightDeltaKg > 0 ? '+' : ''}${summary.bodyWeightDeltaKg} kg`);
    } else {
      bwParts.push(summary.hasBodyWeightData ? 'Steady' : 'No readings');
    }
    if (summary.bwPaceAssessment) {
      bwParts.push(summary.bwPaceAssessment);
    } else if (summary.dotsScore !== null && summary.dotsScore !== undefined) {
      bwParts.push(`DOTS ${summary.dotsScore}`);
    }

    const strengthLabel = sumMode === 'CUT' ? 'Retention & Rel.' : (sumMode === 'BULK' ? 'Strength Gains' : 'Strength');
    const subScoresHtml = [
      ['Volume', summary.volumeScore],
      [strengthLabel, summary.strengthScore],
      ['Cadence', summary.consistencyScore],
      ['Balance', summary.balanceScore],
    ].map(([lbl, val]) => {
      const c = summaryScoreColor(val);
      return `
        <div class="summary-subscore-card">
          <div class="summary-subscore-row"><span class="muted small">${lbl}</span><b style="color:${c};font-size:12.5px;">${val}</b></div>
          <div class="summary-bar-track"><div class="summary-bar-fill" style="width:${val}%;background:${c};"></div></div>
        </div>
      `;
    }).join('');

    const musclesHtml = (summary.muscleItems || []).map((m) => {
      const pct = Math.min(100, Math.round((m.workingSets14d / Math.max(1, m.target14dSets)) * 100));
      const barCol = m.workingSets14d >= m.target14dSets * 0.85
        ? 'var(--up)'
        : (m.workingSets14d >= m.target14dSets * 0.45 ? 'var(--accent)' : 'var(--muted)');
      return `
        <div class="summary-muscle-row">
          <div class="summary-muscle-head">
            <span>${esc(m.category)}</span>
            <span class="muted">${m.workingSets14d}/${m.target14dSets} sets (${fmtTonnage(m.volumeKg14d)})</span>
          </div>
          <div class="summary-bar-track"><div class="summary-bar-fill" style="width:${pct}%;background:${barCol};"></div></div>
        </div>
      `;
    }).join('');

    const liftsRows = (summary.lifts || []).map((item) => {
      const delta = item.e1rmDeltaKg !== null && item.e1rmDeltaKg !== undefined
        ? item.e1rmDeltaKg
        : item.withinPeriodE1rmDeltaKg;
      const isCutHeldDip = sumMode === 'CUT' && delta !== null && delta < -0.5 && (item.retentionPct === null || item.retentionPct === undefined || item.retentionPct >= TrainingModeClient.CUT_RETENTION_FLOOR_PCT);
      let deltaText = `${item.workingSets14d} sets`;
      let deltaCls = 'flat';
      if (item.isAllTimePr) {
        deltaText = delta !== null && delta > 0 ? `PR +${delta} kg` : 'All-time PR';
        deltaCls = 'up';
      } else if (item.isAllTimeRelPr) {
        deltaText = 'REL PR';
        deltaCls = 'up';
      } else if (isCutHeldDip && item.retentionPct) {
        deltaText = `${Math.round(item.retentionPct)}% held`;
        deltaCls = 'delta-retained';
      } else if (delta !== null && delta !== undefined) {
        deltaText = `${delta > 0 ? '+' : ''}${delta} kg`;
        deltaCls = delta > 0.5 ? 'up' : (delta < -0.5 ? 'down' : 'flat');
      }
      const relDeltaHint = item.relBwDelta && Math.abs(item.relBwDelta) >= 0.01
        ? ` <span class="muted small">(${item.relBwDelta > 0 ? '+' : ''}${item.relBwDelta.toFixed(2)}×)</span>`
        : '';
      return `
        <tr>
          <td><b>${esc(item.exerciseName)}</b></td>
          <td>${esc(item.bestSet14d)}</td>
          <td>${item.bestE1rm14dKg} kg</td>
          <td>${item.relativeStrengthBw !== null && item.relativeStrengthBw !== undefined ? `${item.relativeStrengthBw.toFixed(2)}×${relDeltaHint}` : '—'}</td>
          <td><span class="delta ${deltaCls}" style="${(item.isAllTimePr || item.isAllTimeRelPr) ? 'color:var(--pr);' : ''}">${esc(deltaText)}</span></td>
        </tr>
      `;
    }).join('');

    const goalsHtml = (summary.goalStatuses || []).length ? `
      <div class="summary-section-card">
        <h4>Goal Alignment</h4>
        <table class="table">
          <thead><tr><th>Lift</th><th>Target</th><th>Progress</th><th>14d Status</th></tr></thead>
          <tbody>
            ${summary.goalStatuses.map((g) => `
              <tr>
                <td><b>${esc(g.exerciseName)}</b></td>
                <td>${esc(g.targetSummary)}</td>
                <td>${g.progressPct}%</td>
                <td><span class="delta ${g.achieved || g.trainedIn14d ? 'up' : 'flat'}">${g.achieved ? 'Achieved' : (g.trainedIn14d ? 'Active' : 'Untrained')}</span></td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    ` : '';

    contentEl.innerHTML = `
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
        <div class="muted small">Window: <b>${esc(summary.startDate)} – ${esc(summary.endDate)}</b></div>
        <span class="mode-pill ${sumMeta.cssClass}">${esc(sumMeta.shortLabel)} Mode</span>
      </div>

      <div class="summary-save-bar">
        <input type="text" id="m-sum-title" placeholder="Label this 14-day block (e.g. Week 4 Accumulation)" style="flex:1;" />
        <input type="text" id="m-sum-notes" placeholder="Note (optional)" style="flex:0.8;" />
        <button type="button" id="m-sum-save" class="primary compact">Save Entry</button>
      </div>

      <div class="summary-score-card">
        <div>
          <div class="muted small">Training score</div>
          <div style="margin-top:6px;"><span class="tag" style="color:${color};border-color:${color};font-weight:600;">${esc(summary.tierLabel)}</span></div>
        </div>
        <div class="summary-score-num" style="color:${color};">${summary.overallScore}<small>/100</small></div>
      </div>

      <div class="summary-subscores">${subScoresHtml}</div>

      <div class="stats" style="grid-template-columns:repeat(4,1fr);margin-bottom:12px;">
        <div class="stat"><div class="lbl">Sessions</div><div class="val">${summary.workoutsCount}</div><div class="sub">${esc(sessSub)}</div></div>
        <div class="stat"><div class="lbl">External Volume</div><div class="val">${esc(volStr)}</div><div class="sub">${esc(volSub)}</div></div>
        <div class="stat"><div class="lbl">Working Sets</div><div class="val">${summary.workingSetsCount}</div><div class="sub">${esc(setsSub)}</div></div>
        <div class="stat"><div class="lbl">Body Weight</div><div class="val">${esc(bwVal)}</div><div class="sub">${esc(bwParts.join(' • '))}</div></div>
      </div>

      <div class="summary-section-card">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;">
          <h4 style="margin:0;">Muscle Volume (14d vs ${esc(sumMeta.shortLabel)} Target)</h4>
          <span class="muted small">${summary.pushToPullRatio !== null ? `Push:Pull ${summary.pushToPullRatio}:1 • ` : ''}Upper/Lower ${summary.upperSharePct}%/${100 - summary.upperSharePct}%</span>
        </div>
        ${musclesHtml}
        ${summary.workingSetsCount > 0 ? `<div class="muted small" style="margin-top:8px;">Rep zones: ${summary.heavySets} heavy (1-5) • ${summary.strengthHypSets} strength-hyp (6-8) • ${summary.hypertrophySets} hypertrophy (9-12) • ${summary.enduranceSets} endurance (13+)</div>` : ''}
      </div>

      ${liftsRows ? `
        <div class="summary-section-card">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;">
            <h4 style="margin:0;">${sumMode === 'CUT' ? 'Retention & Strength' : 'Strength Progression'}</h4>
            <span class="muted small">${sumMode === 'CUT'
              ? `${summary.prsCount} PR • ${summary.relPrsCount || 0} rel PR • ${(summary.holdingCount || 0) + (summary.retainedCount || 0)} held`
              : `${summary.prsCount} PR • ${summary.progressingCount} up • ${summary.holdingCount} steady • ${summary.regressingCount} down`}</span>
          </div>
          <table class="table">
            <thead><tr><th>Exercise</th><th>Best Set</th><th>Est. 1RM</th><th>Rel BW</th><th>14d Change</th></tr></thead>
            <tbody>${liftsRows}</tbody>
          </table>
        </div>
      ` : ''}

      ${goalsHtml}

      <div class="summary-section-card">
        <h4>Key Findings</h4>
        <ul class="summary-list">${(summary.highlights || []).map((h) => `<li>${esc(h)}</li>`).join('')}</ul>
      </div>

      <div class="summary-section-card">
        <h4>Next-Block Focus</h4>
        <ul class="summary-list">${(summary.recommendations || []).map((r) => `<li>${esc(r)}</li>`).join('')}</ul>
      </div>
    `;

    contentEl.querySelector('#m-sum-save').addEventListener('click', async () => {
      const title = contentEl.querySelector('#m-sum-title').value;
      const notes = contentEl.querySelector('#m-sum-notes').value;
      const res = await window.api.saveSummaryEntry({ title, notes, isMale: BWD.sex === 'male' });
      setState(res.state);
      expandedSavedId = res.entry.id;
      syncTabs();
      toast('Saved 14-day summary entry', 'success');
      activeTab = 'log';
      syncTabs();
      renderLogTab();
    });
  }

  function renderLogTab() {
    const entries = S.savedSummaries || [];
    if (!entries.length) {
      contentEl.innerHTML = `
        <div class="empty">
          No saved summaries yet. Save a 14-day snapshot from the Live Summary tab to track block-over-block progress.
        </div>
      `;
      return;
    }

    let trendHtml = '';
    if (entries.length >= 2) {
      const newest = entries[0];
      const oldest = entries[entries.length - 1];
      const sDiff = newest.overallScore - oldest.overallScore;
      const vDiff = newest.totalVolumeKg - oldest.totalVolumeKg;
      const newestCut = TrainingModeClient.normalize(newest.trainingMode) === 'CUT';
      const volColor = vDiff >= 0 ? 'var(--up)' : (newestCut ? '#60a5fa' : 'var(--down)');
      const bwDiff = (newest.endBodyWeightKg !== null && oldest.endBodyWeightKg !== null)
        ? round(newest.endBodyWeightKg - oldest.endBodyWeightKg, 0.1)
        : null;
      trendHtml = `
        <div class="stats" style="grid-template-columns:repeat(3,1fr);margin-bottom:12px;">
          <div class="stat"><div class="lbl">Score Trend</div><div class="val" style="color:${sDiff >= 0 ? 'var(--up)' : 'var(--down)'};">${sDiff > 0 ? '+' : ''}${sDiff} pts</div><div class="sub">Across ${entries.length} saved blocks</div></div>
          <div class="stat"><div class="lbl">Volume Trend</div><div class="val" style="color:${volColor};">${vDiff > 0 ? '+' : ''}${fmtTonnage(vDiff)}</div><div class="sub">Latest vs earliest</div></div>
          <div class="stat"><div class="lbl">Weight Trend</div><div class="val">${bwDiff !== null ? `${bwDiff > 0 ? '+' : ''}${bwDiff} kg` : '—'}</div><div class="sub">Latest vs earliest</div></div>
        </div>
      `;
    }

    const cardsHtml = entries.map((entry, idx) => {
      const prev = entries[idx + 1];
      const scoreDelta = prev ? entry.overallScore - prev.overallScore : 0;
      const isExpanded = expandedSavedId === entry.id;
      const color = summaryScoreColor(entry.overallScore);
      const entryMeta = TrainingModeClient.meta(entry.trainingMode || 'MAINGAIN');
      return `
        <div class="saved-summary-card" data-entry-id="${esc(entry.id)}">
          <div class="saved-summary-head">
            <div>
              <div style="display:flex;align-items:center;gap:8px;font-weight:600;font-size:14px;">
                <span>${esc(entry.title || `${entry.startDate} – ${entry.endDate}`)}</span>
                <span class="mode-pill ${entryMeta.cssClass}" style="padding:2px 7px;font-size:10px;">${esc(entryMeta.shortLabel)}</span>
              </div>
              <div class="muted small">${esc(entry.startDate)} – ${esc(entry.endDate)} • Saved ${esc(entry.savedAt)}</div>
              <div class="muted small" style="margin-top:4px;">${entry.workoutsCount} sessions • ${entry.workingSetsCount} sets • ${fmtTonnage(entry.totalVolumeKg)}${entry.endBodyWeightKg !== null ? ` • ${entry.endBodyWeightKg} kg BW` : ''}</div>
              ${entry.notes ? `<div class="small" style="color:var(--accent-2);margin-top:4px;">${esc(entry.notes)}</div>` : ''}
            </div>
            <div style="text-align:right;">
              <div style="font-size:18px;font-weight:700;color:${color};font-variant-numeric:tabular-nums;">${entry.overallScore}/100</div>
              ${prev && scoreDelta !== 0 ? `<div class="delta ${scoreDelta > 0 ? 'up' : 'down'}">${scoreDelta > 0 ? '+' : ''}${scoreDelta} vs prev</div>` : `<div class="muted small">${esc(entry.tierLabel)}</div>`}
            </div>
          </div>
          ${isExpanded ? `
            <div class="saved-summary-body">
              <div class="muted small" style="display:flex;gap:16px;margin-bottom:8px;">
                <span>Vol: <b>${entry.volumeScore}</b></span>
                <span>Str: <b>${entry.strengthScore}</b></span>
                <span>Cad: <b>${entry.consistencyScore}</b></span>
                <span>Bal: <b>${entry.balanceScore}</b></span>
                <span>PRs: <b>${entry.prsCount}</b></span>
              </div>
              ${(entry.topLifts || []).length ? `
                <table class="table" style="margin-bottom:8px;">
                  <thead><tr><th>Exercise</th><th>Best Set</th><th>Est. 1RM</th></tr></thead>
                  <tbody>
                    ${entry.topLifts.slice(0, 6).map((l) => `<tr><td>${esc(l.exerciseName)}</td><td>${esc(l.bestSet14d)}</td><td>${l.bestE1rm14dKg} kg</td></tr>`).join('')}
                  </tbody>
                </table>
              ` : ''}
              ${(entry.highlights || []).length ? `<ul class="summary-list" style="margin-bottom:8px;">${entry.highlights.map((h) => `<li>${esc(h)}</li>`).join('')}</ul>` : ''}
              <div style="display:flex;justify-content:flex-end;">
                <button type="button" class="ghost danger compact m-del-summary" data-id="${esc(entry.id)}">Delete Entry</button>
              </div>
            </div>
          ` : ''}
        </div>
      `;
    }).join('');

    contentEl.innerHTML = `${trendHtml}${cardsHtml}`;

    contentEl.querySelectorAll('.saved-summary-head').forEach((head) => {
      head.addEventListener('click', () => {
        const id = head.closest('.saved-summary-card').dataset.entryId;
        expandedSavedId = expandedSavedId === id ? null : id;
        renderLogTab();
      });
    });

    contentEl.querySelectorAll('.m-del-summary').forEach((btn) => {
      btn.addEventListener('click', async (e) => {
        e.stopPropagation();
        const state = await window.api.deleteSummaryEntry(btn.dataset.id);
        setState(state);
        syncTabs();
        renderLogTab();
        toast('Saved summary deleted');
      });
    });
  }

  const renderTab = () => {
    syncTabs();
    if (activeTab === 'live') renderLiveTab();
    else renderLogTab();
  };

  tabsEl.querySelectorAll('.summary-tab-btn').forEach((btn) => {
    btn.addEventListener('click', () => {
      activeTab = btn.dataset.tab;
      renderTab();
    });
  });

  renderTab();
}

function initSummaryModule() {
  $('#progress-summary-btn')?.addEventListener('click', () => open14DaySummaryModal('live'));
  $('#history-summary-btn')?.addEventListener('click', () => open14DaySummaryModal('live'));
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
  $('#progress-open-rel')?.addEventListener('click', () => {
    if (progressState.exercise) openRelativeStrengthModal(progressState.exercise);
  });
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
  initBodyWeightModule();
  initSettingsModule();
  initSummaryModule();

  // Load initial backend state
  try {
    const state = await window.api.state();
    setState(state);
  } catch (err) {
    console.error('Initial state load failed:', err);
    toast(err.message, 'error');
  }
});
