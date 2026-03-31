/**
 * Progression Controller
 * ======================
 *
 *   GET  /mobile/progression/history — Full progression history
 *   GET  /mobile/progression/recent  — Unseen changes (for notifications)
 *   POST /mobile/progression/mark-seen — Acknowledge changes
 */

import { Body, Controller, Get, Param, Post, Req, Res } from '@nestjs/common';
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
   * after a session is completed.
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

  /**
   * Returns progression changes triggered by a specific program session.
   * Used by mobile to show progression context inside a session/exercise report.
   */
  @Get('session/:sessionId')
  async getBySession(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('sessionId') sessionId: string,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const changes = await progressionService.getBySession(authResult.userId, sessionId);
      return { success: true, data: changes };
    } catch (error) {
      console.error('[Progression] Session changes error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch session progression' };
    }
  }

  /**
   * Marks progression changes as seen (user has acknowledged the notification).
   * Body: { ids: string[] }
   */
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
