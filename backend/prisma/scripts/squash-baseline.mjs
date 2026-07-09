/**
 * Squash all Prisma migrations into a single production baseline.
 * Archives old migration folders to prisma/migrations_archive/.
 *
 * Usage (from backend/):
 *   DATABASE_URL=postgresql://... node prisma/scripts/squash-baseline.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const backendRoot = path.resolve(__dirname, '../..');
const migDir = path.join(backendRoot, 'prisma/migrations');
const archiveDir = path.join(backendRoot, 'prisma/migrations_archive');
const baselineName = '20260622140000_baseline_production_v1';
const baselineDir = path.join(migDir, baselineName);
const tempSql = path.join(migDir, '_baseline_temp.sql');

if (!process.env.DATABASE_URL) {
  console.error('DATABASE_URL is required for prisma migrate diff');
  process.exit(1);
}

const diff = spawnSync(
  'npx',
  ['prisma', 'migrate', 'diff', '--from-empty', '--to-schema', 'prisma/schema.prisma', '--script'],
  { cwd: backendRoot, encoding: 'utf8', shell: true, env: process.env },
);

if (diff.status !== 0) {
  console.error(diff.stderr || diff.stdout);
  process.exit(diff.status ?? 1);
}

const header = [
  '-- Production baseline v1 (squashed from schema.prisma)',
  '-- DDL only. Reference data is seeded via npm run seed:base / seed:full.',
  '',
  'CREATE EXTENSION IF NOT EXISTS "pgcrypto";',
  '',
].join('\n');

fs.mkdirSync(archiveDir, { recursive: true });
fs.mkdirSync(baselineDir, { recursive: true });
fs.writeFileSync(path.join(baselineDir, 'migration.sql'), header + diff.stdout);

for (const ent of fs.readdirSync(migDir, { withFileTypes: true })) {
  if (!ent.isDirectory() || ent.name === baselineName) continue;
  const src = path.join(migDir, ent.name);
  const dst = path.join(archiveDir, ent.name);
  if (fs.existsSync(dst)) fs.rmSync(dst, { recursive: true, force: true });
  fs.renameSync(src, dst);
  console.log(`Archived: ${ent.name}`);
}

if (fs.existsSync(tempSql)) fs.unlinkSync(tempSql);

console.log(`Baseline written: prisma/migrations/${baselineName}/migration.sql`);
console.log(`Archived migrations: prisma/migrations_archive/`);
