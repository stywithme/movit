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

import { Controller, Get, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { getAdminIdFromRequest } from '@/lib/auth/admin';
import { progressionAnalyticsService } from './progression-analytics.service';

function verifyAdmin(req: Request, res: Response): boolean {
  const adminId = getAdminIdFromRequest(req);
  if (!adminId) {
    res.status(401);
    return false;
  }
  return true;
}

@Controller('admin/analytics')
export class AdminAnalyticsController {
  @Get('rules')
  async getRuleEffectiveness(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
  async getProgramEffectiveness(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
  async getUserTrends(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
  async getPlatformStats(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
  async getLevelDistribution(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
  async getAssessmentAnalytics(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
  async getLevelTransitionStats(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    if (!verifyAdmin(req, res)) {
      return { success: false, error: 'Unauthorized' };
    }
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
