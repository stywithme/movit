-- Program phases are metadata only. The program calendar source of truth is
-- program_weeks -> program_days -> planned_workouts.

ALTER TABLE "program_phases"
  DROP COLUMN IF EXISTS "weeklyPattern";
