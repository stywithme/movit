import { Controller, Get, Post, Put, Req, Res, Param, Body, Query, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { exerciseProgressionProfileService } from './exercise-progression-profile.service';

@UseGuards(CaslGuard)
@Controller('admin/exercise-progression')
export class ExerciseProgressionProfileController {
  @Get('archetypes')
  @CheckPermission('read', 'ProgressionRule')
  async getArchetypes(@Res({ passthrough: true }) res: Response) {
    try {
      const archetypes = exerciseProgressionProfileService.getAvailableArchetypes();
      return { success: true, data: archetypes };
    } catch (error) {
      console.error('[ExerciseProgression] Archetypes error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch archetypes' };
    }
  }

  @Get('exercises')
  @CheckPermission('read', 'ProgressionRule')
  async listExercises(
    @Res({ passthrough: true }) res: Response,
    @Query('search') search?: string,
  ) {
    try {
      const data = await exerciseProgressionProfileService.listExercisesWithProfileStatus(search);
      return { success: true, data };
    } catch (error) {
      console.error('[ExerciseProgression] List error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch exercises' };
    }
  }

  @Get('profiles')
  @CheckPermission('read', 'ProgressionRule')
  async listProfiles(
    @Res({ passthrough: true }) res: Response,
    @Query('archetype') archetype?: string,
  ) {
    try {
      const data = await exerciseProgressionProfileService.listProfiles(
        archetype ? { archetype } : undefined,
      );
      return { success: true, data };
    } catch (error) {
      console.error('[ExerciseProgression] List profiles error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch profiles' };
    }
  }

  @Get(':exerciseId')
  @CheckPermission('read', 'ProgressionRule')
  async getProfile(
    @Param('exerciseId') exerciseId: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const profile = await exerciseProgressionProfileService.getProfile(exerciseId);
      if (!profile) {
        return { success: true, data: null };
      }

      const validation = await exerciseProgressionProfileService.validateProfile(exerciseId);
      return { success: true, data: { ...profile, validation } };
    } catch (error) {
      console.error('[ExerciseProgression] Get error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch profile' };
    }
  }

  @Post(':exerciseId/generate')
  @CheckPermission('update', 'ProgressionRule')
  async generateProfile(
    @Param('exerciseId') exerciseId: string,
    @Body() body: { archetype: string },
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const profile = await exerciseProgressionProfileService.generateDefaultProfile(
        exerciseId,
        body.archetype,
      );
      return { success: true, data: profile };
    } catch (error: any) {
      console.error('[ExerciseProgression] Generate error:', error);
      res.status(400);
      return { success: false, error: error.message || 'Failed to generate profile' };
    }
  }

  @Put(':exerciseId')
  @CheckPermission('update', 'ProgressionRule')
  async updateProfile(
    @Param('exerciseId') exerciseId: string,
    @Body() body: Record<string, unknown>,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const profile = await exerciseProgressionProfileService.updateProfile(exerciseId, body);
      const validation = await exerciseProgressionProfileService.validateProfile(exerciseId);
      return { success: true, data: { ...profile, validation } };
    } catch (error: any) {
      console.error('[ExerciseProgression] Update error:', error);
      res.status(400);
      return { success: false, error: error.message || 'Failed to update profile' };
    }
  }

  @Post(':exerciseId/archetype')
  @CheckPermission('update', 'ProgressionRule')
  async setArchetype(
    @Param('exerciseId') exerciseId: string,
    @Body() body: { archetype: string },
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const profile = await exerciseProgressionProfileService.setArchetype(
        exerciseId,
        body.archetype,
      );
      return { success: true, data: profile };
    } catch (error: any) {
      console.error('[ExerciseProgression] Set archetype error:', error);
      res.status(400);
      return { success: false, error: error.message || 'Failed to set archetype' };
    }
  }

  @Post('bulk-generate')
  @CheckPermission('update', 'ProgressionRule')
  async bulkGenerate(
    @Body() body: { exerciseIds: string[]; archetype: string },
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const profiles = await exerciseProgressionProfileService.bulkGenerateProfiles(
        body.exerciseIds,
        body.archetype,
      );
      return { success: true, data: { count: profiles.length } };
    } catch (error: any) {
      console.error('[ExerciseProgression] Bulk generate error:', error);
      res.status(400);
      return { success: false, error: error.message || 'Failed to bulk generate profiles' };
    }
  }

  @Get(':exerciseId/validate')
  @CheckPermission('read', 'ProgressionRule')
  async validateProfile(
    @Param('exerciseId') exerciseId: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const validation = await exerciseProgressionProfileService.validateProfile(exerciseId);
      return { success: true, data: validation };
    } catch (error) {
      console.error('[ExerciseProgression] Validate error:', error);
      res.status(500);
      return { success: false, error: 'Failed to validate profile' };
    }
  }
}
