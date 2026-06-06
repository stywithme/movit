import { Body, Controller, Param, Post, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import {
  completePlannedWorkoutReport,
  startPlannedWorkoutReport,
  updatePlannedWorkoutReport,
} from './workout-executions.service';
import type {
  PlannedWorkoutCompletePayload,
  PlannedWorkoutStartPayload,
} from './workout-executions.types';

@Controller('mobile/planned-workouts')
export class MobilePlannedWorkoutsController {
  @Post(':id/start')
  async start(
    @Req() req: Request,
    @Param('id') plannedWorkoutId: string,
    @Body() body: PlannedWorkoutStartPayload,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      if (!body?.weekNumber || !body?.dayNumber) {
        res.status(400);
        return { success: false, error: 'Missing required fields: weekNumber, dayNumber' };
      }

      const report = await startPlannedWorkoutReport(authResult.userId, plannedWorkoutId, body);
      return { success: true, data: report };
    } catch (error) {
      console.error('[Planned Workouts] Error starting:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to start planned workout',
      };
    }
  }

  @Post(':id/complete')
  async complete(
    @Req() req: Request,
    @Param('id') plannedWorkoutId: string,
    @Body() body: PlannedWorkoutCompletePayload,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const report = await completePlannedWorkoutReport(authResult.userId, plannedWorkoutId, body || {});
      return { success: true, data: report };
    } catch (error) {
      console.error('[Planned Workouts] Error completing:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to complete planned workout',
      };
    }
  }

  /** Backward compatibility — prefer /complete for new clients. */
  @Post(':id/report')
  async report(
    @Req() req: Request,
    @Param('id') plannedWorkoutId: string,
    @Body() body: PlannedWorkoutCompletePayload,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const report = await updatePlannedWorkoutReport(authResult.userId, plannedWorkoutId, body || {});
      return { success: true, data: report };
    } catch (error) {
      console.error('[Planned Workouts] Error reporting:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to report planned workout',
      };
    }
  }
}
