import { validateCalendarProgramStructure } from './calendar-program-structure';

function buildWeek(weekNumber: number) {
  return {
    weekNumber,
    days: Array.from({ length: 7 }, (_, i) => ({
      dayNumber: i + 1,
      isRestDay: false,
    })),
  };
}

describe('validateCalendarProgramStructure', () => {
  it('accepts a full week of training days numbered 1..7', () => {
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

  it('throws when a week has no training days', () => {
    const bad = {
      weekNumber: 1,
      days: Array.from({ length: 7 }, (_, i) => ({
        dayNumber: i + 1,
        isRestDay: true,
      })),
    };
    expect(() => validateCalendarProgramStructure(1, [bad])).toThrow(/at least one training day/);
  });

  it('throws when dayNumbers are not sequential from 1', () => {
    const bad = {
      weekNumber: 1,
      days: [
        { dayNumber: 1, isRestDay: false },
        { dayNumber: 3, isRestDay: false },
      ],
    };
    expect(() => validateCalendarProgramStructure(1, [bad])).toThrow(/sequentially/);
  });

  it('accepts interleaved rest days when all days are numbered 1..N', () => {
    const ok = {
      weekNumber: 1,
      days: [
        { dayNumber: 1, isRestDay: false, dayType: 'training' },
        { dayNumber: 2, isRestDay: true, dayType: 'rest' },
        { dayNumber: 3, isRestDay: false, dayType: 'training' },
      ],
    };
    expect(() => validateCalendarProgramStructure(1, [ok])).not.toThrow();
  });

  it('accepts fewer than 7 days when training slots are 1..N', () => {
    const ok = {
      weekNumber: 1,
      days: [
        { dayNumber: 1, isRestDay: false },
        { dayNumber: 2, isRestDay: false },
      ],
    };
    expect(() => validateCalendarProgramStructure(1, [ok])).not.toThrow();
  });
});
