import { Controller, Get, Put, Post, Req, Res, Param, Body, Query, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import type { ExerciseArchetype } from '@prisma/client';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { exerciseProgressionProfileService } from './exercise-progression-profile.service';

const VALID_ARCHETYPES = [
  'weighted_strength',
  'bodyweight_dynamic',
  'isometric_hold',
  'mobility_rom',
  'motor_control',
] as const;

@UseGuards(CaslGuard)
@Controller('admin/exercise-progression-profiles')
export class ExerciseProgressionProfileController {

  @Get()
  @CheckPermission('read', 'ExerciseProgressionProfile')
  async list(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('view') view?: string,
  ) {
    try {
      if (view === 'exercises') {
        const data = await exerciseProgressionProfileService.listExercisesWithProfileStatus();
        return { success: true, data };
      }

      const data = await exerciseProgressionProfileService.listProfiles();
      return { success: true, data };
    } catch (error) {
      console.error('[ProfileAdmin] List Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch profiles' };
    }
  }

  @Get(':exerciseId')
  @CheckPermission('read', 'ExerciseProgressionProfile')
  async getProfile(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('exerciseId') exerciseId: string,
  ) {
    try {
      const profile = await exerciseProgressionProfileService.getProfile(exerciseId);
      return { success: true, data: profile };
    } catch (error) {
      console.error('[ProfileAdmin] Get Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch profile' };
    }
  }

  @Put(':exerciseId')
  @CheckPermission('update', 'ExerciseProgressionProfile')
  async updateProfile(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('exerciseId') exerciseId: string,
    @Body() body: {
      repRange?: unknown;
      weightBounds?: unknown;
      durationBounds?: unknown;
      qualityGate?: unknown;
      promotionRule?: unknown;
      regressionRule?: unknown;
      difficultyLadder?: unknown;
    },
  ) {
    try {
      const profile = await exerciseProgressionProfileService.updateProfile(exerciseId, body);
      return { success: true, data: profile };
    } catch (error: any) {
      console.error('[ProfileAdmin] Update Error:', error);
      res.status(error.message?.includes('No profile') ? 404 : 500);
      return { success: false, error: error.message || 'Failed to update profile' };
    }
  }

  @Post(':exerciseId/generate')
  @CheckPermission('create', 'ExerciseProgressionProfile')
  async generateProfile(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('exerciseId') exerciseId: string,
    @Body() body: { archetype?: string },
  ) {
    try {
      const archetype = body.archetype as ExerciseArchetype;
      if (!archetype || !VALID_ARCHETYPES.includes(archetype as any)) {
        res.status(400);
        return { success: false, error: `Invalid archetype. Valid: ${VALID_ARCHETYPES.join(', ')}` };
      }

      const profile = await exerciseProgressionProfileService.generateDefaultProfile(exerciseId, archetype);
      return { success: true, data: profile };
    } catch (error) {
      console.error('[ProfileAdmin] Generate Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to generate profile' };
    }
  }

  @Put(':exerciseId/archetype')
  @CheckPermission('update', 'ExerciseProgressionProfile')
  async setArchetype(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Param('exerciseId') exerciseId: string,
    @Body() body: { archetype: string },
  ) {
    try {
      const archetype = body.archetype as ExerciseArchetype;
      if (!archetype || !VALID_ARCHETYPES.includes(archetype as any)) {
        res.status(400);
        return { success: false, error: `Invalid archetype. Valid: ${VALID_ARCHETYPES.join(', ')}` };
      }

      const profile = await exerciseProgressionProfileService.setArchetype(exerciseId, archetype);
      return { success: true, data: profile };
    } catch (error) {
      console.error('[ProfileAdmin] SetArchetype Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to set archetype' };
    }
  }

  @Post('bulk-generate')
  @CheckPermission('create', 'ExerciseProgressionProfile')
  async bulkGenerate(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Body() body: { archetype?: string },
  ) {
    try {
      const archetype = body.archetype as ExerciseArchetype | undefined;
      if (archetype && !VALID_ARCHETYPES.includes(archetype as any)) {
        res.status(400);
        return { success: false, error: `Invalid archetype. Valid: ${VALID_ARCHETYPES.join(', ')}` };
      }

      const result = await exerciseProgressionProfileService.bulkGenerateProfiles(archetype);
      return { success: true, data: result };
    } catch (error) {
      console.error('[ProfileAdmin] BulkGenerate Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to bulk generate profiles' };
    }
  }
}
