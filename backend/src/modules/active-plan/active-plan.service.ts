/**
 * ActivePlan Service — Manages user's training schedule with ordered programs.
 *
 * Replaces the simple UserProgram.isActive boolean with a structured plan
 * that supports program sequencing, transitions, and auto-scheduling.
 */

import { getPrisma } from '@/lib/prisma/client';
import { programDomainToLegacyString } from '@/lib/program-domain';
import {
  countEffectiveExerciseItems,
  effectivePlanService,
  type EffectivePlanSession,
} from '@/modules/effective-plan/effective-plan.service';
import type { ProgramAssignmentReason } from '@/modules/programs/program-assignment';
import { resolveCurrentProgramDay } from './plan-position';

interface EnrollProgramOptions {
  assignmentReason?: ProgramAssignmentReason | null;
  name?: Record<string, string> | null;
}

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
                program: {
                  include: {
                    weeks: {
                      select: {
                        weekNumber: true,
                        days: {
                          select: { dayNumber: true },
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
                  program: {
                    include: {
                      weeks: {
                        select: {
                          weekNumber: true,
                          days: {
                            select: { dayNumber: true },
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
    }

    return {
      id: plan.id,
      userId: plan.userId,
      status: plan.status,
      programs: plan.programs.map((slot) => {
        const prog = slot.userProgram.program;
        const progressEntries = slot.userProgram.progress || [];
        const totalDays = prog ? prog.durationWeeks * 7 : 0;
        const position = prog
          ? resolveCurrentProgramDay(prog.weeks, progressEntries)
          : null;

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
                type: programDomainToLegacyString(prog.programDomain),
                durationWeeks: prog.durationWeeks,
                difficulty: prog.difficulty,
                coverImageUrl: prog.coverImageUrl,
              }
            : null,
          progress: {
            completedDays: position?.completedDayCount ?? 0,
            totalDays,
            currentWeek: position?.targetWeekNumber ?? 1,
            currentDay: position?.targetDayNumber ?? 1,
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
    options?: EnrollProgramOptions,
  ): Promise<ActivePlanData> {
    const prisma = await getPrisma();

    await prisma.$transaction(async (tx) => {
      const plan = await tx.activePlan.upsert({
        where: { userId },
        create: { userId, status: 'active' },
        update: {},
      });

      const userProgram = await tx.userProgram.create({
        data: {
          userId,
          programId,
          name: (options?.name as object) || undefined,
          assignmentReason: (options?.assignmentReason as object) || undefined,
          isActive: true,
        },
      });

      await tx.userProgram.updateMany({
        where: {
          userId,
          id: { not: userProgram.id },
          isActive: true,
        },
        data: { isActive: false },
      });

      const maxSlot = await tx.activePlanProgram.findFirst({
        where: { activePlanId: plan.id },
        orderBy: { sortOrder: 'desc' },
      });
      const nextOrder = (maxSlot?.sortOrder ?? -1) + 1;

      await tx.activePlanProgram.updateMany({
        where: { activePlanId: plan.id, status: 'active' },
        data: { status: 'completed', completedAt: new Date() },
      });

      await tx.activePlanProgram.create({
        data: {
          activePlanId: plan.id,
          userProgramId: userProgram.id,
          sortOrder: nextOrder,
          status: 'active',
          actualStartDate: new Date(),
        },
      });

      await tx.activePlan.update({
        where: { id: plan.id },
        data: { status: 'active' },
      });
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

    const progressEntries = activeSlot.userProgram.progress || [];
    const position = resolveCurrentProgramDay(program.weeks, progressEntries);
    const targetWeek = position.targetWeekNumber;
    const targetDay = position.targetDayNumber;
    const week = position.targetWeek as (typeof program.weeks)[number] | undefined;
    const day = position.targetDay as (typeof program.weeks)[number]['days'][number] | undefined;

    if (position.isProgramComplete) {
      return {
        activePlanStatus: 'program_complete',
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

    const userProgramId = activeSlot.userProgram.id;
    let effSessionById = new Map<string, EffectivePlanSession>();
    try {
      const eff = await effectivePlanService.getEffectivePlan(
        userId,
        userProgramId,
        targetWeek,
        targetDay,
      );
      effSessionById = new Map((eff?.sessions ?? []).map((s) => [s.id, s]));
    } catch (error) {
      console.warn('[ActivePlan] effective plan for today:', error);
    }

    return {
      activePlanStatus: plan.status,
      currentProgram: {
        name: program.name as Record<string, string>,
        weekNumber: targetWeek,
        dayNumber: targetDay,
        dayType: day.dayType,
        isRestDay: day.isRestDay,
        sessions: day.sessions.map((s) => {
          const effS = effSessionById.get(s.id);
          const itemCount = effS
            ? countEffectiveExerciseItems(effS)
            : s.items.filter((it) => it.type === 'exercise').length;
          return {
            id: s.id,
            name: s.name as Record<string, string>,
            sessionCategory: s.sessionCategory,
            estimatedDurationMin: s.estimatedDurationMin,
            itemCount,
            isCompleted: s.reports.length > 0,
          };
        }),
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

    if (!nextSlot) {
      const existingPending = await prisma.reassessmentSchedule.findFirst({
        where: {
          userId,
          reason: 'program_complete',
          status: { in: ['pending', 'overdue'] },
        },
      });
      if (!existingPending) {
        await prisma.reassessmentSchedule.create({
          data: {
            userId,
            reason: 'program_complete',
            scheduledDate: new Date(),
            status: 'pending',
            notes: `Auto-scheduled after completing final program`,
          },
        });
      }
    }

    return this.getOrCreate(userId);
  },
};
