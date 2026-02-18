/**
 * Reassessment Scheduler Service
 *
 * Cron jobs for:
 *   1. Marking overdue reassessments (daily at 2 AM)
 *   2. Checking periodic reassessment needs for active users (daily at 5 AM)
 */

import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { reassessmentService } from './reassessment.service';
import { getPrisma } from '@/lib/prisma/client';

@Injectable()
export class ReassessmentSchedulerService {
  private readonly logger = new Logger(ReassessmentSchedulerService.name);

  /**
   * Check for overdue reassessments and update their status.
   * Runs daily at 2 AM.
   */
  @Cron(CronExpression.EVERY_DAY_AT_2AM)
  async handleOverdueCheck() {
    try {
      const count = await reassessmentService.checkOverdue();
      if (count > 0) {
        this.logger.log(`Marked ${count} reassessment(s) as overdue`);
      }
    } catch (error) {
      this.logger.error('Failed to check overdue reassessments', error);
    }
  }

  /**
   * Check if any active users need a periodic reassessment.
   * Runs daily at 5 AM. Scans users with active programs.
   */
  @Cron(CronExpression.EVERY_DAY_AT_5AM)
  async handlePeriodicCheck() {
    try {
      const prisma = await getPrisma();

      // Get all users with an active plan
      const activePlans = await prisma.activePlan.findMany({
        where: { status: 'active' },
        select: { userId: true },
      });

      let scheduled = 0;
      for (const plan of activePlans) {
        const wasScheduled = await reassessmentService.checkPeriodicReassessment(plan.userId);
        if (wasScheduled) scheduled++;
      }

      if (scheduled > 0) {
        this.logger.log(`Scheduled ${scheduled} periodic reassessment(s) for active users`);
      }
    } catch (error) {
      this.logger.error('Failed to check periodic reassessments', error);
    }
  }
}
