import { resolveCurrentProgramDay } from './plan-position';

const weeks = [
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
    const result = resolveCurrentProgramDay(weeks, []);

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
    expect(result.isProgramComplete).toBe(false);
  });

  it('stays on the current day when only a session is completed', () => {
    const result = resolveCurrentProgramDay(weeks, [
      { weekNumber: 1, dayNumber: 1, sessionId: 'session-a', status: 'completed' },
    ]);

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
    expect(result.completedDayCount).toBe(0);
  });

  it('moves to the next ordered day after a completed day sentinel', () => {
    const result = resolveCurrentProgramDay(weeks, [
      { weekNumber: 1, dayNumber: 1, sessionId: '__day__', status: 'completed' },
    ]);

    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(2);
    expect(result.completedDayCount).toBe(1);
  });

  it('marks the program complete when the final ordered day is completed', () => {
    const result = resolveCurrentProgramDay(weeks, [
      { weekNumber: 1, dayNumber: 1, sessionId: '__day__', status: 'completed' },
      { weekNumber: 1, dayNumber: 2, sessionId: '__day__', status: 'completed' },
      { weekNumber: 2, dayNumber: 1, sessionId: '__day__', status: 'completed' },
    ]);

    expect(result.targetWeekNumber).toBe(2);
    expect(result.targetDayNumber).toBe(1);
    expect(result.isProgramComplete).toBe(true);
    expect(result.lastDay).toEqual({ weekNumber: 2, dayNumber: 1 });
  });
});
