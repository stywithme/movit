/**
 * Reassessment Controller
 * =======================
 *
 *   GET  /mobile/reassessment/upcoming — Get upcoming reassessments
 *   GET  /mobile/reassessment/history  — Get reassessment history
 *   POST /mobile/reassessment/request  — Manually request a reassessment
 */

import { Controller, Get, Post, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { reassessmentService } from './reassessment.service';

@Controller('mobile/reassessment')
export class ReassessmentController {
  @Get('upcoming')
  async getUpcoming(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const schedules = await reassessmentService.getUpcoming(authResult.userId);
      return { success: true, data: schedules };
    } catch (error) {
      console.error('[Reassessment] Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch upcoming reassessments' };
    }
  }

  @Get('history')
  async getHistory(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const schedules = await reassessmentService.getHistory(authResult.userId);
      return { success: true, data: schedules };
    } catch (error) {
      console.error('[Reassessment] History Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch reassessment history' };
    }
  }

  @Post('request')
  async requestReassessment(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const schedule = await reassessmentService.schedule(
        authResult.userId,
        'manual',
        new Date(), // Schedule for now (user wants it immediately)
        'User-requested reassessment',
      );

      return { success: true, data: schedule };
    } catch (error) {
      console.error('[Reassessment] Request Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to schedule reassessment' };
    }
  }
}
