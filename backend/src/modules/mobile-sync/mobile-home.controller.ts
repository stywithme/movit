import { Controller, Get, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { getUserHomeStats } from '@/modules/training-sessions/training-sessions.service';
import { levelProfileService } from '@/modules/level-profile/level-profile.service';
import { activePlanService } from '@/modules/active-plan/active-plan.service';

@Controller('mobile/home')
export class MobileHomeController {
  @Get()
  async getHomeData(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const userId = authResult.userId;

      const [userStats, levelProfile, activePlan, todayPlan] = await Promise.all([
        getUserHomeStats(userId),
        levelProfileService.getLatest(userId).catch(() => null),
        activePlanService.getOrCreate(userId).catch(() => null),
        activePlanService.getTodayPlan(userId).catch(() => null),
      ]);

      return {
        success: true,
        data: {
          userStats,
          levelProfile,
          activePlan,
          todayPlan,
        },
        timestamp: new Date().toISOString(),
      };
    } catch (error) {
      console.error('[Mobile Home] Error fetching home data:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch home data' };
    }
  }
}
