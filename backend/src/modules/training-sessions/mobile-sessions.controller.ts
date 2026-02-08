import { Body, Controller, Get, Param, Post, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { getAllHistory, getExerciseHistory, saveSession } from './training-sessions.service';
import type { SessionUploadPayload } from './training-sessions.types';

@Controller('mobile/sessions')
export class MobileSessionsController {
  @Post()
  async upload(@Req() req: Request, @Body() body: SessionUploadPayload, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const payload = body;
      if (!payload?.id || !payload?.exerciseId || !payload?.sessionMetrics) {
        res.status(400);
        return { success: false, error: 'Missing required fields: id, exerciseId, sessionMetrics' };
      }

      const session = await saveSession(authResult.userId, payload);
      return { success: true, data: session };
    } catch (error) {
      console.error('[Sessions] Error saving session:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to save session',
      };
    }
  }

  @Get()
  async list(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('limit') limit?: string,
    @Query('offset') offset?: string,
    @Query('startDate') startDate?: string,
    @Query('endDate') endDate?: string
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const params = {
        limit: Number.parseInt(limit || '50', 10),
        offset: Number.parseInt(offset || '0', 10),
        startDate: startDate || undefined,
        endDate: endDate || undefined,
      };

      const sessions = await getAllHistory(authResult.userId, params);
      return {
        success: true,
        data: sessions,
        meta: {
          count: sessions.length,
          offset: params.offset,
          limit: params.limit,
        },
      };
    } catch (error) {
      console.error('[Sessions] Error fetching sessions:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch sessions' };
    }
  }

  @Get(':exerciseId')
  async getHistoryByExercise(
    @Req() req: Request,
    @Param('exerciseId') exerciseId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const history = await getExerciseHistory(authResult.userId, exerciseId);
      return { success: true, data: history };
    } catch (error) {
      console.error('[Sessions] Error fetching history:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch history' };
    }
  }
}
