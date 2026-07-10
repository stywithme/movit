/**
 * ActivePlan Controller
 * =====================
 *
 *   GET  /mobile/plan           — Get user's active plan
 *   GET  /mobile/plan/today     — Get today's training plan
 *   POST /mobile/plan/enroll    — Enroll in a program (adds to plan)
 *   POST /mobile/plan/complete  — Complete the active program (transition to next)
 */

import { Controller, Get, Post, Req, Res, Body, Query } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { activePlanService } from './active-plan.service';
import { buildAssignmentReason } from '@/modules/programs/program-assignment';

@Controller('mobile/plan')
export class ActivePlanController {
  @Get()
  async getPlan(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const plan = await activePlanService.getOrCreate(authResult.userId);
      return { success: true, data: plan };
    } catch (error) {
      console.error('[ActivePlan] Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch plan' };
    }
  }

  @Get('today')
  async getTodayPlan(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const plan = await activePlanService.getTodayPlan(authResult.userId);
      return { success: true, data: plan };
    } catch (error) {
      console.error('[ActivePlan] Today Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch today plan' };
    }
  }

  @Get('enrollment-check')
  async enrollmentCheck(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('programId') programId: string,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      if (!programId) {
        res.status(400);
        return { success: false, error: 'programId query param is required' };
      }
      const data = await activePlanService.getEnrollmentCheck(authResult.userId, programId);
      return { success: true, data };
    } catch (error) {
      console.error('[ActivePlan] enrollment-check Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to check enrollment' };
    }
  }

  @Post('enroll')
  async enroll(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Body() body: { programId: string },
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      if (!body.programId) {
        res.status(400);
        return { success: false, error: 'programId is required' };
      }

      const plan = await activePlanService.enrollProgram(
        authResult.userId,
        body.programId,
        {
          assignmentReason: buildAssignmentReason(
            'manual_selection',
            ['user_choice'],
            null,
          ),
        },
      );
      return { success: true, data: plan };
    } catch (error) {
      console.error('[ActivePlan] Enroll Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to enroll in program' };
    }
  }

  @Post('complete')
  async completeProgram(
    @Req() req: Request,
    @Body() body: { idempotencyKey?: string } | undefined,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      // idempotencyKey accepted for contract parity with outbox (P1.3); no-op when no active slot.
      void body?.idempotencyKey;

      const result = await activePlanService.completeActiveProgram(authResult.userId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'No active plan found' };
      }

      return {
        success: true,
        data: result.plan,
        completion: result.completion,
        noop: result.noop === true,
      };
    } catch (error) {
      console.error('[ActivePlan] Complete Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to complete program' };
    }
  }
}
