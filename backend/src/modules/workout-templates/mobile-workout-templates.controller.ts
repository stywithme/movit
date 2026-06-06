/**
 * Mobile Workouts Controller
 * ==========================
 *
 *   GET /mobile/workout-templates                      — Published workout templates (sync)
 *   GET /mobile/workout-templates/:id/training-config  — Full training config for a template
 */

import { Controller, Get, Param, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { workoutService } from './workout-templates.service';
import { buildAudioManifestForWorkoutSlug } from '@/modules/mobile-sync/mobile-audio-manifest.service';

@Controller('mobile/workout-templates')
export class MobileWorkoutTemplatesController {
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
   * Audio manifest for one published workout (all referenced exercises; no generation).
   */
  @Get(':slug/audio-manifest')
  async audioManifest(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('slug') slug: string
  ) {
    const authHeader = req.headers.authorization;
    if (authHeader) {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
    }

    try {
      const trimmed = slug?.trim();
      if (!trimmed) {
        res.status(400);
        return { success: false, error: 'slug is required' };
      }
      const protocol = (req.headers['x-forwarded-proto'] as string) || req.protocol || 'http';
      const host = req.headers.host || 'localhost:3000';
      const baseUrl = `${protocol}://${host}`;

      const audioManifest = await buildAudioManifestForWorkoutSlug(trimmed, baseUrl);
      if (!audioManifest) {
        res.status(404);
        return { success: false, error: 'Workout not found or not published' };
      }

      return {
        success: true,
        data: {
          entityType: 'workout' as const,
          slug: trimmed,
          timestamp: new Date().toISOString(),
          filesInManifest: audioManifest.files.length,
          audioManifest,
        },
      };
    } catch (error) {
      console.error('[Mobile Workouts] Error building audio manifest:', error);
      res.status(500);
      return { success: false, error: 'Failed to build audio manifest' };
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
