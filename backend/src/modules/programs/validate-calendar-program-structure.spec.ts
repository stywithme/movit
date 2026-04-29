import { validateCalendarProgramStructure } from './calendar-program-structure';

function buildWeek(weekNumber: number) {
  return {
    weekNumber,
    days: Array.from({ length: 7 }, (_, i) => ({ dayNumber: i + 1 })),
  };
}

describe('validateCalendarProgramStructure', () => {
  it('accepts a full calendar grid for all weeks', () => {
    expect(() => validateCalendarProgramStructure(2, [buildWeek(1), buildWeek(2)])).not.toThrow();
  });

  it('throws when a week is missing', () => {
    expect(() => validateCalendarProgramStructure(2, [buildWeek(1)])).toThrow(/missing week 2/);
  });

  it('throws when a week is outside the configured duration', () => {
    expect(() => validateCalendarProgramStructure(1, [buildWeek(1), buildWeek(2)])).toThrow(
      /outside durationWeeks 1/,
    );
  });

  it('throws when week numbers are duplicated', () => {
    expect(() => validateCalendarProgramStructure(2, [buildWeek(1), buildWeek(1)])).toThrow(
      /duplicate week numbers/,
    );
  });

  it('throws when a week does not have 7 days', () => {
    const bad = { weekNumber: 1, days: buildWeek(1).days.slice(0, 5) };
    expect(() => validateCalendarProgramStructure(1, [bad])).toThrow(/exactly 7 days/);
  });

  it('throws when a day number is missing in a week', () => {
    const bad = {
      weekNumber: 1,
      days: [1, 2, 3, 4, 5, 6, 6].map((d) => ({ dayNumber: d })),
    };
    expect(() => validateCalendarProgramStructure(1, [bad])).toThrow(/include day 7/);
  });
});
