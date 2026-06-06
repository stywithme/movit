/**
 * Level Profile Controller
 * ========================
 *
 * REST endpoints for user level profiles.
 *
 *   GET /mobile/level-profile         — Latest level profile
 *   GET /mobile/level-profile/history — Level profile history
 */

import { Controller, Get, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { levelProfileService } from './level-profile.service';
import { getPrisma } from '@/lib/prisma/client';

@Controller('mobile/level-profile')
export class LevelProfileController {
  /**
   * GET /mobile/level-profile — Get the user's current level profile.
   */
  @Get()
  async getLatest(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const profile = await levelProfileService.getLatest(authResult.userId);

      if (!profile) {
        res.status(404);
        return {
          success: false,
          error: 'No level profile found. Complete an assessment first.',
        };
      }

      return { success: true, data: profile };
    } catch (error) {
      console.error('[LevelProfile] Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch level profile' };
    }
  }

  /**
   * GET /mobile/level-profile/history — Get level profile history.
   */
  @Get('history')
  async getHistory(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const profiles = await levelProfileService.getHistory(authResult.userId);
      return { success: true, data: profiles };
    } catch (error) {
      console.error('[LevelProfile] History Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch level profile history' };
    }
  }

  /**
   * GET /mobile/level-profile/levels — Get all level definitions.
   * Static data used by the mobile app for display.
   */
  @Get('levels')
  async getLevels(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const prisma = await getPrisma();
      const levels = await prisma.level.findMany({
        orderBy: { number: 'asc' },
      });

      return {
        success: true,
        data: levels.map((l) => ({
          number: l.number,
          code: l.code,
          name: l.name,
          description: l.description,
          color: l.color,
          icon: l.icon,
          entryThreshold: l.entryThreshold,
          defaults: {
            setsRange: { min: l.defaultSetsMin, max: l.defaultSetsMax },
            repsRange: { min: l.defaultRepsMin, max: l.defaultRepsMax },
            intensityGuide: l.defaultIntensityGuide,
            restBetweenSetsMs: l.defaultRestBetweenSetsMs,
            workoutDurationRange: { min: l.defaultWorkoutDurMin, max: l.defaultWorkoutDurMax },
            weeklyFrequencyRange: { min: l.defaultWeeklyFreqMin, max: l.defaultWeeklyFreqMax },
          },
        })),
      };
    } catch (error) {
      console.error('[LevelProfile] Levels Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch levels' };
    }
  }
}
