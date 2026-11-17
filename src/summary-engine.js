// In-engine 14-day training summary & deterministic scoring engine (Desktop parity with SummaryEngine.kt).
const crypto = require('crypto');
const SyncMerge = require('./sync-merge');

const MONTHS_SHORT = ['JAN', 'FEB', 'MAR', 'APR', 'MAY', 'JUN', 'JUL', 'AUG', 'SEP', 'OCT', 'NOV', 'DEC'];
const FALLBACK_BW_KG = 157.5 / 2.20462;
const WEEKLY_MUSCLE_TARGETS = { Chest: 10, Back: 12, Legs: 10, Shoulders: 9, Arms: 9, Core: 4 };

const SQUAT_NAMES = new Set(['squat', 'back squat', 'low bar squat', 'high bar squat', 'barbell squat', 'barbell back squat']);
const BENCH_NAMES = new Set(['bench press', 'flat bench press', 'barbell bench press', 'flat barbell bench press']);
const DEADLIFT_NAMES = new Set(['deadlift', 'conventional deadlift', 'sumo deadlift', 'barbell deadlift']);
const DOTS_MALE = [-307.75076, 24.0900756, -0.1918759221, 0.0007391293, -0.000001093];
const DOTS_FEMALE = [-57.96288, 13.6175032, -0.1126655495, 0.0005158568, -0.0000010706];

function round1(v) { return Math.round(v * 10) / 10; }
function round2(v) { return Math.round(v * 100) / 100; }
function roundHalf(v) { return Math.round(v * 2) / 2; }

function fmtNum(n) {
  if (n === null || n === undefined || Number.isNaN(n)) return '0';
  return Number.isInteger(n) ? String(n) : String(+Number(n).toFixed(2));
}

function todayIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function isoToEpochDay(iso) {
  if (!iso || !/^\d{4}-\d{2}-\d{2}$/.test(iso)) return 0;
  const [y, m, d] = iso.split('-').map(Number);
  return Math.floor(Date.UTC(y, m - 1, d) / 86400000);
}

function epochDayToDdMonYyyy(day) {
  const dt = new Date(day * 86400000);
  const dd = String(dt.getUTCDate()).padStart(2, '0');
  const mon = MONTHS_SHORT[dt.getUTCMonth()];
  return `${dd}-${mon}-${dt.getUTCFullYear()}`;
}

function formatDdMonYyyy(raw) {
  const iso = SyncMerge.isoKey(raw);
  return iso ? epochDayToDdMonYyyy(isoToEpochDay(iso)) : String(raw || '');
}

function dayOfWeek(raw) {
  const iso = SyncMerge.isoKey(raw);
  if (!iso) return '';
  return new Date(isoToEpochDay(iso) * 86400000).toLocaleDateString('en-US', { weekday: 'long', timeZone: 'UTC' });
}

function nowTimestampDdMonYyyy() {
  const d = new Date();
  const dd = String(d.getDate()).padStart(2, '0');
  const mon = MONTHS_SHORT[d.getMonth()];
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${dd}-${mon}-${d.getFullYear()} ${hh}:${mm}`;
}

function computeEpley(load, reps) {
  if (!reps || reps <= 0 || !load || load <= 0) return 0;
  if (reps === 1) return load;
  return roundHalf(load * (1 + reps / 30));
}

function weightOn(dateStr, entries) {
  if (!entries || !entries.length) return FALLBACK_BW_KG;
  const targetIso = SyncMerge.isoKey(dateStr) || todayIso();
  const sorted = [...entries]
    .filter((b) => Number(b.weightKg) > 0 && SyncMerge.isoKey(b.date))
    .sort((a, b) => SyncMerge.isoKey(a.date).localeCompare(SyncMerge.isoKey(b.date)));
  if (!sorted.length) return FALLBACK_BW_KG;
  let candidate = Number(sorted[0].weightKg);
  for (const b of sorted) {
    if (SyncMerge.isoKey(b.date) <= targetIso) candidate = Number(b.weightKg);
    else break;
  }
  return candidate;
}

function buildBodyWeightSeries(entries) {
  const byIso = new Map();
  for (const b of entries || []) {
    const iso = SyncMerge.isoKey(b.date);
    if (iso && Number(b.weightKg) > 0) byIso.set(iso, b);
  }
  const sorted = [...byIso.entries()].sort((a, b) => a[0].localeCompare(b[0])).map(([, b]) => b);
  const days = sorted.map((b) => isoToEpochDay(SyncMerge.isoKey(b.date)));
  return sorted.map((b, i) => {
    let sum = 0;
    let n = 0;
    for (let j = i; j >= 0 && days[i] - days[j] <= 6; j--) {
      sum += Number(sorted[j].weightKg);
      n++;
    }
    return {
      iso: SyncMerge.isoKey(b.date),
      day: days[i],
      kg: Number(b.weightKg),
      avg7Kg: n ? sum / n : Number(b.weightKg),
    };
  });
}

function classifyLift(name) {
  const n = String(name || '').trim().toLowerCase();
  if (SQUAT_NAMES.has(n)) return 'squat';
  if (BENCH_NAMES.has(n)) return 'bench';
  if (DEADLIFT_NAMES.has(n)) return 'deadlift';
  return null;
}

function computeDots(totalKg, bwKg, isMale) {
  if (!(totalKg > 0) || !(bwKg > 0)) return 0;
  const bw = Math.min(210, Math.max(40, bwKg));
  const c = isMale ? DOTS_MALE : DOTS_FEMALE;
  const denom = c[0] + c[1] * bw + c[2] * bw ** 2 + c[3] * bw ** 3 + c[4] * bw ** 4;
  return denom > 0 ? (totalKg * 500) / denom : 0;
}

function buildDotsTimeline(workouts, sets, bodyWeights, isMale) {
  if (!bodyWeights || !bodyWeights.length) return [];
  const wById = new Map((workouts || []).map((w) => [w.id, w]));
  const obs = [];
  for (const s of sets || []) {
    const lift = classifyLift(s.exercise);
    const reps = Number(s.reps || 0);
    const w = Number(s.weight || 0);
    if (!lift || reps <= 0 || w <= 0) continue;
    const date = wById.get(s.workoutId)?.date || s.date;
    const iso = SyncMerge.isoKey(date);
    if (!iso) continue;
    const e = reps === 1 ? w : w * (1 + reps / 30);
    obs.push({ iso, day: isoToEpochDay(iso), lift, e });
  }
  if (!obs.length) return [];
  const dates = [...new Map(obs.map((o) => [o.iso, o.day])).entries()].sort((a, b) => a[0].localeCompare(b[0]));
  const out = [];
  for (const [iso, day] of dates) {
    const best = { squat: 0, bench: 0, deadlift: 0 };
    for (const o of obs) {
      if (o.day <= day && day - o.day <= 90 && o.e > best[o.lift]) best[o.lift] = o.e;
    }
    if (!best.squat || !best.bench || !best.deadlift) continue;
    const bw = weightOn(iso, bodyWeights);
    const total = best.squat + best.bench + best.deadlift;
    out.push({ iso, dots: computeDots(total, bw, isMale) });
  }
  return out;
}

function computeDeterministicScores({
  workoutsCount,
  programTarget14d,
  longestRestGapDays,
  validSetsCount,
  volumeDeltaPct,
  muscleItems,
  lifts,
  prsCount,
  progressingCount,
  regressingCount,
  pushToPullRatio,
  upperSets,
  lowerSets,
}) {
  if (workoutsCount === 0 || validSetsCount === 0) {
    return { overallScore: 0, volumeScore: 0, strengthScore: 0, consistencyScore: 0, balanceScore: 0, tierLabel: 'No Activity' };
  }

  const targetSessions = Math.max(2, programTarget14d || 6);
  const freqRatio = Math.min(1.1, Math.max(0, workoutsCount / targetSessions));
  const gapPenalty = Math.max(0, longestRestGapDays - 4) * 6;
  const consistencyScore = Math.max(10, Math.min(100, Math.round(
    (freqRatio * 90) + (longestRestGapDays <= 3 ? 10 : 0) - gapPenalty
  )));

  const primaryMuscles = muscleItems.filter((m) => m.category !== 'Core');
  const avgMuscleCoverage = primaryMuscles.length
    ? primaryMuscles.reduce((acc, m) => acc + Math.min(1.1, m.workingSets14d / Math.max(1, m.target14dSets)), 0) / primaryMuscles.length
    : 0;
  let continuityAdj = 5;
  if (volumeDeltaPct !== null && volumeDeltaPct !== undefined) {
    if (volumeDeltaPct >= -15 && volumeDeltaPct <= 25) continuityAdj = 8;
    else if (volumeDeltaPct > 25) continuityAdj = 4;
    else continuityAdj = -6;
  }
  const volumeScore = Math.max(10, Math.min(100, Math.round((avgMuscleCoverage * 88) + continuityAdj)));

  let strengthScore = 0;
  if (lifts.length) {
    const netPositiveRatio = (progressingCount + 0.5 * (lifts.length - progressingCount - regressingCount)) / lifts.length;
    const withDelta = lifts.filter((l) => l.e1rmDeltaPct !== null && l.e1rmDeltaPct !== undefined);
    const avgDeltaPct = withDelta.length ? withDelta.reduce((a, l) => a + l.e1rmDeltaPct, 0) / withDelta.length : 0;
    const clampedAvg = Math.max(-10, Math.min(10, avgDeltaPct));
    const prBonus = Math.min(18, prsCount * 6);
    strengthScore = Math.max(15, Math.min(100, Math.round(45 + (netPositiveRatio * 35) + (clampedAvg * 1.5) + prBonus)));
  }

  const coveredCount = primaryMuscles.filter((m) => m.workingSets14d >= m.target14dSets * 0.45).length;
  const coveragePts = (coveredCount / 5) * 60;
  let pushPullPts = 10;
  if (pushToPullRatio !== null) {
    if (pushToPullRatio >= 0.75 && pushToPullRatio <= 1.35) pushPullPts = 25;
    else if (pushToPullRatio >= 0.55 && pushToPullRatio <= 1.65) pushPullPts = 16;
    else pushPullPts = 6;
  }
  let lowerPts = 0;
  if (upperSets + lowerSets > 0 && lowerSets > 0) {
    lowerPts = (lowerSets / (upperSets + lowerSets)) >= 0.22 ? 15 : 8;
  }
  const balanceScore = Math.max(10, Math.min(100, Math.round(coveragePts + pushPullPts + lowerPts)));

  const overallScore = Math.max(0, Math.min(100, Math.round(
    consistencyScore * 0.25 + volumeScore * 0.25 + strengthScore * 0.30 + balanceScore * 0.20
  )));

  let tierLabel = 'Low Stimulus';
  if (overallScore >= 88) tierLabel = 'Peak Block';
  else if (overallScore >= 74) tierLabel = 'Strong Momentum';
  else if (overallScore >= 58) tierLabel = 'Steady Progress';
  else if (overallScore >= 40) tierLabel = 'Building Base';

  return { overallScore, volumeScore, strengthScore, consistencyScore, balanceScore, tierLabel };
}

function build14DaySummary({
  workouts = [],
  sets = [],
  exercises = [],
  bodyWeights = [],
  goals = [],
  activeProgram = null,
  isMale = true,
  referenceDate = null,
}) {
  const endIso = SyncMerge.isoKey(referenceDate) || todayIso();
  const endDay = isoToEpochDay(endIso);
  const startDay = endDay - 13;
  const priorEndDay = startDay - 1;
  const priorStartDay = priorEndDay - 13;

  const startDate = epochDayToDdMonYyyy(startDay);
  const endDate = epochDayToDdMonYyyy(endDay);
  const priorStartDate = epochDayToDdMonYyyy(priorStartDay);
  const priorEndDate = epochDayToDdMonYyyy(priorEndDay);

  const workoutById = new Map(workouts.map((w) => [w.id, w]));
  const exerciseMap = new Map(exercises.map((e) => [String(e.name || '').trim().toLowerCase(), e]));
  const hasBwData = (bodyWeights || []).some((b) => Number(b.weightKg) > 0);

  const resolveCategory = (name) => exerciseMap.get(String(name || '').trim().toLowerCase())?.category || 'Other';
  const resolveIsBw = (name) => !!exerciseMap.get(String(name || '').trim().toLowerCase())?.bodyweight;
  const setDateStr = (s) => workoutById.get(s.workoutId)?.date || s.date;
  const setDay = (s) => isoToEpochDay(SyncMerge.isoKey(setDateStr(s)));

  const workoutDays = workouts
    .map((w) => ({ w, d: isoToEpochDay(SyncMerge.isoKey(w.date)) }))
    .filter((x) => x.d > 0);

  const latestPair = workoutDays.reduce((best, x) => (!best || x.d > best.d ? x : best), null);
  const latestLoggedWorkoutDate = latestPair ? formatDdMonYyyy(latestPair.w.date) : null;
  const daysSinceLastWorkout = latestPair ? Math.max(0, endDay - latestPair.d) : null;

  const workouts14d = workoutDays
    .filter((x) => x.d >= startDay && x.d <= endDay)
    .sort((a, b) => (b.d - a.d) || (a.w.id < b.w.id ? 1 : -1));
  const priorWorkouts14d = workoutDays.filter((x) => x.d >= priorStartDay && x.d <= priorEndDay);

  const setsIn14dAll = [];
  const validSets14d = [];
  const validSetsPrior14d = [];
  const validSetsBeforeWindow = [];

  for (const s of sets) {
    const d = setDay(s);
    if (d <= 0) continue;
    const reps = Number(s.reps || 0);
    if (d >= startDay && d <= endDay) {
      setsIn14dAll.push(s);
      if (reps > 0) validSets14d.push(s);
    } else if (d >= priorStartDay && d <= priorEndDay) {
      if (reps > 0) {
        validSetsPrior14d.push(s);
        validSetsBeforeWindow.push(s);
      }
    } else if (d < startDay) {
      if (reps > 0) validSetsBeforeWindow.push(s);
    }
  }

  const failedSetsCount = setsIn14dAll.filter((s) => Number(s.reps || 0) <= 0).length;
  const totalReps = validSets14d.reduce((acc, s) => acc + Number(s.reps || 0), 0);
  const totalVolumeKg = round1(validSets14d.reduce((acc, s) => acc + Number(s.weight || 0) * Number(s.reps || 0), 0));
  const priorVolumeKg = round1(validSetsPrior14d.reduce((acc, s) => acc + Number(s.weight || 0) * Number(s.reps || 0), 0));
  const volumeDeltaKg = round1(totalVolumeKg - priorVolumeKg);
  const volumeDeltaPct = priorVolumeKg > 0 ? round1(((totalVolumeKg - priorVolumeKg) / priorVolumeKg) * 100) : null;

  const e1rmForSet = (s) => {
    const reps = Number(s.reps || 0);
    if (reps <= 0) return 0;
    const isBw = resolveIsBw(s.exercise);
    const w = Number(s.weight || 0);
    const bwKg = isBw ? (hasBwData ? weightOn(setDateStr(s), bodyWeights) : FALLBACK_BW_KG) : 0;
    const load = isBw ? bwKg + w : w;
    return load > 0 ? computeEpley(load, reps) : 0;
  };

  const effectiveBwAdjustedVolumeKg = round1(validSets14d.reduce((acc, s) => {
    const reps = Number(s.reps || 0);
    const isBw = resolveIsBw(s.exercise);
    const w = Number(s.weight || 0);
    const bwKg = isBw ? (hasBwData ? weightOn(setDateStr(s), bodyWeights) : FALLBACK_BW_KG) : 0;
    return acc + (isBw ? bwKg + w : w) * reps;
  }, 0));

  const workoutsCount = workouts14d.length || new Set(validSets14d.map((s) => s.workoutId || setDateStr(s))).size;
  const priorWorkoutsCount = priorWorkouts14d.length || new Set(validSetsPrior14d.map((s) => s.workoutId || setDateStr(s))).size;

  const activeEpochDays = [...new Set([
    ...workouts14d.map((x) => x.d),
    ...validSets14d.map(setDay),
  ])].filter((d) => d >= startDay && d <= endDay).sort((a, b) => a - b);

  const activeDaysCount = activeEpochDays.length;
  let longestRestGapDays = 14;
  if (activeEpochDays.length) {
    let maxGap = activeEpochDays[0] - startDay;
    for (let i = 1; i < activeEpochDays.length; i++) {
      maxGap = Math.max(maxGap, Math.max(0, activeEpochDays[i] - activeEpochDays[i - 1] - 1));
    }
    maxGap = Math.max(maxGap, Math.max(0, endDay - activeEpochDays[activeEpochDays.length - 1]));
    longestRestGapDays = maxGap;
  }

  const progDaysCount = activeProgram?.days?.length || 0;
  const programTargetSessions14d = progDaysCount > 0 ? progDaysCount * 2 : null;
  const programAdherencePct = programTargetSessions14d
    ? Math.max(0, Math.min(100, Math.round((workoutsCount / programTargetSessions14d) * 100)))
    : null;

  const sessionsPerWeek = round1(workoutsCount / 2);
  const avgVolumePerWorkoutKg = workoutsCount > 0 ? round1(totalVolumeKg / workoutsCount) : 0;
  const avgSetsPerWorkout = workoutsCount > 0 ? round1(validSets14d.length / workoutsCount) : 0;
  const avgRepsPerSet = validSets14d.length ? round1(totalReps / validSets14d.length) : 0;

  let heavySets = 0;
  let strengthHypSets = 0;
  let hypertrophySets = 0;
  let enduranceSets = 0;
  for (const s of validSets14d) {
    const r = Number(s.reps || 0);
    if (r <= 5) heavySets++;
    else if (r <= 8) strengthHypSets++;
    else if (r <= 12) hypertrophySets++;
    else enduranceSets++;
  }

  const sets14ByCat = {};
  const vol14ByCat = {};
  for (const s of validSets14d) {
    const cat = resolveCategory(s.exercise);
    sets14ByCat[cat] = (sets14ByCat[cat] || 0) + 1;
    vol14ByCat[cat] = (vol14ByCat[cat] || 0) + Number(s.weight || 0) * Number(s.reps || 0);
  }
  const priorSetsByCat = {};
  for (const s of validSetsPrior14d) {
    const cat = resolveCategory(s.exercise);
    priorSetsByCat[cat] = (priorSetsByCat[cat] || 0) + 1;
  }
  const muscleItems = Object.entries(WEEKLY_MUSCLE_TARGETS).map(([category, wkTarget]) => ({
    category,
    workingSets14d: sets14ByCat[category] || 0,
    prior14dWorkingSets: priorSetsByCat[category] || 0,
    volumeKg14d: round1(vol14ByCat[category] || 0),
    target14dSets: wkTarget * 2,
  }));

  const pushSets = (sets14ByCat.Chest || 0) + (sets14ByCat.Shoulders || 0);
  const pullSets = sets14ByCat.Back || 0;
  const pushToPullRatio = pullSets > 0 ? round2(pushSets / pullSets) : null;
  const upperSets = pushSets + pullSets + (sets14ByCat.Arms || 0);
  const lowerSets = sets14ByCat.Legs || 0;
  const upperSharePct = (upperSets + lowerSets) > 0 ? Math.round((upperSets / (upperSets + lowerSets)) * 100) : 0;

  const startBodyWeightKg = hasBwData ? round1(weightOn(startDate, bodyWeights)) : null;
  const endBodyWeightKg = hasBwData ? round1(weightOn(endDate, bodyWeights)) : null;
  const bodyWeightDeltaKg = (startBodyWeightKg !== null && endBodyWeightKg !== null)
    ? round2(endBodyWeightKg - startBodyWeightKg)
    : null;
  const bwSeries = hasBwData ? buildBodyWeightSeries(bodyWeights) : [];
  const bwInPeriod = bwSeries.filter((p) => p.day >= startDay && p.day <= endDay);
  const lastBwPoint = [...bwSeries].reverse().find((p) => p.day <= endDay);
  const avg7BodyWeightKg = lastBwPoint ? round1(lastBwPoint.avg7Kg) : null;
  let bwWeeklyRateKg = null;
  if (bwInPeriod.length >= 2) {
    const span = Math.max(1, bwInPeriod[bwInPeriod.length - 1].day - bwInPeriod[0].day);
    if (span >= 4) {
      bwWeeklyRateKg = round2((bwInPeriod[bwInPeriod.length - 1].kg - bwInPeriod[0].kg) / (span / 7));
    }
  }

  const dotsTimeline = hasBwData ? buildDotsTimeline(workouts, sets, bodyWeights, isMale) : [];
  const latestDotsPt = [...dotsTimeline].reverse().find((p) => p.iso <= endIso);
  const startIso = SyncMerge.isoKey(startDate);
  const priorDotsPt = [...dotsTimeline].reverse().find((p) => p.iso < startIso);
  const dotsScore = latestDotsPt ? round1(latestDotsPt.dots) : null;
  const dotsDelta = (dotsScore !== null && priorDotsPt) ? round1(dotsScore - priorDotsPt.dots) : null;

  const sets14ByEx = new Map();
  for (const s of validSets14d) {
    const k = String(s.exercise || '').trim().toLowerCase();
    if (!k) continue;
    if (!sets14ByEx.has(k)) sets14ByEx.set(k, []);
    sets14ByEx.get(k).push(s);
  }
  const priorByEx = new Map();
  for (const s of validSetsBeforeWindow) {
    const k = String(s.exercise || '').trim().toLowerCase();
    if (!k) continue;
    if (!priorByEx.has(k)) priorByEx.set(k, []);
    priorByEx.get(k).push(s);
  }

  let prsCount = 0;
  let progressingCount = 0;
  let holdingCount = 0;
  let regressingCount = 0;

  const lifts = [...sets14ByEx.values()].map((exSets14) => {
    const exName = String(exSets14[0].exercise || '').trim();
    const cat = resolveCategory(exName);
    const isBw = resolveIsBw(exName);

    const bySession = new Map();
    for (const s of exSets14) {
      const key = s.workoutId || setDateStr(s);
      if (!bySession.has(key)) bySession.set(key, []);
      bySession.get(key).push(s);
    }
    const sessCurve = [...bySession.values()]
      .map((list) => ({ d: setDay(list[0]), bestE: Math.max(0, ...list.map(e1rmForSet)) }))
      .sort((a, b) => a.d - b.d);

    const withinPeriodE1rmDeltaKg = (sessCurve.length >= 2 && sessCurve[0].bestE > 0 && sessCurve[sessCurve.length - 1].bestE > 0)
      ? round1(sessCurve[sessCurve.length - 1].bestE - sessCurve[0].bestE)
      : null;

    let bestSet = null;
    let bestE1rm14 = 0;
    let exVol14 = 0;
    for (const s of exSets14) {
      exVol14 += Number(s.weight || 0) * Number(s.reps || 0);
      const e = e1rmForSet(s);
      if (e > bestE1rm14) {
        bestE1rm14 = e;
        bestSet = s;
      }
    }

    const priorSets = priorByEx.get(exName.toLowerCase()) || [];
    const priorAllBest = priorSets.length ? Math.max(0, ...priorSets.map(e1rmForSet)) : 0;
    const recentPrior = priorSets.filter((s) => setDay(s) >= priorStartDay - 28);
    const baselinePool = recentPrior.length ? recentPrior : priorSets;
    const recentBaseRaw = baselinePool.length ? Math.max(0, ...baselinePool.map(e1rmForSet)) : 0;
    const priorBestE1rmKg = recentBaseRaw > 0 ? round1(recentBaseRaw) : null;

    const e1rmDeltaKg = (priorBestE1rmKg !== null && bestE1rm14 > 0) ? round1(bestE1rm14 - priorBestE1rmKg) : null;
    const e1rmDeltaPct = (priorBestE1rmKg !== null && priorBestE1rmKg > 0 && e1rmDeltaKg !== null)
      ? round1((e1rmDeltaKg / priorBestE1rmKg) * 100)
      : null;

    const isAllTimePr = bestE1rm14 > 0 && priorAllBest > 0 && bestE1rm14 > priorAllBest;
    const trendDelta = e1rmDeltaKg !== null ? e1rmDeltaKg : withinPeriodE1rmDeltaKg;
    if (isAllTimePr) prsCount++;
    if (trendDelta !== null && trendDelta > 0.5) progressingCount++;
    else if (trendDelta !== null && trendDelta < -0.5) regressingCount++;
    else holdingCount++;

    let bestSet14d = '-';
    if (bestSet) {
      const w = Number(bestSet.weight || 0);
      const wPart = isBw ? (w === 0 ? 'BW' : `BW+${fmtNum(w)}kg`) : `${fmtNum(w)}kg`;
      bestSet14d = `${wPart} x ${bestSet.reps || 0}`;
    }

    const relativeStrengthBw = (endBodyWeightKg && endBodyWeightKg > 0 && bestE1rm14 > 0)
      ? round2(bestE1rm14 / endBodyWeightKg)
      : null;

    return {
      exerciseName: exName,
      category: cat,
      isBodyweight: isBw,
      sessionsIn14d: sessCurve.length,
      workingSets14d: exSets14.length,
      volumeKg14d: round1(exVol14),
      bestSet14d,
      bestE1rm14dKg: round1(bestE1rm14),
      priorBestE1rmKg,
      e1rmDeltaKg,
      e1rmDeltaPct,
      withinPeriodE1rmDeltaKg,
      relativeStrengthBw,
      isAllTimePr,
    };
  }).sort((a, b) => (Number(b.isAllTimePr) - Number(a.isAllTimePr))
    || ((b.e1rmDeltaKg ?? b.withinPeriodE1rmDeltaKg ?? 0) - (a.e1rmDeltaKg ?? a.withinPeriodE1rmDeltaKg ?? 0))
    || (b.workingSets14d - a.workingSets14d));

  const sets14ByWorkoutId = new Map();
  for (const s of validSets14d) {
    if (!sets14ByWorkoutId.has(s.workoutId)) sets14ByWorkoutId.set(s.workoutId, []);
    sets14ByWorkoutId.get(s.workoutId).push(s);
  }
  const sessions = workouts14d.map(({ w }) => {
    const wSets = sets14ByWorkoutId.get(w.id) || [];
    return {
      workoutId: w.id,
      date: formatDdMonYyyy(w.date),
      dayOfWeek: dayOfWeek(w.date),
      title: w.title || 'Workout Session',
      workingSets: wSets.length,
      volumeKg: round1(wSets.reduce((acc, s) => acc + Number(s.weight || 0) * Number(s.reps || 0), 0)),
      exercisesCount: new Set(wSets.map((s) => String(s.exercise || '').trim().toLowerCase())).size,
    };
  });

  const goalStatuses = (goals || [])
    .filter((g) => g.enabled !== false)
    .map((g) => {
      const exSets = sets.filter((s) => String(s.exercise || '').trim().toLowerCase() === String(g.exerciseName || '').trim().toLowerCase() && Number(s.reps || 0) > 0);
      const bestE = exSets.length ? Math.max(0, ...exSets.map(e1rmForSet)) : 0;
      const targetE = g.goalType === 'SETS_REPS'
        ? computeEpley(Number(g.targetWeightKg || 0), Number(g.targetReps || 8))
        : Number(g.targetE1rmKg || 0);
      const pct = targetE > 0 ? Math.max(0, Math.min(100, Math.round((bestE / targetE) * 100))) : 0;
      const achieved = !!g.achievedDate || pct >= 100;
      const targetSummary = g.goalType === 'SETS_REPS'
        ? `${g.targetSets || 3}x${g.targetReps || 8} @ ${fmtNum(g.targetWeightKg || 0)}kg`
        : `${fmtNum(g.targetE1rmKg || 0)}kg 1RM`;
      return {
        exerciseName: g.exerciseName,
        targetSummary,
        progressPct: pct,
        trainedIn14d: sets14ByEx.has(String(g.exerciseName || '').trim().toLowerCase()),
        achieved,
      };
    });

  const scores = computeDeterministicScores({
    workoutsCount,
    programTarget14d: programTargetSessions14d,
    longestRestGapDays,
    validSetsCount: validSets14d.length,
    volumeDeltaPct,
    muscleItems,
    lifts,
    prsCount,
    progressingCount,
    regressingCount,
    pushToPullRatio,
    upperSets,
    lowerSets,
  });

  const highlights = [];
  const recommendations = [];
  if (workoutsCount === 0) {
    const lastInfo = daysSinceLastWorkout !== null
      ? `Last recorded session was ${daysSinceLastWorkout} days ago.`
      : 'No sessions logged yet.';
    highlights.push(`Zero working sessions recorded in the trailing 14-day window. ${lastInfo}`);
    recommendations.push('Log a baseline Push, Pull, or Full Body session to re-establish training momentum.');
  } else {
    if (volumeDeltaPct !== null) {
      const dir = volumeDeltaPct >= 0 ? `+${fmtNum(volumeDeltaPct)}%` : `${fmtNum(volumeDeltaPct)}%`;
      highlights.push(`Volume totaled ${fmtNum(totalVolumeKg)} kg across ${workoutsCount} sessions (${dir} vs prior 14 days, ${priorWorkoutsCount} sessions).`);
    } else {
      highlights.push(`Logged ${workoutsCount} sessions totaling ${fmtNum(totalVolumeKg)} kg of external workload.`);
    }

    if (prsCount > 0) {
      const topPr = lifts.find((l) => l.isAllTimePr);
      if (topPr) {
        highlights.push(`${prsCount} all-time E1RM PR${prsCount > 1 ? 's' : ''} set, led by ${topPr.exerciseName} at ${fmtNum(topPr.bestE1rm14dKg)} kg (${topPr.bestSet14d}).`);
      }
    } else {
      const bestGain = lifts.filter((l) => (l.e1rmDeltaKg || 0) > 0).sort((a, b) => b.e1rmDeltaKg - a.e1rmDeltaKg)[0];
      if (bestGain) {
        highlights.push(`Top strength gain on ${bestGain.exerciseName}: +${fmtNum(bestGain.e1rmDeltaKg)} kg est. 1RM (${bestGain.bestSet14d}).`);
      }
    }

    if (hasBwData && bodyWeightDeltaKg !== null) {
      const sign = bodyWeightDeltaKg > 0 ? '+' : '';
      const rateStr = bwWeeklyRateKg !== null ? ` (${bwWeeklyRateKg > 0 ? '+' : ''}${fmtNum(bwWeeklyRateKg)} kg/wk)` : '';
      const dotsStr = dotsScore !== null
        ? ` • DOTS ${fmtNum(dotsScore)}${dotsDelta !== null ? ` (${dotsDelta > 0 ? '+' : ''}${fmtNum(dotsDelta)})` : ''}`
        : '';
      highlights.push(`Body weight moved ${sign}${fmtNum(bodyWeightDeltaKg)} kg over 14 days${rateStr}${dotsStr}.`);
    }

    if (activeProgram?.programName && programAdherencePct !== null) {
      highlights.push(`${activeProgram.programName} adherence: ${programAdherencePct}% of scheduled 14-day sessions completed.`);
    }

    const underTrained = muscleItems
      .filter((m) => m.category !== 'Core' && m.workingSets14d < m.target14dSets * 0.5)
      .sort((a, b) => (a.workingSets14d / Math.max(1, a.target14dSets)) - (b.workingSets14d / Math.max(1, b.target14dSets)));
    if (underTrained.length) {
      const names = underTrained.slice(0, 2).map((m) => `${m.category} (${m.workingSets14d}/${m.target14dSets} sets)`).join(' and ');
      recommendations.push(`Prioritize ${names} in the upcoming block to close 14-day volume deficits.`);
    }

    if (pushToPullRatio !== null && pushToPullRatio > 1.4) {
      recommendations.push(`Push-to-pull ratio is ${fmtNum(pushToPullRatio)}:1; add horizontal/vertical pulling sets for shoulder balance.`);
    } else if (pushToPullRatio !== null && pushToPullRatio < 0.65) {
      recommendations.push(`Pulling exceeds pressing (${fmtNum(pushToPullRatio)}:1); add pressing volume to match upper-back work.`);
    }

    if (upperSets > 0 && lowerSets === 0) {
      recommendations.push('Zero lower-body working sets logged in 14 days; schedule a squat or hinge session.');
    }

    if (failedSetsCount >= 2) {
      recommendations.push(`${failedSetsCount} zero-rep failed sets logged in 14 days; trim top-set load by 2.5-5% to restore bar speed.`);
    }

    const untouchedGoals = goalStatuses.filter((g) => !g.achieved && !g.trainedIn14d);
    if (untouchedGoals.length) {
      recommendations.push(`Active goal lift${untouchedGoals.length > 1 ? 's' : ''} not trained in 14 days: ${untouchedGoals.map((g) => g.exerciseName).join(', ')}.`);
    }

    if (longestRestGapDays >= 5) {
      recommendations.push(`Longest training gap was ${longestRestGapDays} days; tighter session spacing will improve weekly stimulus.`);
    }

    if (volumeDeltaPct !== null && volumeDeltaPct > 30) {
      recommendations.push(`Volume jumped +${fmtNum(volumeDeltaPct)}% vs the prior 14 days; watch recovery before adding more sets.`);
    }

    if (!recommendations.length) {
      recommendations.push('Volume, cadence, and strength trends are balanced; hold current split and progress loads by 1.25-2.5 kg on top-rep hits.');
    }
  }

  return {
    startDate,
    endDate,
    priorStartDate,
    priorEndDate,
    latestLoggedWorkoutDate,
    daysSinceLastWorkout,
    ...scores,
    workoutsCount,
    priorWorkoutsCount,
    sessionsPerWeek,
    activeDaysCount,
    longestRestGapDays,
    programName: activeProgram?.programName || null,
    programTargetSessions14d,
    programAdherencePct,
    workingSetsCount: validSets14d.length,
    priorWorkingSetsCount: validSetsPrior14d.length,
    totalReps,
    failedSetsCount,
    totalVolumeKg,
    priorVolumeKg,
    volumeDeltaKg,
    volumeDeltaPct,
    effectiveBwAdjustedVolumeKg,
    avgVolumePerWorkoutKg,
    avgSetsPerWorkout,
    avgRepsPerSet,
    heavySets,
    strengthHypSets,
    hypertrophySets,
    enduranceSets,
    pushSets,
    pullSets,
    pushToPullRatio,
    upperSets,
    lowerSets,
    upperSharePct,
    hasBodyWeightData: hasBwData,
    startBodyWeightKg,
    endBodyWeightKg,
    bodyWeightDeltaKg,
    avg7BodyWeightKg,
    bwWeeklyRateKg,
    dotsScore,
    dotsDelta,
    prsCount,
    progressingCount,
    holdingCount,
    regressingCount,
    muscleItems,
    lifts,
    sessions,
    goalStatuses,
    highlights: highlights.slice(0, 4),
    recommendations: recommendations.slice(0, 4),
  };
}

function toSavedEntry(summary, customTitle = '', customNotes = '') {
  const defaultTitle = summary.workoutsCount === 0
    ? `14-Day Block (${summary.startDate} - ${summary.endDate})`
    : `${summary.startDate} - ${summary.endDate} (${summary.workoutsCount} sessions)`;
  return {
    id: `sum-${Date.now().toString(36)}-${crypto.randomBytes(3).toString('hex')}`,
    title: String(customTitle || '').trim() || defaultTitle,
    notes: String(customNotes || '').trim(),
    savedAt: nowTimestampDdMonYyyy(),
    savedEpochMs: Date.now(),
    startDate: summary.startDate,
    endDate: summary.endDate,
    overallScore: summary.overallScore,
    tierLabel: summary.tierLabel,
    volumeScore: summary.volumeScore,
    strengthScore: summary.strengthScore,
    consistencyScore: summary.consistencyScore,
    balanceScore: summary.balanceScore,
    workoutsCount: summary.workoutsCount,
    priorWorkoutsCount: summary.priorWorkoutsCount,
    workingSetsCount: summary.workingSetsCount,
    priorWorkingSetsCount: summary.priorWorkingSetsCount,
    totalReps: summary.totalReps,
    failedSetsCount: summary.failedSetsCount,
    totalVolumeKg: summary.totalVolumeKg,
    priorVolumeKg: summary.priorVolumeKg,
    volumeDeltaPct: summary.volumeDeltaPct,
    startBodyWeightKg: summary.startBodyWeightKg,
    endBodyWeightKg: summary.endBodyWeightKg,
    bodyWeightDeltaKg: summary.bodyWeightDeltaKg,
    avg7BodyWeightKg: summary.avg7BodyWeightKg,
    dotsScore: summary.dotsScore,
    prsCount: summary.prsCount,
    progressingCount: summary.progressingCount,
    holdingCount: summary.holdingCount,
    regressingCount: summary.regressingCount,
    pushToPullRatio: summary.pushToPullRatio,
    upperSharePct: summary.upperSharePct,
    heavySets: summary.heavySets,
    strengthHypSets: summary.strengthHypSets,
    hypertrophySets: summary.hypertrophySets,
    enduranceSets: summary.enduranceSets,
    muscleItems: summary.muscleItems,
    topLifts: (summary.lifts || []).slice(0, 10),
    highlights: summary.highlights || [],
    recommendations: summary.recommendations || [],
  };
}

module.exports = {
  build14DaySummary,
  toSavedEntry,
};
