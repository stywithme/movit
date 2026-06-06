import { Module } from '@nestjs/common';
import { MobilePlannedWorkoutsController } from './mobile-planned-workouts.controller';
import { MobileWorkoutExecutionsController } from './mobile-workout-executions.controller';

@Module({
  controllers: [MobileWorkoutExecutionsController, MobilePlannedWorkoutsController],
})
export class WorkoutExecutionsModule {}
