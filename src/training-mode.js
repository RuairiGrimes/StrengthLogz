// Shared training phase policy (Bulk / Maingain / Cut) mirrored from Android TrainingModePolicy.

const CUT_RETENTION_FLOOR_RATIO = 0.95;
const CUT_RETENTION_FLOOR_PCT = 95.0;

const MODES = {
  BULK: {
    id: 'BULK',
    label: 'Bulk',
    shortLabel: 'Bulk',
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
    tagline: 'Protect strength retention, emphasize relative strength (× BW & DOTS) & manage fatigue',
    bullets: [
      'Focuses on strength retention (≥ 95% E1RM) & Relative Strength (× BW)',
      'Recovery-friendly volume (~75%) to protect muscle without burnout',
      'Replaces harsh red loss alerts on expected < 5% dips with calm Retention cues',
      'Celebrates Relative Strength PRs & DOTS gains while weight trends down',
    ],
  },
};

function normalizeTrainingMode(raw) {
  const clean = String(raw || '').trim().toUpperCase();
  return Object.prototype.hasOwnProperty.call(MODES, clean) ? clean : 'MAINGAIN';
}

function getTrainingModeMeta(raw) {
  return MODES[normalizeTrainingMode(raw)];
}

function weeklyMuscleTargets(raw) {
  const mode = normalizeTrainingMode(raw);
  if (mode === 'BULK') {
    return { Chest: 12, Back: 14, Legs: 12, Shoulders: 10, Arms: 10, Core: 4 };
  }
  if (mode === 'CUT') {
    return { Chest: 7, Back: 8, Legs: 7, Shoulders: 6, Arms: 6, Core: 4 };
  }
  return { Chest: 10, Back: 12, Legs: 10, Shoulders: 9, Arms: 9, Core: 4 };
}

function scoreWeights(raw) {
  const mode = normalizeTrainingMode(raw);
  if (mode === 'BULK') return { strength: 0.35, volume: 0.25, consistency: 0.20, balance: 0.20 };
  if (mode === 'CUT') return { strength: 0.35, volume: 0.20, consistency: 0.25, balance: 0.20 };
  return { strength: 0.30, volume: 0.25, consistency: 0.25, balance: 0.20 };
}

function tierLabel(raw, overallScore) {
  if (overallScore <= 0) return 'No Activity';
  const mode = normalizeTrainingMode(raw);
  if (mode === 'BULK') {
    if (overallScore >= 88) return 'Peak Growth';
    if (overallScore >= 74) return 'Strong Gains';
    if (overallScore >= 58) return 'Steady Build';
    if (overallScore >= 40) return 'Building Base';
    return 'Low Stimulus';
  }
  if (mode === 'CUT') {
    if (overallScore >= 88) return 'Elite Retention';
    if (overallScore >= 74) return 'Strong Cut';
    if (overallScore >= 58) return 'Solid Hold';
    if (overallScore >= 40) return 'Managing Fatigue';
    return 'Low Stimulus';
  }
  if (overallScore >= 88) return 'Peak Recomp';
  if (overallScore >= 74) return 'Strong Momentum';
  if (overallScore >= 58) return 'Steady Progress';
  if (overallScore >= 40) return 'Building Base';
  return 'Low Stimulus';
}

function classifyBodyWeightPace(raw, weeklyRateKg) {
  const mode = normalizeTrainingMode(raw);
  const r = Number(weeklyRateKg) || 0;
  if (mode === 'BULK') {
    if (r >= 0.15 && r <= 0.50) {
      return { status: 'On-pace lean bulk', isOnTarget: true, detail: 'Controlled surplus (+0.15 to +0.50 kg/wk) ideal for strength and hypertrophy.' };
    }
    if (r > 0.75) {
      return { status: 'Fast surplus — watch fat gain', isOnTarget: false, detail: 'Gaining faster than +0.75 kg/wk; consider trimming surplus slightly.' };
    }
    if (r > 0.50) {
      return { status: 'Brisk surplus', isOnTarget: false, detail: 'Slightly above lean bulk pace (+0.15 to +0.50 kg/wk).' };
    }
    if (r < -0.10) {
      return { status: 'Weight dropping in Bulk mode', isOnTarget: false, detail: 'Body weight is trending down; fuel recovery to support surplus overload.' };
    }
    return { status: 'Slow/maintenance surplus', isOnTarget: true, detail: 'Near maintenance; push intake slightly if absolute E1RMs stall.' };
  }
  if (mode === 'CUT') {
    if (r >= -0.85 && r <= -0.25) {
      return { status: 'On-pace sustainable cut', isOnTarget: true, detail: 'Steady fat-loss pace (-0.25 to -0.85 kg/wk) ideal for preserving lean mass & strength.' };
    }
    if (r < -1.10) {
      return { status: 'Aggressive deficit — monitor recovery', isOnTarget: false, detail: 'Losing faster than -1.1 kg/wk; keep intensity high and consider trimming fatigue.' };
    }
    if (r < -0.85) {
      return { status: 'Brisk deficit', isOnTarget: true, detail: 'Noticeable deficit (-0.85 to -1.10 kg/wk) — prioritize strength retention & sleep.' };
    }
    if (r > 0.15) {
      return { status: 'Weight trending up in Cut mode', isOnTarget: false, detail: 'Scale weight rose slightly over the window; check weekly average trend.' };
    }
    return { status: 'Mild cut / recomposition pace', isOnTarget: true, detail: 'Slow loss or holding weight — supportive for strength retention and relative ×BW.' };
  }
  if (r >= -0.20 && r <= 0.20) {
    return { status: 'Steady maingain weight', isOnTarget: true, detail: 'Weight holding within ±0.20 kg/wk — ideal for steady recomp and micro-gains.' };
  }
  if (r > 0.20) {
    return { status: 'Slight surplus drift', isOnTarget: false, detail: 'Trending above +0.20 kg/wk; tighten intake or switch to Bulk mode.' };
  }
  return { status: 'Slight deficit drift', isOnTarget: false, detail: 'Trending below -0.20 kg/wk; add recovery calories or switch to Cut mode.' };
}

function isExpectedCutDip(raw, currentVal, prevVal, currentRel = 0, prevRel = 0) {
  if (normalizeTrainingMode(raw) !== 'CUT') return false;
  if (!(prevVal > 0 && currentVal > 0 && currentVal < prevVal)) return false;
  return currentVal >= prevVal * CUT_RETENTION_FLOOR_RATIO || (prevRel > 0 && currentRel >= prevRel - 0.01);
}

module.exports = {
  CUT_RETENTION_FLOOR_RATIO,
  CUT_RETENTION_FLOOR_PCT,
  MODES,
  normalizeTrainingMode,
  getTrainingModeMeta,
  weeklyMuscleTargets,
  scoreWeights,
  tierLabel,
  classifyBodyWeightPace,
  isExpectedCutDip,
};
