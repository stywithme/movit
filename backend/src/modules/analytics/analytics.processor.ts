import { Processor, WorkerHost } from '@nestjs/bullmq';
import { Logger } from '@nestjs/common';
import type { Job } from 'bullmq';
import { ANALYTICS_QUEUE } from './analytics.constants';

@Processor(ANALYTICS_QUEUE)
export class AnalyticsProcessor extends WorkerHost {
  private readonly logger = new Logger(AnalyticsProcessor.name);

  async process(job: Job): Promise<void> {
    switch (job.name) {
      case 'workout_execution_analytics':
        this.logger.log(`Processing workout execution analytics: ${JSON.stringify(job.data)}`);
        break;
      case 'daily_summary':
        this.logger.log(`Processing daily summary: ${JSON.stringify(job.data)}`);
        break;
      default:
        this.logger.warn(`Unknown analytics job: ${job.name}`);
    }
  }
}
