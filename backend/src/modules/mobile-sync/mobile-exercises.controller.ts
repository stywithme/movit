import { Controller, Get, Param, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { exerciseService } from '@/modules/exercises/exercises.service';
import { buildAudioManifestForExerciseSlug } from './mobile-audio-manifest.service';
import { mobileSyncService } from './mobile-sync.service';

@Controller('mobile/exercises')
export class MobileExercisesController {
  @Get('substitutions')
  async substitutions(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('slug') slug: string,
    @Query('limit') limit?: string,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      if (!slug?.trim()) {
        res.status(400);
        return { success: false, error: 'slug query param is required' };
      }
      const lim = Math.min(50, Math.max(1, Number.parseInt(limit || '12', 10) || 12));
      const rows = await exerciseService.listSubstitutionCandidates(slug.trim(), lim);
      return { success: true, data: rows };
    } catch (error) {
      console.error('[MobileExercises] substitutions:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch substitutions' };
    }
  }

  /**
   * Full training config for one published exercise (standalone ensure path).
   * GET /mobile/exercises/:slug/training-config
   */
  @Get(':slug/training-config')
  async trainingConfig(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('slug') slug: string,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      const trimmed = slug?.trim();
      if (!trimmed) {
        res.status(400);
        return { success: false, error: 'slug is required' };
      }
      const config = await mobileSyncService.getExerciseTrainingConfig(trimmed);
      if (!config) {
        res.status(404);
        return { success: false, error: 'Exercise not found or not published' };
      }
      return { success: true, data: config };
    } catch (error) {
      console.error('[MobileExercises] training-config:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch exercise training config' };
    }
  }

  /**
   * Audio manifest for one published exercise (existing audio URLs only; no generation).
   */
  @Get(':slug/audio-manifest')
  async audioManifest(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('slug') slug: string
  ) {
    try {
      const authHeader = req.headers.authorization;
      if (authHeader) {
        const authResult = await verifyMobileToken(req);
        if (!authResult.success || !authResult.userId) {
          res.status(401);
          return { success: false, error: authResult.error || 'Unauthorized' };
        }
      }
      const trimmed = slug?.trim();
      if (!trimmed) {
        res.status(400);
        return { success: false, error: 'slug is required' };
      }
      const protocol = (req.headers['x-forwarded-proto'] as string) || req.protocol || 'http';
      const host = req.headers.host || 'localhost:3000';
      const baseUrl = `${protocol}://${host}`;

      const audioManifest = await buildAudioManifestForExerciseSlug(trimmed, baseUrl);
      if (!audioManifest) {
        res.status(404);
        return { success: false, error: 'Exercise not found or not published' };
      }

      return {
        success: true,
        data: {
          entityType: 'exercise' as const,
          slug: trimmed,
          timestamp: new Date().toISOString(),
          filesInManifest: audioManifest.files.length,
          audioManifest,
        },
      };
    } catch (error) {
      console.error('[MobileExercises] audio-manifest:', error);
      res.status(500);
      return { success: false, error: 'Failed to build audio manifest' };
    }
  }
}
