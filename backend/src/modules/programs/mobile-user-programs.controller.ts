import { Body, Controller, Delete, Get, Param, Post, Put, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { getPrisma } from '@/lib/prisma/client';
import { validateOverrideTargetInput } from '@/lib/user-program-override';
import type { Prisma } from '@prisma/client';
import { effectivePlanService } from '@/modules/effective-plan/effective-plan.service';
import { programService } from './programs.service';
import { programProgressService } from './program-progress.service';
import { activePlanService } from '@/modules/active-plan/active-plan.service';

@Controller('mobile/user-programs')
export class MobileUserProgramsController {
  @Get()
  async listUserPrograms(
    @Req() req: Request,
    @Query('updatedAfter') updatedAfter: string | undefined,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      let updatedAfterDate: Date | undefined;
      if (updatedAfter) {
        const parsed = new Date(updatedAfter);
        if (!Number.isFinite(parsed.getTime())) {
          res.status(400);
          return { success: false, error: 'updatedAfter must be a valid ISO timestamp' };
        }
        updatedAfterDate = parsed;
      }

      const userPrograms = await programService.listUserProgramsForMobile(
        authResult.userId,
        updatedAfterDate,
      );
      return { success: true, userPrograms };
    } catch (error) {
      console.error('Error listing user programs:', error);
      res.status(500);
      return { success: false, error: 'Failed to list user programs' };
    }
  }

  @Put(':id')
  async updateUserProgram(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: { name?: { ar?: string; en?: string }; customizations?: Record<string, unknown>; isActive?: boolean },
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const result = await programService.updateUserProgram(id, authResult.userId, body as any);
      if (result.count === 0) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }
      return { success: true, data: { updated: result.count } };
    } catch (error) {
      console.error('Error updating user program:', error);
      res.status(500);
      return { success: false, error: 'Failed to update user program' };
    }
  }

  @Get(':id/progress-metrics')
  async getProgressMetrics(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      const data = await programProgressService.getProgressMetrics(authResult.userId, id);
      if (!data) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }
      return { success: true, data };
    } catch (error) {
      console.error('Error fetching program progress metrics:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch progress metrics' };
    }
  }

  @Get(':id/effective-plan')
  async getEffectivePlan(
    @Req() req: Request,
    @Param('id') id: string,
    @Query('week') week: string,
    @Query('day') day: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      const weekNumber = Number.parseInt(week, 10);
      const dayNumber = Number.parseInt(day, 10);
      if (!Number.isFinite(weekNumber) || !Number.isFinite(dayNumber)) {
        res.status(400);
        return { success: false, error: 'week and day query params are required' };
      }
      const plan = await effectivePlanService.getEffectivePlan(
        authResult.userId,
        id,
        weekNumber,
        dayNumber
      );
      if (!plan) {
        res.status(404);
        return { success: false, error: 'Effective plan not found' };
      }
      return { success: true, data: plan };
    } catch (error) {
      console.error('Error fetching effective plan:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch effective plan' };
    }
  }

  @Get(':id/overrides')
  async listOverrides(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      const prisma = await getPrisma();
      const up = await prisma.userProgram.findFirst({
        where: { id, userId: authResult.userId },
      });
      if (!up) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }
      const rows = await prisma.userProgramOverride.findMany({
        where: { userProgramId: id },
        orderBy: { createdAt: 'desc' },
      });
      return { success: true, data: rows };
    } catch (error) {
      console.error('Error listing overrides:', error);
      res.status(500);
      return { success: false, error: 'Failed to list overrides' };
    }
  }

  @Post(':id/overrides')
  async createOverride(
    @Req() req: Request,
    @Param('id') id: string,
    @Body()
    body: {
      weekNumber: number;
      dayNumber: number;
      /** Legacy target — mutually exclusive with workoutTemplateExerciseId */
      plannedWorkoutItemId?: string;
      /** Template exercise target — mutually exclusive with plannedWorkoutItemId */
      workoutTemplateExerciseId?: string;
      overrideType: string;
      reasonCode?: string;
      data?: Record<string, unknown>;
      /** Client outbox operationId — replay-safe (P1.3). */
      idempotencyKey?: string;
    },
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      const prisma = await getPrisma();
      const up = await prisma.userProgram.findFirst({
        where: { id, userId: authResult.userId },
      });
      if (!up) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }
      if (body.weekNumber === undefined || body.dayNumber === undefined || !body.overrideType) {
        res.status(400);
        return {
          success: false,
          error: 'weekNumber, dayNumber, overrideType are required',
        };
      }

      const target = validateOverrideTargetInput({
        plannedWorkoutItemId: body.plannedWorkoutItemId,
        workoutTemplateExerciseId: body.workoutTemplateExerciseId,
      });
      if (!target.valid) {
        res.status(400);
        return { success: false, error: target.error };
      }

      const idempotencyKey =
        typeof body.idempotencyKey === 'string' &&
        body.idempotencyKey.trim().length >= 8 &&
        body.idempotencyKey.trim().length <= 128
          ? body.idempotencyKey.trim()
          : undefined;

      if (idempotencyKey) {
        const existing = await prisma.userProgramOverride.findUnique({
          where: {
            userProgramId_idempotencyKey: {
              userProgramId: id,
              idempotencyKey,
            },
          },
        });
        if (existing) {
          return { success: true, data: existing };
        }
      }

      try {
        const row = await prisma.userProgramOverride.create({
          data: {
            userProgramId: id,
            weekNumber: body.weekNumber,
            dayNumber: body.dayNumber,
            plannedWorkoutItemId: target.plannedWorkoutItemId,
            workoutTemplateExerciseId: target.workoutTemplateExerciseId,
            overrideType: body.overrideType as never,
            reasonCode: (body.reasonCode as never) ?? undefined,
            data: (body.data as Prisma.InputJsonValue) ?? undefined,
            appliedBy: 'USER',
            idempotencyKey,
          },
        });
        return { success: true, data: row };
      } catch (error) {
        if (
          idempotencyKey &&
          typeof error === 'object' &&
          error !== null &&
          'code' in error &&
          (error as { code?: string }).code === 'P2002'
        ) {
          const existing = await prisma.userProgramOverride.findUnique({
            where: {
              userProgramId_idempotencyKey: {
                userProgramId: id,
                idempotencyKey,
              },
            },
          });
          if (existing) {
            return { success: true, data: existing };
          }
        }
        throw error;
      }
    } catch (error) {
      console.error('Error creating override:', error);
      res.status(500);
      return { success: false, error: 'Failed to create override' };
    }
  }

  /**
   * @deprecated Prefer POST /api/mobile/plan/complete — this route now delegates to the same mutation.
   */
  @Post(':id/complete')
  async completeProgram(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: { idempotencyKey?: string } | undefined,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      void body?.idempotencyKey;

      const prisma = await getPrisma();
      const userProgram = await prisma.userProgram.findFirst({
        where: { id, userId: authResult.userId },
        select: { id: true },
      });
      if (!userProgram) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }

      const plan = await prisma.activePlan.findUnique({
        where: { userId: authResult.userId },
        include: { programs: { where: { status: 'active' } } },
      });
      const activeSlot = plan?.programs?.[0];
      // P1.3: no active slot → no-op 200 (replay-safe) instead of 400.
      if (!activeSlot) {
        const current = await activePlanService.getOrCreate(authResult.userId);
        return {
          success: true,
          data: null,
          completion: null,
          plan: current,
          noop: true,
        };
      }
      if (activeSlot.userProgramId !== id) {
        res.status(400);
        return { success: false, error: 'User program is not the active plan slot' };
      }

      const result = await activePlanService.completeActiveProgram(authResult.userId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'No active plan found' };
      }
      return {
        success: true,
        data: result.completion,
        completion: result.completion,
        plan: result.plan,
        noop: result.noop === true,
      };
    } catch (error) {
      console.error('Error completing program:', error);
      res.status(500);
      return { success: false, error: 'Failed to complete program' };
    }
  }

  @Delete(':id/overrides/:overrideId')
  async deleteOverride(
    @Req() req: Request,
    @Param('id') id: string,
    @Param('overrideId') overrideId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      const prisma = await getPrisma();
      const up = await prisma.userProgram.findFirst({
        where: { id, userId: authResult.userId },
      });
      if (!up) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }
      const existing = await prisma.userProgramOverride.findFirst({
        where: { id: overrideId, userProgramId: id },
      });
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Override not found' };
      }
      await prisma.userProgramOverride.delete({ where: { id: overrideId } });
      return { success: true, data: { deleted: true } };
    } catch (error) {
      console.error('Error deleting override:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete override' };
    }
  }

  @Get(':id/today')
  async getTodayPlan(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const plan = await programService.getTodayPlan(authResult.userId);
      if (!plan || plan.userProgramId !== id) {
        res.status(404);
        return { success: false, error: 'Plan not found' };
      }
      return { success: true, data: plan };
    } catch (error) {
      console.error('Error fetching today plan:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch today plan' };
    }
  }
}
