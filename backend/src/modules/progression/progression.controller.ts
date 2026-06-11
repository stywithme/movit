/**
 * Progression Controller
 * ======================
 *
 *   GET  /mobile/progression/history — Full progression history
 *   GET  /mobile/progression/recent  — Unseen changes (for notifications)
 *   POST /mobile/progression/mark-seen — Acknowledge changes
 */

import { Body, Controller, Get, Post, Param, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { progressionService } from './progression.service';

@Controller('mobile/progression')
export class ProgressionController {
  @Get('history')
  async getHistory(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const history = await progressionService.getHistory(authResult.userId);
      return { success: true, data: history };
    } catch (error) {
      console.error('[Progression] History error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch progression history' };
    }
  }

  /**
   * Returns unseen progression changes — used by mobile to show notifications
   * after a planned workout is completed.
   */
  @Get('recent')
  async getRecent(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const changes = await progressionService.getRecent(authResult.userId);
      return { success: true, data: changes };
    } catch (error) {
      console.error('[Progression] Recent error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch recent changes' };
    }
  }

  @Get('planned-workout/:plannedWorkoutId')
  async getByPlannedWorkout(
    @Param('plannedWorkoutId') plannedWorkoutId: string,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ) {
    return this.getSessionProgression(plannedWorkoutId, req, res);
  }

  /** Legacy Retrofit alias — sessionId is the planned-workout id. */
  @Get('session/:sessionId')
  async getSessionProgression(
    @Param('sessionId') sessionId: string,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const changes = await progressionService.getByPlannedWorkout(authResult.userId, sessionId);
      return { success: true, data: changes };
    } catch (error) {
      console.error('[Progression] Workout progression error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workout progression' };
    }
  }

  @Post('mark-seen')
  async markSeen(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Body() body: { ids: string[] },
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      if (!Array.isArray(body?.ids) || body.ids.length === 0) {
        res.status(400);
        return { success: false, error: 'ids must be a non-empty array' };
      }

      const count = await progressionService.markSeen(authResult.userId, body.ids);
      return { success: true, data: { markedCount: count } };
    } catch (error) {
      console.error('[Progression] Mark-seen error:', error);
      res.status(500);
      return { success: false, error: 'Failed to mark changes as seen' };
    }
  }
}
