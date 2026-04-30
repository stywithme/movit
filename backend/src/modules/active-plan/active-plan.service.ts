/**
 * ActivePlan Service — Manages user's training schedule with ordered programs.
 *
 * Replaces the simple UserProgram.isActive boolean with a structured plan
 * that supports program sequencing, transitions, and auto-scheduling.
 */

import { getPrisma } from '@/lib/prisma/client';
import { typeStringFromProgramDomain } from '@/lib/program-domain';
import {
  countEffectiveExerciseItems,
  effectivePlanService,
  type EffectivePlanSession,
} from '@/modules/effective-plan/effective-plan.service';
import type { ProgramAssignmentReason } from '@/modules/programs/program-assignment';
import { resolveCurrentProgramDay, getProgramCalendarDayIndex } from './plan-position';
import { programCompletionService } from '@/modules/programs/program-completion.service';
import type { ProgramCompletionDecision } from '@/modules/programs/program-completion.service';
import { computeCatchUpSuggestion } from '@/modules/programs/program-catchup';

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
  /** True when the active enrollment is paused (calendar frozen). */
  isPaused?: boolean;
  /** When the user has fallen behind completed sessions vs calendar position. */
  catchUpSuggestion?: {
    missedTrainingDays: number;
    message: string;
    missedSlots: { weekNumber: number; dayNumber: number }[];
  } | null;
}

export interface EnrollmentCheckData {
  hasActiveProgram: boolean;
  willReplace: boolean;
  activeProgram: {
    id: string;
    name: Record<string, string>;
    programId: string | null;
    progress: { completedDays: number; totalDays: number; percentage: number };
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
          ? resolveCurrentProgramDay(prog.weeks, progressEntries, {
              startDate: slot.userProgram.startDate,
              durationWeeks: prog.durationWeeks,
              totalPausedDays: slot.userProgram.totalPausedDays,
              pausedAt: slot.userProgram.pausedAt,
            })
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
                type: typeStringFromProgramDomain(prog.programDomain),
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

      let userProgram = await tx.userProgram.findFirst({
        where: { userId, programId, isActive: true },
        orderBy: { updatedAt: 'desc' },
      });

      if (!userProgram) {
        userProgram = await tx.userProgram.create({
          data: {
            userId,
            programId,
            name: (options?.name as object) || undefined,
            assignmentReason: (options?.assignmentReason as object) || undefined,
            isActive: true,
          },
        });
      }

      await tx.userProgram.updateMany({
        where: {
          userId,
          id: { not: userProgram.id },
          isActive: true,
        },
        data: { isActive: false },
      });

      await tx.activePlanProgram.updateMany({
        where: {
          activePlanId: plan.id,
          status: 'active',
          userProgramId: { not: userProgram.id },
        },
        data: { status: 'completed', completedAt: new Date() },
      });

      const existingActiveSlot = await tx.activePlanProgram.findFirst({
        where: {
          activePlanId: plan.id,
          userProgramId: userProgram.id,
          status: 'active',
        },
      });

      if (!existingActiveSlot) {
        const maxSlot = await tx.activePlanProgram.findFirst({
          where: { activePlanId: plan.id },
          orderBy: { sortOrder: 'desc' },
        });
        const nextOrder = (maxSlot?.sortOrder ?? -1) + 1;

        await tx.activePlanProgram.create({
          data: {
            activePlanId: plan.id,
            userProgramId: userProgram.id,
            sortOrder: nextOrder,
            status: 'active',
            actualStartDate: new Date(),
          },
        });
      }

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
        isPaused: false,
        catchUpSuggestion: null,
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
        isPaused: false,
        catchUpSuggestion: null,
      };
    }

    const progressEntries = activeSlot.userProgram.progress || [];
    const position = resolveCurrentProgramDay(program.weeks, progressEntries, {
      startDate: activeSlot.userProgram.startDate,
      durationWeeks: program.durationWeeks,
      totalPausedDays: activeSlot.userProgram.totalPausedDays,
      pausedAt: activeSlot.userProgram.pausedAt,
    });
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
        isPaused: false,
        catchUpSuggestion: null,
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
        isPaused: Boolean(activeSlot.userProgram.pausedAt),
        catchUpSuggestion: null,
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

    const catchUpSuggestion = await computeCatchUpSuggestion(
      userId,
      program.id,
      program.weeks,
      program.durationWeeks,
      targetWeek,
      targetDay,
    );

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
      isPaused: Boolean(activeSlot.userProgram.pausedAt),
      catchUpSuggestion,
    };
  },

  async pauseActiveProgram(userId: string): Promise<{ success: boolean; error?: string }> {
    const prisma = await getPrisma();
    const plan = await prisma.activePlan.findUnique({
      where: { userId },
      include: {
        programs: { where: { status: 'active' }, select: { userProgramId: true } },
      },
    });
    const slot = plan?.programs[0];
    if (!slot) return { success: false, error: 'no_active_program' };
    const up = await prisma.userProgram.findFirst({
      where: { id: slot.userProgramId, userId },
    });
    if (!up) return { success: false, error: 'user_program_not_found' };
    if (up.pausedAt) return { success: false, error: 'already_paused' };
    await prisma.userProgram.update({
      where: { id: up.id },
      data: { pausedAt: new Date() },
    });
    return { success: true };
  },

  async resumeActiveProgram(userId: string): Promise<{ success: boolean; error?: string }> {
    const prisma = await getPrisma();
    const up = await prisma.userProgram.findFirst({
      where: { userId, isActive: true, pausedAt: { not: null } },
    });
    if (!up?.pausedAt) return { success: false, error: 'not_paused' };
    const pauseDays = getProgramCalendarDayIndex(up.pausedAt, new Date());
    await prisma.userProgram.update({
      where: { id: up.id },
      data: {
        totalPausedDays: up.totalPausedDays + pauseDays,
        pausedAt: null,
      },
    });
    return { success: true };
  },

  async getEnrollmentCheck(userId: string, programId: string): Promise<EnrollmentCheckData> {
    const prisma = await getPrisma();
    const active = await prisma.userProgram.findFirst({
      where: { userId, isActive: true },
      include: {
        program: {
          include: {
            weeks: {
              orderBy: [{ sortOrder: 'asc' }, { weekNumber: 'asc' }],
              include: {
                days: {
                  orderBy: { dayNumber: 'asc' },
                  include: { sessions: { select: { id: true } } },
                },
              },
            },
          },
        },
        progress: true,
      },
    });

    if (!active?.program) {
      return { hasActiveProgram: false, willReplace: false, activeProgram: null };
    }

    const willReplace = active.programId !== programId;
    const position = resolveCurrentProgramDay(active.program.weeks, active.progress ?? [], {
      startDate: active.startDate,
      durationWeeks: active.program.durationWeeks,
      totalPausedDays: active.totalPausedDays,
      pausedAt: active.pausedAt,
    });
    const totalDays = active.program.durationWeeks * 7;
    const percentage =
      totalDays > 0 ? Math.round((position.completedDayCount / totalDays) * 100) : 0;

    return {
      hasActiveProgram: true,
      willReplace,
      activeProgram: {
        id: active.id,
        name: active.program.name as Record<string, string>,
        programId: active.programId,
        progress: {
          completedDays: position.completedDayCount,
          totalDays,
          percentage: Math.min(100, Math.max(0, percentage)),
        },
      },
    };
  },

  /**
   * Transition the active program (complete it and activate next).
   * Runs completion evaluation before mutating state.
   */
  async completeActiveProgram(userId: string): Promise<{
    plan: ActivePlanData;
    completion: ProgramCompletionDecision | null;
  } | null> {
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

    const completion = await programCompletionService.evaluate(userId, activeSlot.userProgramId);

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

    const updated = await this.getOrCreate(userId);
    return { plan: updated, completion };
  },
};
