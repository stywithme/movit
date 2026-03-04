import { Body, Controller, Get, Param, Post, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import {
  completeProgramSessionReport,
  getAllHistory,
  getExerciseHistory,
  getUserHomeStats,
  saveSession,
  saveExploreSession,
  startProgramSessionReport,
  updateProgramSessionReport,
} from './training-sessions.service';
import type {
  ExploreSessionUploadPayload,
  ProgramSessionCompletePayload,
  ProgramSessionStartPayload,
  SessionUploadPayload,
} from './training-sessions.types';

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

  @Post(':sessionId/start')
  async startProgramSession(
    @Req() req: Request,
    @Param('sessionId') sessionId: string,
    @Body() body: ProgramSessionStartPayload,
    @Res({ passthrough: true }) res: Response
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

      const report = await startProgramSessionReport(authResult.userId, sessionId, body);
      return { success: true, data: report };
    } catch (error) {
      console.error('[Program Sessions] Error starting session:', error);
      res.status(500);
      return { success: false, error: error instanceof Error ? error.message : 'Failed to start session' };
    }
  }

  @Post(':sessionId/complete')
  async completeProgramSession(
    @Req() req: Request,
    @Param('sessionId') sessionId: string,
    @Body() body: ProgramSessionCompletePayload,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      // Single endpoint for completing + reporting. The completeProgramSessionReport
      // function handles progress tracking and report creation in one call.
      const report = await completeProgramSessionReport(authResult.userId, sessionId, body || {});
      return { success: true, data: report };
    } catch (error) {
      console.error('[Program Sessions] Error completing session:', error);
      res.status(500);
      return { success: false, error: error instanceof Error ? error.message : 'Failed to complete session' };
    }
  }

  /**
   * Kept for backward compatibility — new mobile versions should use /complete only.
   * This now delegates to updateProgramSessionReport which only patches metrics.
   */
  @Post(':sessionId/report')
  async reportProgramSession(
    @Req() req: Request,
    @Param('sessionId') sessionId: string,
    @Body() body: ProgramSessionCompletePayload,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const report = await updateProgramSessionReport(authResult.userId, sessionId, body || {});
      return { success: true, data: report };
    } catch (error) {
      console.error('[Program Sessions] Error reporting session:', error);
      res.status(500);
      return { success: false, error: error instanceof Error ? error.message : 'Failed to report session' };
    }
  }

  /**
   * POST /mobile/sessions/explore — Save a multi-exercise free session (Explore / Quick Start).
   *
   * All sessions in the payload share the same groupId so they can be
   * retrieved and reported together as a single workout block.
   */
  @Post('explore')
  async uploadExplore(
    @Req() req: Request,
    @Body() body: ExploreSessionUploadPayload,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      if (!body?.groupId || !Array.isArray(body?.sessions) || body.sessions.length === 0) {
        res.status(400);
        return { success: false, error: 'Missing required fields: groupId, sessions[]' };
      }

      const result = await saveExploreSession(authResult.userId, body);
      return { success: true, data: result };
    } catch (error) {
      console.error('[Sessions] Error saving explore session:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to save explore session',
      };
    }
  }

  /**
   * GET /mobile/sessions/stats — User home stats.
   * Returns weekly workouts, average form score, streak, and total stats.
   */
  @Get('stats')
  async getStats(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const stats = await getUserHomeStats(authResult.userId);
      return { success: true, data: stats };
    } catch (error) {
      console.error('[Sessions] Error fetching stats:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch stats' };
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
