/**
 * Build exercise JSON files for the "Missing Exercises" spreadsheet group.
 *
 * Usage:
 *   npx tsx prisma/build-missing-exercises-json.ts
 *   npx tsx prisma/build-missing-exercises-json.ts --include-existing
 */
import * as fs from 'fs/promises';
import * as path from 'path';
import { validateExerciseConfig } from '../src/lib/types/android-schema';
import {
  buildExerciseJsonFromRows,
  buildSlug,
  CANONICAL_EXISTING_SLUGS,
  CANONICAL_EXISTING_IDS,
  parseChecksCsv,
  parseCsv,
  parseRomCsv,
} from './lib/spreadsheet-exercise-builder';

const GROUP_TAG = 'missing_exercises';
const DEFAULT_SOURCE_DIR = path.resolve(process.cwd(), '../New-Exercises');
const OUTPUT_DIR = path.resolve(
  process.cwd(),
  'prisma/Exercise-json/missing-exercises/exercises-from-db',
);

async function main() {
  const includeExisting = process.argv.includes('--include-existing');
  const sourceDir = process.argv.find((arg) => arg.startsWith('--source='))?.split('=')[1]
    ? path.resolve(process.cwd(), process.argv.find((arg) => arg.startsWith('--source='))!.split('=')[1])
    : DEFAULT_SOURCE_DIR;

  const exercisesCsv = await fs.readFile(
    path.join(sourceDir, 'Missing Exercises - Exercises.csv'),
    'utf8',
  );
  const romCsv = await fs.readFile(
    path.join(sourceDir, 'Missing Exercises - Joint & ROM.csv'),
    'utf8',
  );
  const checksCsv = await fs.readFile(
    path.join(sourceDir, 'Missing Exercises - Checks.csv'),
    'utf8',
  );

  const exercises = parseCsv(exercisesCsv);
  const romById = new Map(parseRomCsv(romCsv).map((row) => [row.Exercise_ID, row]));
  const checksById = new Map(parseChecksCsv(checksCsv).map((row) => [row.Exercise_ID, row]));

  await fs.mkdir(OUTPUT_DIR, { recursive: true });

  const errors: string[] = [];
  const written: string[] = [];
  const skipped: string[] = [];

  for (const exerciseRow of exercises) {
    const exerciseId = exerciseRow.Exercise_ID?.trim();
    if (!exerciseId) continue;

    const exerciseName = exerciseRow['Exercise Name']?.trim() || exerciseId;
    const slug = buildSlug(exerciseId, exerciseName);

    if (!includeExisting && (CANONICAL_EXISTING_SLUGS.has(slug) || CANONICAL_EXISTING_IDS.has(exerciseId))) {
      skipped.push(`${exerciseId} (${slug}) — already in exercises-from-db`);
      continue;
    }

    const romRow = romById.get(exerciseId);
    const checksRow = checksById.get(exerciseId);
    if (!romRow) {
      errors.push(`${exerciseId}: missing ROM row`);
      continue;
    }
    if (!checksRow) {
      errors.push(`${exerciseId}: missing checks row`);
      continue;
    }

    const json = buildExerciseJsonFromRows(exerciseRow, romRow, checksRow, {
      groupTag: GROUP_TAG,
    });

    const validationErrors = validateExerciseConfig(json as never);
    if (validationErrors.length > 0) {
      errors.push(`${slug}:\n  - ${validationErrors.join('\n  - ')}`);
      continue;
    }

    const outPath = path.join(OUTPUT_DIR, `${slug}.json`);
    await fs.writeFile(outPath, `${JSON.stringify(json, null, 2)}\n`, 'utf8');
    written.push(slug);
  }

  const writtenSet = new Set(written);
  for (const file of await fs.readdir(OUTPUT_DIR)) {
    if (!file.endsWith('.json')) continue;
    const slug = path.basename(file, '.json');
    if (!writtenSet.has(slug)) {
      await fs.unlink(path.join(OUTPUT_DIR, file));
      console.log(`Removed stale file: ${file}`);
    }
  }

  console.log(`Source: ${sourceDir}`);
  console.log(`Output: ${OUTPUT_DIR}`);
  console.log(`Written: ${written.length} exercise JSON files`);
  for (const slug of written) console.log(`  - ${slug}`);

  if (skipped.length > 0) {
    console.log(`Skipped (already canonical): ${skipped.length}`);
    for (const line of skipped) console.log(`  - ${line}`);
  }

  if (errors.length > 0) {
    console.error(`\nValidation/build errors (${errors.length}):`);
    for (const err of errors) console.error(`- ${err}`);
    process.exit(1);
  }
}

main().catch((error) => {
  console.error('Build missing exercises JSON failed:', error);
  process.exit(1);
});
