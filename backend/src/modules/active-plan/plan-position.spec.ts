import { resolveCurrentProgramDay } from './plan-position';

function days1to7(): { dayNumber: number }[] {
  return Array.from({ length: 7 }, (_, i) => ({ dayNumber: i + 1 }));
}

/** Two calendar weeks, 7 days each — matches publish validation */
const twoCalendarWeeks = [
  { weekNumber: 1, days: days1to7() },
  { weekNumber: 2, days: days1to7() },
];

const sparseWeeks = [
  {
    weekNumber: 1,
    days: [{ dayNumber: 1 }, { dayNumber: 2 }],
  },
  {
    weekNumber: 2,
    days: [{ dayNumber: 1 }],
  },
];

describe('resolveCurrentProgramDay', () => {
  it('starts at the first day when there is no progress', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, []);

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
    expect(result.isProgramComplete).toBe(false);
  });

  it('stays on the current day when only a session is completed', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [
      { weekNumber: 1, dayNumber: 1, sessionId: 'session-a', status: 'completed' },
    ]);

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
    expect(result.completedDayCount).toBe(0);
  });

  it('moves to the next ordered day after a completed day sentinel', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [
      { weekNumber: 1, dayNumber: 1, sessionId: '__day__', status: 'completed' },
    ]);

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(2);
    expect(result.completedDayCount).toBe(1);
  });

  it('marks the program complete when the final ordered day is completed', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [
      { weekNumber: 1, dayNumber: 1, sessionId: '__day__', status: 'completed' },
      { weekNumber: 1, dayNumber: 2, sessionId: '__day__', status: 'completed' },
      { weekNumber: 2, dayNumber: 1, sessionId: '__day__', status: 'completed' },
    ]);

    expect(result.targetWeekNumber).toBe(2);
    expect(result.targetDayNumber).toBe(1);
    expect(result.isProgramComplete).toBe(true);
    expect(result.lastDay).toEqual({ weekNumber: 2, dayNumber: 1 });
  });

  it('calendar mode: anchors day one to the enrollment start date', () => {
    const result = resolveCurrentProgramDay(twoCalendarWeeks, [], {
      startDate: new Date('2026-04-29T14:00:00.000Z'),
      now: new Date('2026-04-29T20:00:00.000Z'),
      durationWeeks: 2,
    });

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
  });

  it('calendar mode: same calendar day stays on that slot even if day is marked completed', () => {
    const result = resolveCurrentProgramDay(
      twoCalendarWeeks,
      [{ weekNumber: 1, dayNumber: 1, sessionId: '__day__', status: 'completed' }],
      {
        startDate: new Date('2026-04-29T14:00:00.000Z'),
        now: new Date('2026-04-29T20:00:00.000Z'),
        durationWeeks: 2,
      },
    );

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
    expect(result.completedDayCount).toBe(1);
  });

  it('calendar mode: maps elapsed days to week/day within durationWeeks', () => {
    const result = resolveCurrentProgramDay(twoCalendarWeeks, [], {
      startDate: new Date('2026-04-29T14:00:00.000Z'),
      now: new Date('2026-05-01T08:00:00.000Z'),
      durationWeeks: 2,
    });

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(3);
    expect(result.isProgramComplete).toBe(false);
  });

  it('calendar mode: program complete after durationWeeks * 7 calendar days', () => {
    const result = resolveCurrentProgramDay(twoCalendarWeeks, [], {
      startDate: new Date('2026-04-29T00:00:00.000Z'),
      now: new Date('2026-05-13T00:00:00.000Z'),
      durationWeeks: 2,
    });

    expect(result.isProgramComplete).toBe(true);
    expect(result.targetWeekNumber).toBe(2);
    expect(result.targetDayNumber).toBe(7);
  });

  it('legacy: without durationWeeks, uses flattened day order for date anchor', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [], {
      startDate: new Date('2026-04-29T14:00:00.000Z'),
      now: new Date('2026-05-01T08:00:00.000Z'),
    });

    expect(result.targetWeekNumber).toBe(2);
    expect(result.targetDayNumber).toBe(1);
  });
});
