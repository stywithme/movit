import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { workoutService } from './workouts.service';
import { validateCreateWorkout, validateUpdateWorkout } from './workouts.validation';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@UseGuards(CaslGuard)
@Controller('workouts')
export class WorkoutsController {
  @Get()
  @CheckPermission('read', 'Workout')
  async list(
    @Query('status') status?: string,
    @Query('search') search?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    try {
      const result = await workoutService.list({
        status: (status as 'draft' | 'published') || undefined,
        search: search || undefined,
        page: Number.parseInt(page || '1', 10),
        limit: Number.parseInt(limit || '20', 10),
      });

      return {
        success: true,
        data: result.workouts,
        pagination: result.pagination,
      };
    } catch (error) {
      console.error('Error fetching workouts:', error);
      return { success: false, error: 'Failed to fetch workouts' };
    }
  }

  @Post()
  @CheckPermission('create', 'Workout')
  async create(@Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      const errors = validateCreateWorkout(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const workout = await workoutService.create(body);
      res.status(201);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error creating workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to create workout' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'Workout')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const workout = await workoutService.getById(id);
      if (!workout) {
        res.status(404);
        return { success: false, error: 'Workout not found' };
      }
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error fetching workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workout' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'Workout')
  async update(@Param('id') id: string, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      const errors = validateUpdateWorkout(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const workout = await workoutService.update(id, body);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error updating workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to update workout' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'Workout')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await workoutService.delete(id);
      return { success: true, message: 'Workout deleted successfully' };
    } catch (error) {
      console.error('Error deleting workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete workout' };
    }
  }

  @Post(':id/publish')
  @CheckPermission('publish', 'Workout')
  async publish(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const workout = await workoutService.publish(id);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error publishing workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to publish workout' };
    }
  }

  @Delete(':id/publish')
  @CheckPermission('publish', 'Workout')
  async unpublish(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const workout = await workoutService.unpublish(id);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error unpublishing workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to unpublish workout' };
    }
  }

  @Post(':id/duplicate')
  @CheckPermission('create', 'Workout')
  async duplicate(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const workout = await workoutService.duplicate(id);
      res.status(201);
      return { success: true, data: workout };
    } catch (error) {
      console.error('Error duplicating workout:', error);
      res.status(500);
      return { success: false, error: 'Failed to duplicate workout' };
    }
  }
}
