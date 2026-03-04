/**
 * Mobile Workouts Controller
 * ==========================
 *
 *   GET /mobile/workouts                      — Published workouts list (sync)
 *   GET /mobile/workouts/:id/training-config  — Full training config for a workout
 */

import { Controller, Get, Param, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { workoutService } from './workouts.service';

@Controller('mobile/workouts')
export class MobileWorkoutsController {
  /**
   * Returns all published workouts as a compact list for the Explore tab.
   * Requires a valid mobile token.
   */
  @Get()
  async list(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    const authResult = await verifyMobileToken(req);
    if (!authResult.success) {
      res.status(401);
      return { success: false, error: authResult.error || 'Unauthorized' };
    }

    try {
      const workouts = await workoutService.getPublishedForMobile();
      return {
        success: true,
        data: workouts,
        meta: {
          count: workouts.length,
          timestamp: new Date().toISOString(),
        },
      };
    } catch (error) {
      console.error('[Mobile Workouts] Error fetching list:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workouts' };
    }
  }

  /**
   * Returns the full training configuration for a workout.
   * Includes all exercise data, pose variants, position checks, and
   * feedback messages — everything the mobile training engine needs.
   *
   * Requires a valid mobile token.
   */
  @Get(':id/training-config')
  async getTrainingConfig(
    @Param('id') id: string,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
  ) {
    const authResult = await verifyMobileToken(req);
    if (!authResult.success) {
      res.status(401);
      return { success: false, error: authResult.error || 'Unauthorized' };
    }

    try {
      const config = await workoutService.getTrainingConfig(id);

      if (!config) {
        res.status(404);
        return { success: false, error: 'Workout not found or not published' };
      }

      return { success: true, data: config };
    } catch (error) {
      console.error('[Mobile Workouts] Error fetching training config:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workout training config' };
    }
  }
}
