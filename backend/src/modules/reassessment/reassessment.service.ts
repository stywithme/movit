/**
 * Reassessment Service — Manages reassessment scheduling and triggers.
 *
 * Triggers (Section 8):
 *   - program_complete: User finishes active program
 *   - periodic: N weeks since last assessment (default: 6 weeks)
 *   - progression_trigger: A progression rule fires suggest_reassessment
 *   - manual: User requests reassessment
 */

import { getPrisma } from '@/lib/prisma/client';

const PERIODIC_REASSESSMENT_WEEKS = 6;

export interface ReassessmentData {
  id: string;
  userId: string;
  reason: string;
  scheduledDate: string;
  status: string;
  assessmentId: string | null;
  notes: string | null;
  createdAt: string;
}

export const reassessmentService = {
  /**
   * Get upcoming reassessments for a user.
   */
  async getUpcoming(userId: string): Promise<ReassessmentData[]> {
    const prisma = await getPrisma();

    const schedules = await prisma.reassessmentSchedule.findMany({
      where: { userId, status: { in: ['pending', 'overdue'] } },
      orderBy: { scheduledDate: 'asc' },
    });

    return schedules.map((s) => ({
      id: s.id,
      userId: s.userId,
      reason: s.reason,
      scheduledDate: s.scheduledDate.toISOString(),
      status: s.status,
      assessmentId: s.assessmentId,
      notes: s.notes,
      createdAt: s.createdAt.toISOString(),
    }));
  },

  /**
   * Get full reassessment history for a user.
   */
  async getHistory(userId: string): Promise<ReassessmentData[]> {
    const prisma = await getPrisma();

    const schedules = await prisma.reassessmentSchedule.findMany({
      where: { userId },
      orderBy: { scheduledDate: 'desc' },
      take: 20,
    });

    return schedules.map((s) => ({
      id: s.id,
      userId: s.userId,
      reason: s.reason,
      scheduledDate: s.scheduledDate.toISOString(),
      status: s.status,
      assessmentId: s.assessmentId,
      notes: s.notes,
      createdAt: s.createdAt.toISOString(),
    }));
  },

  /**
   * Schedule a reassessment for a user.
   */
  async schedule(
    userId: string,
    reason: string,
    scheduledDate: Date,
    notes?: string,
  ): Promise<ReassessmentData> {
    const prisma = await getPrisma();

    const schedule = await prisma.reassessmentSchedule.create({
      data: {
        userId,
        reason,
        scheduledDate,
        status: 'pending',
        notes: notes ?? null,
      },
    });

    return {
      id: schedule.id,
      userId: schedule.userId,
      reason: schedule.reason,
      scheduledDate: schedule.scheduledDate.toISOString(),
      status: schedule.status,
      assessmentId: schedule.assessmentId,
      notes: schedule.notes,
      createdAt: schedule.createdAt.toISOString(),
    };
  },

  /**
   * Mark a reassessment as completed (called after assessment upload).
   */
  async markCompleted(userId: string, assessmentId: string): Promise<void> {
    const prisma = await getPrisma();

    // Find the most recent pending schedule
    const schedule = await prisma.reassessmentSchedule.findFirst({
      where: { userId, status: { in: ['pending', 'overdue'] } },
      orderBy: { scheduledDate: 'asc' },
    });

    if (schedule) {
      await prisma.reassessmentSchedule.update({
        where: { id: schedule.id },
        data: { status: 'completed', assessmentId },
      });
    }
  },

  /**
   * Check for overdue reassessments and update statuses.
   * Called periodically by a cron job.
   */
  async checkOverdue(): Promise<number> {
    const prisma = await getPrisma();

    const result = await prisma.reassessmentSchedule.updateMany({
      where: {
        status: 'pending',
        scheduledDate: { lt: new Date() },
      },
      data: { status: 'overdue' },
    });

    return result.count;
  },

  /**
   * Check if a user needs a periodic reassessment.
   * Schedules one if the last assessment is older than PERIODIC_REASSESSMENT_WEEKS.
   */
  async checkPeriodicReassessment(userId: string): Promise<boolean> {
    const prisma = await getPrisma();

    // Check if there's already a pending reassessment
    const pending = await prisma.reassessmentSchedule.findFirst({
      where: { userId, status: { in: ['pending', 'overdue'] } },
    });

    if (pending) return false;

    // Check last assessment date
    const lastAssessment = await prisma.bodyScanResult.findFirst({
      where: { userId },
      orderBy: { completedAt: 'desc' },
    });

    if (!lastAssessment) return false;

    const weeksSinceAssessment =
      (Date.now() - lastAssessment.completedAt.getTime()) /
      (7 * 24 * 60 * 60 * 1000);

    if (weeksSinceAssessment >= PERIODIC_REASSESSMENT_WEEKS) {
      const scheduledDate = new Date(Date.now() + 24 * 60 * 60 * 1000);
      await prisma.reassessmentSchedule.create({
        data: {
          userId,
          reason: 'periodic',
          scheduledDate,
          status: 'pending',
          notes: `${PERIODIC_REASSESSMENT_WEEKS} weeks since last assessment`,
        },
      });
      return true;
    }

    return false;
  },
};
