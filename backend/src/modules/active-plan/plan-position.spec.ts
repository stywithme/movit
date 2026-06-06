import {
  resolveCurrentProgramDay,
  resolveTrainingPositionMeta,
  countTrainingDaySlots,
  getProgramCalendarDayIndex,
} from './plan-position';

const sparseWeeks = [
  {
    weekNumber: 1,
    days: [
      { dayNumber: 1, plannedWorkouts: [{ id: 'a' }, { id: 'a2' }] },
      { dayNumber: 2, plannedWorkouts: [{ id: 'b' }] },
    ],
  },
  {
    weekNumber: 2,
    days: [{ dayNumber: 1, plannedWorkouts: [{ id: 'c' }] }],
  },
];

describe('resolveCurrentProgramDay (completion-based)', () => {
  it('starts at the first day when there is no progress', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, []);
    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
    expect(result.isProgramComplete).toBe(false);
  });

  it('stays on the current day when not all planned workouts on that day are completed', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a', status: 'completed' },
    ]);
    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(1);
  });

  it('moves to the next day when all planned workouts on the current day are completed', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a', status: 'completed' },
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a2', status: 'completed' },
    ]);
    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(2);
  });

  it('advances when a day has no planned workouts and __day__ is completed', () => {
    const weeksNoSessions = [
      {
        weekNumber: 1,
        days: [
          { dayNumber: 1, plannedWorkouts: [] },
          { dayNumber: 2, plannedWorkouts: [{ id: 'b' }] },
        ],
      },
    ];
    const result = resolveCurrentProgramDay(weeksNoSessions, [
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: '__day__', status: 'completed' },
    ]);
    expect(result.targetWeekNumber).toBe(1);
    expect(result.targetDayNumber).toBe(2);
  });

  it('marks the program complete when the final ordered day planned workouts are done', () => {
    const result = resolveCurrentProgramDay(sparseWeeks, [
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a', status: 'completed' },
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a2', status: 'completed' },
      { weekNumber: 1, dayNumber: 2, plannedWorkoutId: 'b', status: 'completed' },
      { weekNumber: 2, dayNumber: 1, plannedWorkoutId: 'c', status: 'completed' },
    ]);
    expect(result.isProgramComplete).toBe(true);
  });

  it('applies week restart when 3?29 days since last planned workout', () => {
    const now = new Date('2026-05-10T12:00:00.000Z');
    const last = new Date('2026-05-05T12:00:00.000Z'); // 5 days
    const progress = [
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a', status: 'completed' },
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a2', status: 'completed' },
    ];
    const meta = resolveTrainingPositionMeta(sparseWeeks, progress, {
      lastWorkoutCompletedAt: last,
      now,
      durationWeeks: 2,
    });
    expect(meta.naturalIndex).toBe(1);
    expect(meta.snappedIndex).toBe(0);
    expect(meta.calendarDaysSinceLastWorkout).toBe(5);
    expect(meta.position.targetWeekNumber).toBe(1);
    expect(meta.position.targetDayNumber).toBe(1);
  });

  it('applies program restart when 30+ days since last planned workout', () => {
    const now = new Date('2026-06-15T12:00:00.000Z');
    const last = new Date('2026-05-01T12:00:00.000Z');
    const progress = [
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a', status: 'completed' },
      { weekNumber: 1, dayNumber: 1, plannedWorkoutId: 'a2', status: 'completed' },
      { weekNumber: 1, dayNumber: 2, plannedWorkoutId: 'b', status: 'completed' },
    ];
    const meta = resolveTrainingPositionMeta(sparseWeeks, progress, {
      lastWorkoutCompletedAt: last,
      now,
      durationWeeks: 2,
    });
    expect(meta.naturalIndex).toBe(2);
    expect(meta.snappedIndex).toBe(0);
    expect(meta.position.targetWeekNumber).toBe(1);
    expect(meta.position.targetDayNumber).toBe(1);
  });

  it('countTrainingDaySlots sums non-rest template days', () => {
    const weeks = [
      {
        weekNumber: 1,
        days: [
          { dayNumber: 1, isRestDay: true, plannedWorkouts: [] },
          { dayNumber: 2, plannedWorkouts: [{ id: 'x' }] },
        ],
      },
    ];
    expect(countTrainingDaySlots(weeks as any)).toBe(1);
  });
});

describe('getProgramCalendarDayIndex', () => {
  it('counts UTC calendar days between anchors', () => {
    const n = getProgramCalendarDayIndex(
      new Date('2026-04-29T14:00:00.000Z'),
      new Date('2026-05-01T08:00:00.000Z'),
    );
    expect(n).toBe(2);
  });
});
