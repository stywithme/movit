import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { AnalyticsProcessor } from './analytics.processor';
import { AnalyticsService } from './analytics.service';
import { ANALYTICS_QUEUE } from './analytics.constants';

@Module({
  imports: [BullModule.registerQueue({ name: ANALYTICS_QUEUE })],
  providers: [AnalyticsService, AnalyticsProcessor],
  exports: [AnalyticsService],
})
export class AnalyticsModule {}
