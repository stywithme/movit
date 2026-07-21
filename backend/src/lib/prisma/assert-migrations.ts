/**
 * Fail-fast when Prisma migrations are pending or failed.
 * Prevents serving clients against a stale schema (e.g. P2022 500s on /mobile/sync).
 */

import { execSync } from 'child_process';
import path from 'path';

function isDevLike(): boolean {
  const env = (process.env.NODE_ENV ?? 'development').toLowerCase();
  return env !== 'production' && env !== 'prod';
}

/**
 * Runs `prisma migrate status`. Pending/failed wording or non-zero exit → abort.
 */
export function assertPrismaMigrationsApplied(): void {
  const cwd = process.cwd();

  let combined = '';
  let exitCode = 0;
  try {
    combined = execSync('npx prisma migrate status', {
      cwd,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
      env: process.env,
    });
  } catch (err: unknown) {
    const e = err as { status?: number; stdout?: string; stderr?: string; message?: string };
    exitCode = typeof e.status === 'number' ? e.status : 1;
    combined = `${e.stdout ?? ''}\n${e.stderr ?? ''}\n${e.message ?? ''}`;
  }

  const pending =
    /have not yet been applied/i.test(combined) ||
    /Following migration/i.test(combined) ||
    /failed migrations/i.test(combined) ||
    /migrate found failed/i.test(combined) ||
    /Database schema is not up to date/i.test(combined);

  if (!pending && exitCode === 0) {
    return;
  }
  if (!pending && exitCode !== 0) {
    console.error(
      `\n🚨 [migrations] prisma migrate status failed (exit ${exitCode}).\n` +
        `cwd=${path.resolve(cwd)}\n${combined}\n`,
    );
    if (isDevLike()) {
      process.exit(1);
    }
    return;
  }

  console.error(
    `\n🚨🚨🚨 FATAL: Prisma migrations are pending or failed.\n` +
      `The API must not start against a stale schema (clients get 500s like P2022).\n` +
      `Fix: cd backend && npx prisma migrate deploy\n\n` +
      `${combined}\n🚨🚨🚨\n`,
  );
  process.exit(1);
}
