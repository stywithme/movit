#!/usr/bin/env node
/**
 * P1.3 dry-run: report rows that would collide on (userId, idempotencyKey)
 * or (userProgramId, idempotencyKey) before/after migrate.
 *
 * Usage (from backend/):
 *   node --env-file=.env prisma/scripts/p1-3-idempotency-dedupe-dry-run.mjs
 *   node --env-file=.env prisma/scripts/p1-3-idempotency-dedupe-dry-run.mjs --apply
 *
 * --apply nulls out older duplicate keys (keeps newest). Safe when keys are null.
 * Prefer running against a production clone first.
 */

import pg from 'pg';

const apply = process.argv.includes('--apply');
const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  console.error('DATABASE_URL is required');
  process.exit(1);
}

const client = new pg.Client({ connectionString: databaseUrl });

async function main() {
  await client.connect();

  const reportDupes = await client.query(`
    SELECT "userId", "idempotencyKey", COUNT(*)::int AS cnt, array_agg(id ORDER BY "updatedAt" DESC) AS ids
    FROM "planned_workout_reports"
    WHERE "idempotencyKey" IS NOT NULL
    GROUP BY "userId", "idempotencyKey"
    HAVING COUNT(*) > 1
    ORDER BY cnt DESC
  `);

  const overrideDupes = await client.query(`
    SELECT "userProgramId", "idempotencyKey", COUNT(*)::int AS cnt, array_agg(id ORDER BY "createdAt" DESC) AS ids
    FROM "user_program_overrides"
    WHERE "idempotencyKey" IS NOT NULL
    GROUP BY "userProgramId", "idempotencyKey"
    HAVING COUNT(*) > 1
    ORDER BY cnt DESC
  `);

  console.log(
    JSON.stringify(
      {
        mode: apply ? 'apply' : 'dry-run',
        plannedWorkoutReportDuplicateGroups: reportDupes.rowCount,
        userProgramOverrideDuplicateGroups: overrideDupes.rowCount,
        reports: reportDupes.rows,
        overrides: overrideDupes.rows,
      },
      null,
      2,
    ),
  );

  if (!apply) {
    console.error('\nDry-run only. Re-run with --apply to null older duplicate keys.');
    return;
  }

  if (reportDupes.rowCount === 0 && overrideDupes.rowCount === 0) {
    console.error('Nothing to apply.');
    return;
  }

  await client.query('BEGIN');
  try {
    const reportResult = await client.query(`
      WITH ranked AS (
        SELECT id, ROW_NUMBER() OVER (
          PARTITION BY "userId", "idempotencyKey"
          ORDER BY "updatedAt" DESC NULLS LAST, "createdAt" DESC NULLS LAST, id DESC
        ) AS rn
        FROM "planned_workout_reports"
        WHERE "idempotencyKey" IS NOT NULL
      )
      UPDATE "planned_workout_reports" AS p
      SET "idempotencyKey" = NULL
      FROM ranked r
      WHERE p.id = r.id AND r.rn > 1
    `);
    const overrideResult = await client.query(`
      WITH ranked AS (
        SELECT id, ROW_NUMBER() OVER (
          PARTITION BY "userProgramId", "idempotencyKey"
          ORDER BY "createdAt" DESC NULLS LAST, id DESC
        ) AS rn
        FROM "user_program_overrides"
        WHERE "idempotencyKey" IS NOT NULL
      )
      UPDATE "user_program_overrides" AS o
      SET "idempotencyKey" = NULL
      FROM ranked r
      WHERE o.id = r.id AND r.rn > 1
    `);
    await client.query('COMMIT');
    console.error(
      `Applied: nulled ${reportResult.rowCount} report key(s), ${overrideResult.rowCount} override key(s).`,
    );
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  }
}

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(async () => {
    await client.end().catch(() => undefined);
  });
