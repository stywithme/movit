/**
 * Safe sync watermark helper (B-N2 / P2.2).
 * Kept free of Prisma so unit tests do not need DATABASE_URL.
 */

/**
 * Response watermark must not sit strictly before any entity `updatedAt`
 * returned in this payload. Clients use `gt` on the next delta —
 * so timestamp = max(now, max entity.updatedAt).
 */
export function computeSafeSyncWatermark(
  requestStartedAt: Date,
  entityUpdatedAts: Array<Date | string | null | undefined>,
): string {
  let maxMs = requestStartedAt.getTime();
  for (const value of entityUpdatedAts) {
    if (value == null) continue;
    const ms = value instanceof Date ? value.getTime() : new Date(value).getTime();
    if (!Number.isNaN(ms) && ms > maxMs) {
      maxMs = ms;
    }
  }
  return new Date(maxMs).toISOString();
}
