/**
 * Remove tracked joint "spine" from exercises-from-db JSON and remap
 * joint / jointCode "spine" to left_hip or right_hip per pose variant.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DIR = path.resolve(__dirname, '../Exercise-json/exercises-from-db');

function collectJointCodes(trackedJoints) {
  const s = new Set();
  if (!Array.isArray(trackedJoints)) return s;
  for (const j of trackedJoints) {
    if (j?.joint) s.add(j.joint);
  }
  return s;
}

function pickReplacement(codes, slug) {
  if (slug === 'lib_spiderman_lunge') return 'left_hip';
  const lh = codes.has('left_hip');
  const rh = codes.has('right_hip');
  if (lh && !rh) return 'right_hip';
  if (rh && !lh) return 'left_hip';
  if (lh && rh) return 'right_hip';
  return 'left_hip';
}

function remapSpineStrings(value, replacement) {
  if (value === null || value === undefined) return value;
  if (Array.isArray(value)) {
    return value.map((v) => remapSpineStrings(v, replacement));
  }
  if (typeof value !== 'object') return value;
  const out = {};
  for (const [k, v] of Object.entries(value)) {
    if ((k === 'joint' || k === 'jointCode') && v === 'spine') {
      out[k] = replacement;
    } else {
      out[k] = remapSpineStrings(v, replacement);
    }
  }
  return out;
}

function stripSpineFromTrackedJoints(trackedJoints) {
  if (!Array.isArray(trackedJoints)) return trackedJoints;
  return trackedJoints.filter((j) => j?.joint !== 'spine');
}

function trackedListHasSpine(trackedJoints) {
  return Array.isArray(trackedJoints) && trackedJoints.some((j) => j?.joint === 'spine');
}

function main() {
  const files = fs.readdirSync(DIR).filter((f) => f.endsWith('.json'));
  let updated = 0;

  for (const f of files) {
    const fp = path.join(DIR, f);
    const raw = fs.readFileSync(fp, 'utf8');
    if (!raw.includes('"spine"')) continue;

    const data = JSON.parse(raw);
    const slug = data.slug || path.basename(f, '.json');
    const pvs = data.poseVariants;
    if (!Array.isArray(pvs)) continue;

    let touched = false;

    for (let i = 0; i < pvs.length; i++) {
      const pv = pvs[i];
      const tj = pv.trackedJoints;
      const dbPv = data._database?.poseVariants?.[i];
      const dbTj = dbPv?.trackedJointsConfig;

      const hadSpineInTop = trackedListHasSpine(tj);
      const hadSpineInDb = trackedListHasSpine(dbTj);
      if (!hadSpineInTop && !hadSpineInDb) continue;

      const codes = collectJointCodes(tj || dbTj);
      const replacement = pickReplacement(codes, slug);

      if (hadSpineInTop) {
        pv.trackedJoints = stripSpineFromTrackedJoints(tj);
        pvs[i] = remapSpineStrings(pv, replacement);
      }
      if (dbPv && hadSpineInDb) {
        dbPv.trackedJointsConfig = stripSpineFromTrackedJoints(dbTj);
        data._database.poseVariants[i] = remapSpineStrings(dbPv, replacement);
      }
      touched = true;
    }

    const out = JSON.stringify(data, null, 2) + '\n';
    if (out.includes('"spine"')) {
      console.warn('Still contains spine:', f);
    }
    if (touched) {
      fs.writeFileSync(fp, out, 'utf8');
      updated++;
    }
  }

  console.log('Files written:', updated);
}

main();
