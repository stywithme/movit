/**
 * For side-view squat-style configs: add missing right_hip tracked joint
 * (clone of left_hip) when we have left_hip + right_knee but no right_hip.
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DIR = path.resolve(__dirname, '../Exercise-json/exercises-from-db');

function needsMirror(pv) {
  const tj = pv.trackedJoints;
  if (!Array.isArray(tj)) return false;
  const set = new Set(tj.map((j) => j?.joint).filter(Boolean));
  return set.has('left_hip') && set.has('right_knee') && !set.has('right_hip');
}

function cloneRightHipFromLeft(leftHip) {
  const j = structuredClone(leftHip);
  j.joint = 'right_hip';
  if (j.pairedWith) j.pairedWith = 'left_hip';
  return j;
}

function insertAfterLeftHip(trackedJoints) {
  const idx = trackedJoints.findIndex((j) => j?.joint === 'left_hip');
  if (idx === -1) return false;
  const copy = cloneRightHipFromLeft(trackedJoints[idx]);
  trackedJoints.splice(idx + 1, 0, copy);
  return true;
}

function main() {
  const files = fs.readdirSync(DIR).filter((f) => f.endsWith('.json'));
  let written = 0;

  for (const f of files) {
    const fp = path.join(DIR, f);
    const data = JSON.parse(fs.readFileSync(fp, 'utf8'));
    const pvs = data.poseVariants;
    if (!Array.isArray(pvs)) continue;

    let touched = false;
    for (let i = 0; i < pvs.length; i++) {
      const pv = pvs[i];
      if (!needsMirror(pv)) continue;
      if (!insertAfterLeftHip(pv.trackedJoints)) continue;

      const dbPv = data._database?.poseVariants?.[i];
      if (dbPv?.trackedJointsConfig) {
        const tj = dbPv.trackedJointsConfig;
        const set = new Set(tj.map((j) => j?.joint).filter(Boolean));
        if (set.has('left_hip') && set.has('right_knee') && !set.has('right_hip')) {
          insertAfterLeftHip(tj);
        }
      }
      touched = true;
    }

    if (touched) {
      fs.writeFileSync(fp, JSON.stringify(data, null, 2) + '\n', 'utf8');
      written++;
      console.log('Added right_hip mirror:', f);
    }
  }
  console.log('Files updated:', written);
}

main();
