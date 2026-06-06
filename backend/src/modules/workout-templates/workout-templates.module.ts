import { Module } from '@nestjs/common';
import { MobileWorkoutTemplatesController } from './mobile-workout-templates.controller';
import { WorkoutTemplatesController } from './workout-templates.controller';

@Module({
  controllers: [WorkoutTemplatesController, MobileWorkoutTemplatesController],
})
export class WorkoutTemplatesModule {}
