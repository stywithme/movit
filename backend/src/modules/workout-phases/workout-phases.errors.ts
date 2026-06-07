export class WorkoutPhaseInUseError extends Error {
  readonly templateCount: number;

  constructor(templateCount: number) {
    super(`Workout phase is used by ${templateCount} workout template(s)`);
    this.name = 'WorkoutPhaseInUseError';
    this.templateCount = templateCount;
  }
}
