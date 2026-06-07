import { Body, Controller, Delete, Get, Param, Post, Put, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { workoutService } from './workout-templates.service';
import { validateCreateWorkout, validateUpdateWorkout } from './workout-templates.validation';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { getAdminIdFromRequest } from '@/lib/auth/admin';

@UseGuards(CaslGuard)
@Controller('workout-templates')
export class WorkoutTemplatesController {
  @Get()
  @CheckPermission('read', 'WorkoutTemplate')
  async list(
    @Query('status') status?: string,
    @Query('search') search?: string,
    @Query('featured') featured?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    try {
      const isFeatured =
        featured === 'true' || featured === '1'
          ? true
          : featured === 'false' || featured === '0'
            ? false
            : undefined;

      const result = await workoutService.list({
        status: (status as 'draft' | 'published') || undefined,
        search: search || undefined,
        isFeatured,
        page: Number.parseInt(page || '1', 10),
        limit: Number.parseInt(limit || '20', 10),
      });

      return {
        success: true,
        data: result.workouts,
        pagination: result.pagination,
      };
    } catch (error) {
      console.error('Error fetching workout templates:', error);
      return { success: false, error: 'Failed to fetch workout templates' };
    }
  }

  @Post()
  @CheckPermission('create', 'WorkoutTemplate')
  async create(@Body() body: any, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const errors = validateCreateWorkout(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const workout = await workoutService.create(body, getAdminIdFromRequest(req) ?? undefined);
      res.status(201);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error creating workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to create workout template' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'WorkoutTemplate')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const workout = await workoutService.getById(id);
      if (!workout) {
        res.status(404);
        return { success: false, error: 'Workout template not found' };
      }
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error fetching workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workout template' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'WorkoutTemplate')
  async update(@Param('id') id: string, @Body() body: any, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const errors = validateUpdateWorkout(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const existing = await workoutService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Workout template not found' };
      }

      const workout = await workoutService.update(id, body, getAdminIdFromRequest(req) ?? undefined);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error updating workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to update workout template' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'WorkoutTemplate')
  async remove(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      await workoutService.delete(id, getAdminIdFromRequest(req) ?? undefined);
      return { success: true, message: 'Workout template deleted successfully' };
    } catch (error) {
      console.error('Error deleting workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete workout template' };
    }
  }

  @Post(':id/publish')
  @CheckPermission('update', 'WorkoutTemplate')
  async publish(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const existing = await workoutService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Workout template not found' };
      }
      const workout = await workoutService.publish(id, getAdminIdFromRequest(req) ?? undefined);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error publishing workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to publish workout template' };
    }
  }

  @Delete(':id/publish')
  @CheckPermission('update', 'WorkoutTemplate')
  async unpublish(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const existing = await workoutService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Workout template not found' };
      }
      const workout = await workoutService.unpublish(id, getAdminIdFromRequest(req) ?? undefined);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error unpublishing workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to unpublish workout template' };
    }
  }

  @Post(':id/duplicate')
  @CheckPermission('update', 'WorkoutTemplate')
  async duplicate(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const existing = await workoutService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Workout template not found' };
      }
      const workout = await workoutService.duplicate(id, getAdminIdFromRequest(req) ?? undefined);
      res.status(201);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error duplicating workout template:', error);
      res.status(500);
      return { success: false, error: 'Failed to duplicate workout template' };
    }
  }
}
