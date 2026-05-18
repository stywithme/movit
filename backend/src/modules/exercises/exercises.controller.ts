import { BadRequestException, Body, Controller, Delete, Get, Param, Post, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { exerciseService } from './exercises.service';
import { exerciseSubstitutionsService } from './exercise-substitutions.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { PositionCheckSchema, TrackedJointSchema } from './exercises.validation';
import { z } from 'zod';

const PoseVariantSchema = z.object({
  trackedJointsConfig: z.array(TrackedJointSchema).optional(),
  positionChecks: z.array(PositionCheckSchema).optional(),
}).passthrough();

const BilateralConfigPayloadSchema = z.object({
  switchMode: z.enum(['every_rep', 'after_all_reps']).optional(),
  switchEvery: z.number().int().min(1).optional(),
  startSide: z.enum(['left', 'right']).optional(),
}).passthrough();

const ExercisePayloadSchema = z.object({
  trackedJointsConfig: z.array(TrackedJointSchema).optional(),
  poseVariants: z.array(PoseVariantSchema).optional(),
  bilateralConfig: BilateralConfigPayloadSchema.nullable().optional(),
}).passthrough();

function validateExercisePayload(body: unknown) {
  const result = ExercisePayloadSchema.safeParse(body);
  if (!result.success) {
    const issues = result.error.issues.map((issue) => ({
      path: issue.path.join('.'),
      message: issue.message,
      code: issue.code,
    }));
    console.error('[ExerciseValidation] Payload rejected:', JSON.stringify(issues, null, 2));
    throw new BadRequestException({
      message: 'Exercise payload validation failed',
      errors: result.error.flatten().fieldErrors,
      details: issues,
    });
  }
}

@UseGuards(CaslGuard)
@Controller('exercises')
export class ExercisesController {
  @Get()
  @CheckPermission('read', 'Exercise')
  async list(
    @Query('status') status?: string,
    @Query('categoryId') categoryId?: string,
    @Query('search') search?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string,
    @Query('includeAttributes') includeAttributes?: string
  ) {
    try {
      const result = await exerciseService.list({
        status: (status as 'draft' | 'published') || undefined,
        categoryId: categoryId || undefined,
        search: search || undefined,
        page: Number.parseInt(page || '1', 10),
        limit: Number.parseInt(limit || '20', 10),
        includeAttributes: includeAttributes === 'true',
      });

      return {
        success: true,
        data: result.exercises,
        pagination: result.pagination,
      };
    } catch (error) {
      console.error('Error fetching exercises:', error);
      return { success: false, error: 'Failed to fetch exercises' };
    }
  }

  @Post()
  @CheckPermission('create', 'Exercise')
  async create(@Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.name || !body?.categoryId || !body?.countingMethodId) {
        res.status(400);
        return { success: false, error: 'Name, categoryId, and countingMethodId are required' };
      }

      if (!body.name.en && !body.name.ar) {
        res.status(400);
        return { success: false, error: 'Name must have at least English or Arabic value' };
      }

      validateExercisePayload(body);

      const exercise = await exerciseService.create(body);
      res.status(201);
      return { success: true, data: exercise };
    } catch (error) {
      if (error instanceof BadRequestException) throw error;
      console.error('Error creating exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to create exercise' };
    }
  }

  @Get('published')
  @CheckPermission('read', 'Exercise')
  async listPublished() {
    try {
      const exercises = await exerciseService.getPublished();
      return { success: true, data: exercises };
    } catch (error) {
      console.error('Error fetching published exercises:', error);
      return { success: false, error: 'Failed to fetch published exercises' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'Exercise')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const exercise = await exerciseService.getById(id);
      if (!exercise) {
        res.status(404);
        return { success: false, error: 'Exercise not found' };
      }
      return { success: true, data: exercise };
    } catch (error) {
      console.error('Error fetching exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch exercise' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'Exercise')
  async update(@Param('id') id: string, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      validateExercisePayload(body);

      const exercise = await exerciseService.update(id, body);
      return { success: true, data: exercise };
    } catch (error) {
      if (error instanceof BadRequestException) throw error;
      console.error('Error updating exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to update exercise' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'Exercise')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await exerciseService.delete(id);
      return { success: true, message: 'Exercise deleted successfully' };
    } catch (error) {
      console.error('Error deleting exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete exercise' };
    }
  }

  @Put(':id/publish')
  @CheckPermission('publish', 'Exercise')
  async publish(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const exercise = await exerciseService.publish(id);
      return { success: true, data: exercise };
    } catch (error) {
      console.error('Error publishing exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to publish exercise' };
    }
  }

  @Delete(':id/publish')
  @CheckPermission('publish', 'Exercise')
  async unpublish(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const exercise = await exerciseService.unpublish(id);
      return { success: true, data: exercise };
    } catch (error) {
      console.error('Error unpublishing exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to unpublish exercise' };
    }
  }

  @Get(':id/config')
  @CheckPermission('read', 'Exercise')
  async getConfig(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const config = await exerciseService.getExerciseConfig(id);
      if (!config) {
        res.status(404);
        return { success: false, error: 'Exercise not found' };
      }
      return { success: true, data: config };
    } catch (error) {
      console.error('Error generating android config:', error);
      res.status(500);
      return { success: false, error: 'Failed to generate config' };
    }
  }

  @Get(':id/substitutions')
  @CheckPermission('read', 'Exercise')
  async getSubstitutions(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const subs = await exerciseSubstitutionsService.getSubstitutions(id);
      return { success: true, data: subs };
    } catch (error) {
      console.error('Error fetching substitutions:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch substitutions' };
    }
  }
}
