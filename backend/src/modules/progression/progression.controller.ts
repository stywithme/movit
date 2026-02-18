/**
 * Progression Controller
 * ======================
 *
 *   GET /mobile/progression/history — Get progression history
 */

import { Controller, Get, Req, Res } from '@nestjs/common';
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
      console.error('[Progression] History Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch progression history' };
    }
  }
}
