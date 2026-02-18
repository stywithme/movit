import { Module } from '@nestjs/common';
import { ReassessmentController } from './reassessment.controller';
import { ReassessmentSchedulerService } from './reassessment-scheduler.service';

@Module({
  controllers: [ReassessmentController],
  providers: [ReassessmentSchedulerService],
})
export class ReassessmentModule {}
