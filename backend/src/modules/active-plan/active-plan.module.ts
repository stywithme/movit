import { Module } from '@nestjs/common';
import { ActivePlanController } from './active-plan.controller';

@Module({
  controllers: [ActivePlanController],
})
export class ActivePlanModule {}
