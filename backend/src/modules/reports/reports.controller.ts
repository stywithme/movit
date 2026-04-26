/**
 * Reports Controller — Unified Metrics Endpoint
 * ===============================================
 *
 * GET /mobile/reports/metrics
 *
 * One endpoint to rule them all. Scope + filters determine what you get back.
 */

import { Controller, Get, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { reportsService } from './reports.service';
import type {
  MetricsScope,
  MetricsQuery,
  ReportDashboardPeriod,
  ReportDashboardQuery,
  ReportDashboardSource,
} from './reports.types';
import { MobileAuthGuard, RequireProUser } from '@/lib/guards/mobile-auth.guard';

@Controller('mobile/reports')
export class ReportsController {
  @Get('dashboard')
  @UseGuards(MobileAuthGuard)
  @RequireProUser()
  async getDashboard(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('programId') programId?: string,
    @Query('period') period?: string,
    @Query('source') source?: string,
    @Query('exerciseSlug') exerciseSlug?: string,
  ) {
    try {
      const userId = (req as any).userId as string | undefined;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const validPeriods: ReportDashboardPeriod[] = ['7d', '30d', '90d', 'program', 'all'];
      const validSources: ReportDashboardSource[] = ['all', 'program', 'free', 'workout', 'quick', 'explore'];

      if (period && !validPeriods.includes(period as ReportDashboardPeriod)) {
        res.status(400);
        return {
          success: false,
          error: `period must be one of: ${validPeriods.join(', ')}`,
        };
      }

      if (source && !validSources.includes(source as ReportDashboardSource)) {
        res.status(400);
        return {
          success: false,
          error: `source must be one of: ${validSources.join(', ')}`,
        };
      }

      const query: ReportDashboardQuery = {
        programId: programId || undefined,
        period: (period as ReportDashboardPeriod | undefined) ?? undefined,
        source: (source as ReportDashboardSource | undefined) ?? undefined,
        exerciseSlug: exerciseSlug || undefined,
      };

      return await reportsService.getDashboard(userId, query);
    } catch (error) {
      console.error('[Reports Dashboard] Error:', error);
      res.status(500);
      return { success: false, error: 'Internal server error' };
    }
  }

  @Get('metrics')
  @UseGuards(MobileAuthGuard)
  @RequireProUser()
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
      const userId = (req as any).userId as string | undefined;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      // ── Validate required params ──
      // Phase 0: programId is still required for now, but we no longer
      // require the program to be active (historical reports are allowed).
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
      const result = await reportsService.getMetrics(userId, query);

      return result;
    } catch (error) {
      console.error('[Reports] Error:', error);
      res.status(500);
      return { success: false, error: 'Internal server error' };
    }
  }
}
