/**
 * Admin Analytics Controller — Phase 5.4
 *
 *   GET /admin/analytics/rules              — Rule effectiveness
 *   GET /admin/analytics/programs           — Program effectiveness
 *   GET /admin/analytics/user-trends        — User progression trends
 *   GET /admin/analytics/platform           — Platform overview stats
 *   GET /admin/analytics/levels             — Level distribution
 *   GET /admin/analytics/assessments        — Assessment analytics
 *   GET /admin/analytics/level-transitions  — Level transition timing
 *
 * All endpoints are protected by admin cookie-based auth.
 */

import { Controller, Get, NotFoundException, Param, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { progressionAnalyticsService } from './progression-analytics.service';
import { AdminReportsService, type AnalyticsPeriodQuery } from './admin-reports.service';

@UseGuards(CaslGuard)
@Controller('admin/analytics')
export class AdminAnalyticsController {
  constructor(private readonly reportsService: AdminReportsService) {}

  @Get('overview')
  @CheckPermission('read', 'ReportOverview')
  async getOverview(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getOverview(query) };
  }

  @Get('users')
  @CheckPermission('read', 'ReportUsers')
  async getUsersGrowth(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getUsersGrowth(query) };
  }

  @Get('activation')
  @CheckPermission('read', 'ReportActivation')
  async getActivation(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getActivation(query) };
  }

  @Get('retention')
  @CheckPermission('read', 'ReportRetention')
  async getRetention(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getRetention(query) };
  }

  @Get('training')
  @CheckPermission('read', 'ReportTraining')
  async getTrainingPerformance(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getTrainingPerformance(query) };
  }

  @Get('progression')
  @CheckPermission('read', 'ReportProgression')
  async getProgression(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getProgression(query) };
  }

  @Get('revenue')
  @UseGuards(AdminGuard)
  @AdminOnly()
  @CheckPermission('read', 'ReportRevenue')
  async getRevenue(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getRevenue(query) };
  }

  @Get('safety')
  @CheckPermission('read', 'ReportSafety')
  async getSafety(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getSafety(query) };
  }

  @Get('content')
  @CheckPermission('read', 'ReportContent')
  async getContent(@Query() query: AnalyticsPeriodQuery) {
    return { success: true, data: await this.reportsService.getContent(query) };
  }

  @Get('programs/:id')
  @CheckPermission('read', 'ReportProgram')
  async getProgramDetail(@Param('id') id: string, @Query() query: AnalyticsPeriodQuery) {
    const data = await this.reportsService.getProgramDetail(id, query);
    if (!data) throw new NotFoundException('Program not found');
    return { success: true, data };
  }

  @Get('users/:id/report')
  @CheckPermission('read', 'ReportUsers')
  async getUserReport(@Param('id') id: string) {
    const data = await this.reportsService.getUserReport(id);
    if (!data) throw new NotFoundException('User not found');
    return { success: true, data };
  }

  @Get('workout-executions/:id/report')
  @CheckPermission('read', 'ReportTraining')
  async getWorkoutExecutionReport(@Param('id') id: string) {
    const data = await this.reportsService.getWorkoutExecutionReport(id);
    if (!data) throw new NotFoundException('Workout execution not found');
    return { success: true, data };
  }

  @Get('rules')
  @CheckPermission('read', 'ReportProgression')
  async getRuleEffectiveness(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getRuleEffectiveness();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Rules Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch rule analytics' };
    }
  }

  @Get('programs')
  @CheckPermission('read', 'ReportProgram')
  async getProgramEffectiveness(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getProgramEffectiveness();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Programs Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch program analytics' };
    }
  }

  @Get('user-trends')
  @CheckPermission('read', 'ReportUsers')
  async getUserTrends(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getUserTrends();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Trends Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch user trends' };
    }
  }

  @Get('platform')
  @CheckPermission('read', 'ReportOverview')
  async getPlatformStats(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getPlatformStats();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Platform Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch platform stats' };
    }
  }

  @Get('levels')
  @CheckPermission('read', 'ReportLevel')
  async getLevelDistribution(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getLevelDistribution();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Levels Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch level distribution' };
    }
  }

  @Get('assessments')
  @CheckPermission('read', 'ReportAssessment')
  async getAssessmentAnalytics(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getAssessmentAnalytics();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Assessments Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch assessment analytics' };
    }
  }

  @Get('level-transitions')
  @CheckPermission('read', 'ReportLevel')
  async getLevelTransitionStats(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await progressionAnalyticsService.getLevelTransitionStats();
      return { success: true, data };
    } catch (error) {
      console.error('[Analytics] Level Transitions Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch level transition stats' };
    }
  }
}
