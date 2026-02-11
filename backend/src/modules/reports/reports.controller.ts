/**
 * Reports Controller — Unified Metrics Endpoint
 * ===============================================
 *
 * GET /mobile/reports/metrics
 *
 * One endpoint to rule them all. Scope + filters determine what you get back.
 */

import { Controller, Get, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { reportsService } from './reports.service';
import type { MetricsScope, MetricsQuery } from './reports.types';

@Controller('mobile/reports')
export class ReportsController {
  @Get('metrics')
  async getMetrics(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('programId') programId?: string,
    @Query('scope') scope?: string,
    @Query('weekNumber') weekNumber?: string,
    @Query('dayNumber') dayNumber?: string,
    @Query('sessionId') sessionId?: string,
    @Query('exerciseSlug') exerciseSlug?: string,
    @Query('includeHistory') includeHistory?: string,
    @Query('includeChildren') includeChildren?: string,
  ) {
    try {
      // ── Auth ──
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      // ── Validate required params ──
      if (!programId) {
        res.status(400);
        return { success: false, error: 'programId is required' };
      }

      const validScopes: MetricsScope[] = ['program', 'week', 'day', 'session', 'exercise'];
      if (!scope || !validScopes.includes(scope as MetricsScope)) {
        res.status(400);
        return {
          success: false,
          error: `scope is required and must be one of: ${validScopes.join(', ')}`,
        };
      }

      // ── Build query ──
      const query: MetricsQuery = {
        programId,
        scope: scope as MetricsScope,
        weekNumber: weekNumber ? parseInt(weekNumber, 10) : undefined,
        dayNumber: dayNumber ? parseInt(dayNumber, 10) : undefined,
        sessionId: sessionId || undefined,
        exerciseSlug: exerciseSlug || undefined,
        includeHistory: includeHistory === 'true',
        includeChildren: includeChildren === 'true',
      };

      // ── Get metrics ──
      const result = await reportsService.getMetrics(authResult.userId, query);

      if (!result.success) {
        res.status(404);
      }

      return result;
    } catch (error) {
      console.error('[Reports] Error:', error);
      res.status(500);
      return { success: false, error: 'Internal server error' };
    }
  }
}
