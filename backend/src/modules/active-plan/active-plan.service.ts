/**
 * ActivePlan Service — Manages user's training schedule with ordered programs.
 *
 * Replaces the simple UserProgram.isActive boolean with a structured plan
 * that supports program sequencing, transitions, and auto-scheduling.
 */

import { getPrisma } from '@/lib/prisma/client';

// ── Types ──

export interface ActivePlanData {
  id: string;
  userId: string;
  status: string;
  programs: ActivePlanProgramData[];
  createdAt: string;
  updatedAt: string;
}

export interface ActivePlanProgramData {
  id: string;
  sortOrder: number;
  status: string;
  scheduledStartDate: string | null;
  actualStartDate: string | null;
  completedAt: string | null;
  program: {
    id: string;
    name: Record<string, string>;
    slug: string;
    type: string;
    durationWeeks: number;
    difficulty: string;
    coverImageUrl: string | null;
  } | null;
  progress: {
    completedDays: number;
    totalDays: number;
    currentWeek: number;
    currentDay: number;
  };
}

export interface TodayPlanData {
  activePlanStatus: string;
  currentProgram: {
    name: Record<string, string>;
    weekNumber: number;
    dayNumber: number;
    dayType: string;
    isRestDay: boolean;
    sessions: {
      id: string;
      name: Record<string, string>;
      sessionCategory: string | null;
      estimatedDurationMin: number | null;
      itemCount: number;
      isCompleted: boolean;
    }[];
  } | null;
  nextReassessment: {
    scheduledDate: string;
    reason: string;
  } | null;
}

// ── Service ──

export const activePlanService = {
  /**
   * Get or create the user's active plan.
   */
  async getOrCreate(userId: string): Promise<ActivePlanData> {
    const prisma = await getPrisma();

    let plan = await prisma.activePlan.findUnique({
      where: { userId },
      include: {
        programs: {
          orderBy: { sortOrder: 'asc' },
          include: {
            userProgram: {
              include: {
                program: true,
                progress: true,
              },
            },
          },
        },
      },
    });

    if (!plan) {
      plan = await prisma.activePlan.upsert({
        where: { userId },
        create: { userId, status: 'active' },
        update: {},
        include: {
          programs: {
            orderBy: { sortOrder: 'asc' },
            include: {
              userProgram: {
                include: {
                  program: true,
                  progress: true,
                },
              },
            },
          },
        },
      });
    }

    return {
      id: plan.id,
      userId: plan.userId,
      status: plan.status,
      programs: plan.programs.map((slot) => {
        const prog = slot.userProgram.program;
        const progressEntries = slot.userProgram.progress || [];
        // Count only __day__ sentinel entries as fully completed days
        const completedDaySentinels = progressEntries.filter(
          (p) => p.status === 'completed' && p.sessionId === '__day__',
        );
        const totalDays = prog ? prog.durationWeeks * 7 : 0;

        // Find current position from day-level sentinels
        const latestDay = completedDaySentinels.sort((a, b) => {
          if (a.weekNumber !== b.weekNumber) return b.weekNumber - a.weekNumber;
          return b.dayNumber - a.dayNumber;
        })[0];

        return {
          id: slot.id,
          sortOrder: slot.sortOrder,
          status: slot.status,
          scheduledStartDate: slot.scheduledStartDate?.toISOString() || null,
          actualStartDate: slot.actualStartDate?.toISOString() || null,
          completedAt: slot.completedAt?.toISOString() || null,
          program: prog
            ? {
                id: prog.id,
                name: prog.name as Record<string, string>,
                slug: prog.slug,
                type: prog.type,
                durationWeeks: prog.durationWeeks,
                difficulty: prog.difficulty,
                coverImageUrl: prog.coverImageUrl,
              }
            : null,
          progress: {
            completedDays: completedDaySentinels.length,
            totalDays,
            currentWeek: latestDay?.weekNumber ?? 1,
            currentDay: latestDay?.dayNumber ?? 1,
          },
        };
      }),
      createdAt: plan.createdAt.toISOString(),
      updatedAt: plan.updatedAt.toISOString(),
    };
  },

  /**
   * Add a program to the user's active plan via enrollment.
   */
  async enrollProgram(
    userId: string,
    programId: string,
  ): Promise<ActivePlanData> {
    const prisma = await getPrisma();

    // Ensure plan exists
    let plan = await prisma.activePlan.upsert({
      where: { userId },
      create: { userId, status: 'active' },
      update: {},
    });

    // Create UserProgram enrollment
    const userProgram = await prisma.userProgram.create({
      data: {
        userId,
        programId,
        isActive: true,
      },
    });

    // Deactivate previous UserPrograms
    await prisma.userProgram.updateMany({
      where: {
        userId,
        id: { not: userProgram.id },
        isActive: true,
      },
      data: { isActive: false },
    });

    // Get current max sortOrder
    const maxSlot = await prisma.activePlanProgram.findFirst({
      where: { activePlanId: plan.id },
      orderBy: { sortOrder: 'desc' },
    });
    const nextOrder = (maxSlot?.sortOrder ?? -1) + 1;

    // Mark previous active slot as completed
    await prisma.activePlanProgram.updateMany({
      where: { activePlanId: plan.id, status: 'active' },
      data: { status: 'completed', completedAt: new Date() },
    });

    // Add program to plan
    await prisma.activePlanProgram.create({
      data: {
        activePlanId: plan.id,
        userProgramId: userProgram.id,
        sortOrder: nextOrder,
        status: 'active',
        actualStartDate: new Date(),
      },
    });

    // Ensure plan is active
    await prisma.activePlan.update({
      where: { id: plan.id },
      data: { status: 'active' },
    });

    return this.getOrCreate(userId);
  },

  /**
   * Get today's training plan for the user.
   */
  async getTodayPlan(userId: string): Promise<TodayPlanData> {
    const prisma = await getPrisma();

    const plan = await prisma.activePlan.findUnique({
      where: { userId },
      include: {
        programs: {
          where: { status: 'active' },
          include: {
            userProgram: {
              include: {
                program: {
                  include: {
                    weeks: {
                      include: {
                        days: {
                          include: {
                            sessions: {
                              include: {
                                items: true,
                                reports: {
                                  where: { userId, status: 'completed' },
                                },
                              },
                            },
                          },
                        },
                      },
                    },
                  },
                },
                progress: true,
              },
            },
          },
        },
      },
    });

    // Get next reassessment
    const nextReassessment = await prisma.reassessmentSchedule.findFirst({
      where: { userId, status: 'pending' },
      orderBy: { scheduledDate: 'asc' },
    });

    if (!plan || plan.programs.length === 0) {
      return {
        activePlanStatus: plan?.status ?? 'none',
        currentProgram: null,
        nextReassessment: nextReassessment
          ? {
              scheduledDate: nextReassessment.scheduledDate.toISOString(),
              reason: nextReassessment.reason,
            }
          : null,
      };
    }

    const activeSlot = plan.programs[0];
    const program = activeSlot.userProgram.program;
    if (!program) {
      return {
        activePlanStatus: plan.status,
        currentProgram: null,
        nextReassessment: nextReassessment
          ? {
              scheduledDate: nextReassessment.scheduledDate.toISOString(),
              reason: nextReassessment.reason,
            }
          : null,
      };
    }

    // Determine current week/day from progress.
    // Only use '__day__' sentinel entries to determine completed days
    // (individual session entries don't mean the entire day is done).
    const progressEntries = activeSlot.userProgram.progress || [];
    const completedDaySentinels = progressEntries.filter(
      (p) => p.status === 'completed' && p.sessionId === '__day__',
    );

    // Calculate next training day
    const allWeeks = program.weeks.sort((a, b) => a.weekNumber - b.weekNumber);
    let targetWeek = 1;
    let targetDay = 1;

    if (completedDaySentinels.length > 0) {
      // Find the latest completed day (by week then day number)
      const latest = completedDaySentinels.sort((a, b) => {
        if (a.weekNumber !== b.weekNumber) return b.weekNumber - a.weekNumber;
        return b.dayNumber - a.dayNumber;
      })[0];
      targetWeek = latest.weekNumber;
      targetDay = latest.dayNumber + 1;
      if (targetDay > 7) {
        targetWeek += 1;
        targetDay = 1;
      }
    } else if (progressEntries.some((p) => p.status === 'completed')) {
      // Sessions exist but no __day__ sentinel yet — user is mid-day.
      // Find the day they're currently working on (latest session entry).
      const latestSession = progressEntries
        .filter((p) => p.status === 'completed' && p.sessionId !== '__day__')
        .sort((a, b) => {
          if (a.weekNumber !== b.weekNumber) return b.weekNumber - a.weekNumber;
          return b.dayNumber - a.dayNumber;
        })[0];
      if (latestSession) {
        targetWeek = latestSession.weekNumber;
        targetDay = latestSession.dayNumber;
      }
    }

    // Find the day
    const week = allWeeks.find((w) => w.weekNumber === targetWeek);
    const day = week?.days.find((d) => d.dayNumber === targetDay);

    if (!week || !day) {
      return {
        activePlanStatus: plan.status,
        currentProgram: {
          name: program.name as Record<string, string>,
          weekNumber: targetWeek,
          dayNumber: targetDay,
          dayType: 'completed',
          isRestDay: false,
          sessions: [],
        },
        nextReassessment: nextReassessment
          ? {
              scheduledDate: nextReassessment.scheduledDate.toISOString(),
              reason: nextReassessment.reason,
            }
          : null,
      };
    }

    return {
      activePlanStatus: plan.status,
      currentProgram: {
        name: program.name as Record<string, string>,
        weekNumber: targetWeek,
        dayNumber: targetDay,
        dayType: day.dayType,
        isRestDay: day.isRestDay,
        sessions: day.sessions.map((s) => ({
          id: s.id,
          name: s.name as Record<string, string>,
          sessionCategory: s.sessionCategory,
          estimatedDurationMin: s.estimatedDurationMin,
          itemCount: s.items.length,
          isCompleted: s.reports.length > 0,
        })),
      },
      nextReassessment: nextReassessment
        ? {
            scheduledDate: nextReassessment.scheduledDate.toISOString(),
            reason: nextReassessment.reason,
          }
        : null,
    };
  },

  /**
   * Transition the active program (complete it and activate next).
   */
  async completeActiveProgram(userId: string): Promise<ActivePlanData | null> {
    const prisma = await getPrisma();

    const plan = await prisma.activePlan.findUnique({
      where: { userId },
      include: {
        programs: {
          orderBy: { sortOrder: 'asc' },
        },
      },
    });

    if (!plan) return null;

    const activeSlot = plan.programs.find((p) => p.status === 'active');
    if (!activeSlot) return null;

    // Mark current as completed
    await prisma.activePlanProgram.update({
      where: { id: activeSlot.id },
      data: { status: 'completed', completedAt: new Date() },
    });

    // Mark the corresponding UserProgram as inactive
    await prisma.userProgram.update({
      where: { id: activeSlot.userProgramId },
      data: { isActive: false },
    });

    // Activate next upcoming program
    const nextSlot = plan.programs.find((p) => p.status === 'upcoming');
    if (nextSlot) {
      await prisma.activePlanProgram.update({
        where: { id: nextSlot.id },
        data: { status: 'active', actualStartDate: new Date() },
      });
      await prisma.userProgram.update({
        where: { id: nextSlot.userProgramId },
        data: { isActive: true },
      });
    } else {
      // No more programs — mark plan as completed
      await prisma.activePlan.update({
        where: { id: plan.id },
        data: { status: 'completed' },
      });
    }

    // Schedule reassessment after program completion
    await prisma.reassessmentSchedule.create({
      data: {
        userId,
        reason: 'program_complete',
        scheduledDate: new Date(Date.now() + 24 * 60 * 60 * 1000), // Tomorrow
        status: 'pending',
        notes: `Auto-scheduled after completing program`,
      },
    });

    return this.getOrCreate(userId);
  },
};
