import { Injectable, Logger } from '@nestjs/common';
import { InjectQueue } from '@nestjs/bullmq';
import type { Queue } from 'bullmq';
import { Cron, CronExpression } from '@nestjs/schedule';
import { ANALYTICS_QUEUE } from './analytics.constants';

@Injectable()
export class AnalyticsService {
  private readonly logger = new Logger(AnalyticsService.name);

  constructor(@InjectQueue(ANALYTICS_QUEUE) private readonly queue: Queue) {}

  async enqueueWorkoutExecutionAnalytics(userId: string, workoutExecutionId: string) {
    await this.queue.add('workout_execution_analytics', { userId, workoutExecutionId }, { removeOnComplete: true });
  }

  @Cron(CronExpression.EVERY_DAY_AT_3AM)
  async scheduleDailySummary() {
    this.logger.log('Scheduling daily analytics summary');
    await this.queue.add('daily_summary', { date: new Date().toISOString() }, { removeOnComplete: true });
  }
}
