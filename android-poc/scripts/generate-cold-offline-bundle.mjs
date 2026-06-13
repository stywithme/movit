#!/usr/bin/env node
/**
 * Build-time generator for cold_offline_bundle.json (FIX-4).
 *
 * Fetches REAL catalog + system messages from GET /api/mobile/sync and writes the
 * bundled first-install seed. Home is intentionally omitted (user-specific; comes from sync after login).
 *
 * Usage:
 *   node scripts/generate-cold-offline-bundle.mjs
 *   node scripts/generate-cold-offline-bundle.mjs --base-url https://back.mongz.online
 *   MOVIT_API_BASE_URL=http://192.168.1.10:4000 node scripts/generate-cold-offline-bundle.mjs
 *
 * Output: core/resources/src/commonMain/composeResources/files/cold_offline_bundle.json
 */

import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const DEFAULT_OUT = join(
  __dirname,
  '../core/resources/src/commonMain/composeResources/files/cold_offline_bundle.json',
);

function parseArgs(argv) {
  const args = { baseUrl: process.env.MOVIT_API_BASE_URL || 'https://back.mongz.online', out: DEFAULT_OUT };
  for (let i = 2; i < argv.length; i++) {
    if (argv[i] === '--base-url' && argv[i + 1]) args.baseUrl = argv[++i];
    else if (argv[i] === '--out' && argv[i + 1]) args.out = argv[++i];
  }
  return args;
}

function localizedName(obj, key = 'name') {
  const v = obj?.[key];
  if (!v || typeof v !== 'object') return { en: '', ar: '' };
  return { en: v.en ?? '', ar: v.ar ?? '' };
}

function str(obj, key) {
  const v = obj?.[key];
  return typeof v === 'string' ? v : '';
}

function intOrNull(obj, key) {
  const v = obj?.[key];
  return typeof v === 'number' ? v : null;
}

function mapWorkoutLevel(obj) {
  if (!obj || typeof obj !== 'object') return null;
  return {
    number: obj.number ?? 0,
    code: str(obj, 'code'),
    name: localizedName(obj),
  };
}

function mapProgramLevel(obj) {
  if (!obj || typeof obj !== 'object') return null;
  return {
    number: obj.number ?? 0,
    code: str(obj, 'code'),
    name: localizedName(obj),
  };
}

function toExploreLevel(level) {
  return {
    number: level.number ?? 0,
    code: level.code ?? '',
    name: level.name ?? { en: '', ar: '' },
  };
}

/** Mirrors SyncCatalogMapper.kt — keep in sync when mapping rules change. */
function mapSyncPayloadToExploreSlice(payload) {
  const workouts = (payload.workoutTemplates ?? [])
    .map((raw) => {
      const obj = raw;
      const id = str(obj, 'id');
      if (!id) return null;
      const featured = obj.isFeatured === true;
      const exerciseCount = Array.isArray(obj.exercises) ? obj.exercises.length : 0;
      return {
        featured,
        dto: {
          id,
          slug: str(obj, 'slug'),
          name: localizedName(obj),
          level: mapWorkoutLevel(obj.level),
          estimatedDurationMin: intOrNull(obj, 'estimatedDurationMin'),
          coverImageUrl: obj.coverImageUrl ?? null,
          exerciseCount,
          updatedAt: str(obj, 'updatedAt'),
        },
      };
    })
    .filter(Boolean)
    .sort((a, b) => {
      if (a.featured !== b.featured) return a.featured ? -1 : 1;
      return (b.dto.updatedAt || '').localeCompare(a.dto.updatedAt || '');
    })
    .map((x) => x.dto);

  const programs = (payload.programs ?? [])
    .map((raw) => {
      const obj = raw;
      const id = str(obj, 'id');
      if (!id) return null;
      return {
        featured: obj.isFeatured === true,
        dto: {
          id,
          slug: str(obj, 'slug'),
          name: localizedName(obj),
          levelMin: mapProgramLevel(obj.levelMin),
          levelMax: mapProgramLevel(obj.levelMax),
          durationWeeks: obj.durationWeeks ?? 0,
          coverImageUrl: obj.coverImageUrl ?? null,
          updatedAt: str(obj, 'updatedAt'),
        },
      };
    })
    .filter(Boolean)
    .sort((a, b) => {
      if (a.featured !== b.featured) return a.featured ? -1 : 1;
      return (b.dto.updatedAt || '').localeCompare(a.dto.updatedAt || '');
    })
    .map((x) => x.dto);

  const exercises = (payload.exercises ?? [])
    .map((raw) => {
      const obj = raw;
      const id = str(obj, 'id');
      const slug = str(obj, 'slug');
      if (!id && !slug) return null;
      const category = obj.category;
      return {
        featured: obj.isFeatured === true,
        dto: {
          id,
          slug,
          name: localizedName(obj),
          categoryCode: category?.code ?? null,
          categoryName: category ? localizedName(category) : null,
          musclesCount: Array.isArray(obj.muscles) ? obj.muscles.length : 0,
          imageUrl: obj.imageUrl ?? null,
          updatedAt: str(obj, 'updatedAt'),
        },
      };
    })
    .filter(Boolean)
    .sort((a, b) => {
      if (a.featured !== b.featured) return a.featured ? -1 : 1;
      return (b.dto.updatedAt || '').localeCompare(a.dto.updatedAt || '');
    })
    .map((x) => x.dto);

  const byNumber = new Map();
  for (const w of workouts) {
    if (w.level) byNumber.set(w.level.number, toExploreLevel(w.level));
  }
  for (const p of programs) {
    if (p.levelMin) byNumber.set(p.levelMin.number, toExploreLevel(p.levelMin));
    if (p.levelMax) byNumber.set(p.levelMax.number, toExploreLevel(p.levelMax));
  }
  const levels = [...byNumber.values()].sort((a, b) => a.number - b.number);

  return {
    levels,
    programs,
    workoutTemplates: workouts,
    exercises,
    deletedProgramIds: payload.deletedProgramIds ?? [],
    deletedWorkoutTemplateIds: payload.deletedWorkoutTemplateIds ?? [],
    deletedExerciseIds: payload.deletedExerciseIds ?? [],
  };
}

function mapSystemMessages(messages) {
  return (messages ?? [])
    .map((m) => ({
      code: m.code ?? '',
      content: localizedName(m, 'content'),
      updatedAt: m.updatedAt ?? '',
    }))
    .filter((m) => m.code.length > 0)
    .sort((a, b) => a.code.localeCompare(b.code));
}

async function main() {
  const { baseUrl, out } = parseArgs(process.argv);
  const url = `${baseUrl.replace(/\/$/, '')}/api/mobile/sync`;
  console.log(`Fetching ${url} ...`);

  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} from ${url}`);
  }

  const body = await response.json();
  if (!body.success || !body.data) {
    throw new Error(`Unexpected sync response: ${JSON.stringify(body).slice(0, 200)}`);
  }

  const explore = mapSyncPayloadToExploreSlice(body.data);
  const systemMessages = mapSystemMessages(body.data.systemMessages);

  const bundle = {
    home: null,
    explore,
    systemMessages,
  };

  writeFileSync(out, `${JSON.stringify(bundle, null, 2)}\n`, 'utf8');

  console.log('Wrote', out);
  console.log(
    `  programs=${explore.programs.length} workouts=${explore.workoutTemplates.length} ` +
      `exercises=${explore.exercises.length} systemMessages=${systemMessages.length}`,
  );
  console.log('  home=null (user-specific; not bundled)');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
