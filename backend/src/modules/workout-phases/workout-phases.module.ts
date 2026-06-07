import { Module } from '@nestjs/common';
import { WorkoutPhasesController } from './workout-phases.controller';

@Module({
  controllers: [WorkoutPhasesController],
})
export class WorkoutPhasesModule {}
