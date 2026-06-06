import { Body, Controller, Get, Param, Post, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import {
  getAllHistory,
  getExerciseHistory,
  getUserHomeStats,
  saveWorkoutExecution,
  saveExploreWorkout,
} from './workout-executions.service';
import type { ExploreWorkoutUploadPayload, WorkoutExecutionUploadPayload } from './workout-executions.types';

@Controller('mobile/workout-executions')
export class MobileWorkoutExecutionsController {
  @Post()
  async upload(@Req() req: Request, @Body() body: WorkoutExecutionUploadPayload, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const payload = body;
      if (!payload?.id || !payload?.exerciseId || !payload?.executionMetrics) {
        res.status(400);
        return { success: false, error: 'Missing required fields: id, exerciseId, executionMetrics' };
      }

      const execution = await saveWorkoutExecution(authResult.userId, payload);
      return { success: true, data: execution };
    } catch (error) {
      console.error('[Workout Executions] Error saving execution:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to save workout execution',
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
    @Query('endDate') endDate?: string,
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

      const executions = await getAllHistory(authResult.userId, params);
      return {
        success: true,
        data: executions,
        meta: {
          count: executions.length,
          offset: params.offset,
          limit: params.limit,
        },
      };
    } catch (error) {
      console.error('[Workout Executions] Error fetching history:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workout executions' };
    }
  }

  @Post('explore')
  async uploadExplore(
    @Req() req: Request,
    @Body() body: ExploreWorkoutUploadPayload,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      if (!body?.workoutGroupId || !Array.isArray(body?.executions) || body.executions.length === 0) {
        res.status(400);
        return { success: false, error: 'Missing required fields: workoutGroupId, executions[]' };
      }

      const result = await saveExploreWorkout(authResult.userId, body);
      return { success: true, data: result };
    } catch (error) {
      console.error('[Workout Executions] Error saving explore block:', error);
      res.status(500);
      return {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to save explore workout',
      };
    }
  }

  @Get('stats')
  async getStats(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const stats = await getUserHomeStats(authResult.userId);
      return { success: true, data: stats };
    } catch (error) {
      console.error('[Workout Executions] Error fetching stats:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch stats' };
    }
  }

  @Get(':exerciseId')
  async getHistoryByExercise(
    @Req() req: Request,
    @Param('exerciseId') exerciseId: string,
    @Res({ passthrough: true }) res: Response,
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
      console.error('[Workout Executions] Error fetching exercise history:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch history' };
    }
  }
}
