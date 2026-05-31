import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { AnalyticsProcessor } from './analytics.processor';
import { AnalyticsService } from './analytics.service';
import { AdminAnalyticsController } from './admin-analytics.controller';
import { AdminReportsService } from './admin-reports.service';
import { ANALYTICS_QUEUE } from './analytics.constants';

@Module({
  imports: [BullModule.registerQueue({ name: ANALYTICS_QUEUE })],
  controllers: [AdminAnalyticsController],
  providers: [AnalyticsService, AnalyticsProcessor, AdminReportsService],
  exports: [AnalyticsService],
})
export class AnalyticsModule {}
