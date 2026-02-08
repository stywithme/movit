import { Module } from '@nestjs/common';
import { MobileWorkoutsController } from './mobile-workouts.controller';
import { WorkoutsController } from './workouts.controller';

@Module({
  controllers: [WorkoutsController, MobileWorkoutsController],
})
export class WorkoutsModule {}
