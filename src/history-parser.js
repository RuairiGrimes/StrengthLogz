// Parses data/history.txt (normalised notes format) into workouts + sets.
const fs = require('fs');
const path = require('path');

const HISTORY_FILE = path.join(__dirname, '..', 'data', 'history.txt');

/** Split on commas that are not inside parentheses. */
function splitTopLevel(str) {
  const parts = [];
  let depth = 0;
  let cur = '';
  for (const ch of str) {
    if (ch === '(') depth++;
    if (ch === ')') depth = Math.max(0, depth - 1);
    if (ch === ',' && depth === 0) {
      parts.push(cur);
      cur = '';
    } else {
      cur += ch;
    }
  }
  if (cur.trim()) parts.push(cur);
  return parts.map((p) => p.trim()).filter(Boolean);
}

const SPEC_RE = /^(BW|-|\d+(?:\.\d+)?)\s*x\s*(-|\d+)(?:\s*x\s*(\d+))?\s*(?:\((.*)\))?$/i;

function parseSpec(spec, lineNo) {
  const m = spec.match(SPEC_RE);
  if (!m) throw new Error(`history.txt line ${lineNo}: cannot parse set spec "${spec}"`);
  const [, w, r, s, note] = m;
  return {
    weight: w === '-' ? null : w.toUpperCase() === 'BW' ? 0 : Number(w),
    reps: r === '-' ? null : Number(r),
    sets: s ? Number(s) : 1,
    note: note ? note.trim() : '',
  };
}

function parseHistory(text = fs.readFileSync(HISTORY_FILE, 'utf8')) {
  const workouts = [];
  const sets = [];
  let current = null;
  const lines = text.split(/\r?\n/);

  lines.forEach((raw, idx) => {
    const lineNo = idx + 1;
    const line = raw.trim();
    if (!line || (line.startsWith('#') && !line.startsWith('##'))) return;

    if (line.startsWith('##')) {
      const [date, title = '', ...rest] = line.slice(2).split('|').map((s) => s.trim());
      if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) throw new Error(`history.txt line ${lineNo}: bad date "${date}"`);
      current = { id: `hist-${date}`, date, title, notes: rest.join(' | ').trim() };
      workouts.push(current);
      return;
    }

    if (!current) throw new Error(`history.txt line ${lineNo}: exercise before any workout header`);
    const [exercise, specStr = '', ...noteParts] = line.split('|').map((s) => s.trim());
    const exNote = noteParts.join(' | ').trim();
    let setNo = 0;
    for (const specText of splitTopLevel(specStr)) {
      const spec = parseSpec(specText, lineNo);
      for (let i = 0; i < spec.sets; i++) {
        setNo++;
        const notes = [spec.note, exNote].filter(Boolean).join('; ');
        sets.push({
          id: `${current.id}-${slug(exercise)}-${setNo}`,
          workoutId: current.id,
          date: current.date,
          exercise,
          setNo,
          weight: spec.weight,
          reps: spec.reps,
          notes,
        });
      }
    }
  });

  return { workouts, sets };
}

function slug(s) {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

module.exports = { parseHistory, HISTORY_FILE };

