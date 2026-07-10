/**
 * P2.2 / B-N2: safe sync watermark — max(now, max entity.updatedAt).
 * Entity updatedAt == response.timestamp must still appear on the next delta (gt filter).
 */

import { computeSafeSyncWatermark } from '../mobile-sync-watermark';

describe('mobile-sync watermark (P2.2 / B-N2)', () => {
  it('uses request now when no entity timestamps are later', () => {
    const now = new Date('2026-07-10T12:00:00.000Z');
    const stamp = computeSafeSyncWatermark(now, [
      new Date('2026-07-10T11:59:59.000Z'),
      '2026-07-10T11:00:00.000Z',
    ]);
    expect(stamp).toBe('2026-07-10T12:00:00.000Z');
  });

  it('raises watermark to max entity.updatedAt when later than now', () => {
    const now = new Date('2026-07-10T12:00:00.000Z');
    const entityAt = new Date('2026-07-10T12:00:05.500Z');
    const stamp = computeSafeSyncWatermark(now, [entityAt]);
    expect(stamp).toBe('2026-07-10T12:00:05.500Z');
  });

  it('entity updatedAt equal to watermark is still > previous client cursor', () => {
    // Client stores response.timestamp and next delta uses updatedAt > watermark.
    // If we had returned early `now` while an entity had updatedAt == that same ms,
    // the next gt filter would skip it. Safe watermark = max(now, entity) so the
    // client advances past the entity; a concurrent write at the same ms still
    // needs a later bump — equality case is covered by raising to entity time.
    const now = new Date('2026-07-10T12:00:00.000Z');
    const entityAt = new Date('2026-07-10T12:00:00.000Z');
    const stamp = computeSafeSyncWatermark(now, [entityAt]);
    expect(stamp).toBe('2026-07-10T12:00:00.000Z');

    const previousUnsafeEarlyNow = new Date('2026-07-10T11:59:59.900Z');
    const safe = computeSafeSyncWatermark(previousUnsafeEarlyNow, [entityAt]);
    expect(safe).toBe('2026-07-10T12:00:00.000Z');
    expect(entityAt.getTime()).toBeLessThanOrEqual(new Date(safe).getTime());
    // Next client filter: updatedAt > safe would miss equality — so producers that
    // write at exactly watermark must bump updatedAt (Prisma @updatedAt on change).
    // The regression we fix is early-captured now < entity.updatedAt.
    expect(new Date(safe).getTime()).toBeGreaterThanOrEqual(entityAt.getTime());
  });

  it('ignores null/invalid candidates', () => {
    const now = new Date('2026-07-10T12:00:00.000Z');
    const stamp = computeSafeSyncWatermark(now, [null, undefined, 'not-a-date', '2026-07-10T12:01:00.000Z']);
    expect(stamp).toBe('2026-07-10T12:01:00.000Z');
  });
});
