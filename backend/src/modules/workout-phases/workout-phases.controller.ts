import { Body, Controller, Delete, Get, Param, Post, Put, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { getAdminIdFromRequest } from '@/lib/auth/admin';
import { workoutPhasesService } from './workout-phases.service';
import { WorkoutPhaseInUseError } from './workout-phases.errors';
import { validateCreateWorkoutPhase, validateUpdateWorkoutPhase } from './workout-phases.validation';
import type { CreateWorkoutPhaseInput, UpdateWorkoutPhaseInput } from './workout-phases.types';

@UseGuards(CaslGuard)
@Controller('workout-phases')
export class WorkoutPhasesController {
  @Get()
  @CheckPermission('read', 'WorkoutPhase')
  async list(@Query('active') active?: string, @Query('search') search?: string) {
    try {
      const phases = await workoutPhasesService.list({
        active: active === undefined ? undefined : active === 'true' || active === '1',
        search: search || undefined,
      });

      return { success: true, data: phases };
    } catch (error) {
      console.error('Error fetching workout phases:', error);
      return { success: false, error: 'Failed to fetch workout phases' };
    }
  }

  @Post()
  @CheckPermission('create', 'WorkoutPhase')
  async create(
    @Body() body: CreateWorkoutPhaseInput,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateCreateWorkoutPhase(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      if (body.slug) {
        const existing = await workoutPhasesService.getBySlug(body.slug);
        if (existing) {
          res.status(409);
          return { success: false, error: 'Workout phase slug already exists' };
        }
      }

      const phase = await workoutPhasesService.create(body, getAdminIdFromRequest(req) ?? undefined);
      res.status(201);
      return { success: true, data: phase };
    } catch (error) {
      console.error('Error creating workout phase:', error);
      res.status(500);
      return { success: false, error: 'Failed to create workout phase' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'WorkoutPhase')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const phase = await workoutPhasesService.getById(id);
      if (!phase) {
        res.status(404);
        return { success: false, error: 'Workout phase not found' };
      }

      return { success: true, data: phase };
    } catch (error) {
      console.error('Error fetching workout phase:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workout phase' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'WorkoutPhase')
  async update(
    @Param('id') id: string,
    @Body() body: UpdateWorkoutPhaseInput,
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateUpdateWorkoutPhase(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const existing = await workoutPhasesService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Workout phase not found' };
      }

      if (body.slug) {
        const existingSlug = await workoutPhasesService.getBySlug(body.slug);
        if (existingSlug && existingSlug.id !== id) {
          res.status(409);
          return { success: false, error: 'Workout phase slug already exists' };
        }
      }

      const phase = await workoutPhasesService.update(id, body, getAdminIdFromRequest(req) ?? undefined);
      return { success: true, data: phase };
    } catch (error) {
      console.error('Error updating workout phase:', error);
      res.status(500);
      return { success: false, error: 'Failed to update workout phase' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'WorkoutPhase')
  async remove(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const existing = await workoutPhasesService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Workout phase not found' };
      }

      await workoutPhasesService.delete(id, getAdminIdFromRequest(req) ?? undefined);
      return { success: true, data: { deleted: true } };
    } catch (error) {
      if (error instanceof WorkoutPhaseInUseError) {
        res.status(409);
        return {
          success: false,
          error: `Cannot delete phase: it is used by ${error.templateCount} workout template(s)`,
          templateCount: error.templateCount,
        };
      }
      console.error('Error deleting workout phase:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete workout phase' };
    }
  }
}
