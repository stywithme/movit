import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { exerciseService } from './exercises.service';

@Controller('exercises')
export class ExercisesController {
  @Get()
  async list(
    @Query('status') status?: string,
    @Query('categoryId') categoryId?: string,
    @Query('search') search?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    try {
      const result = await exerciseService.list({
        status: (status as 'draft' | 'published') || undefined,
        categoryId: categoryId || undefined,
        search: search || undefined,
        page: Number.parseInt(page || '1', 10),
        limit: Number.parseInt(limit || '20', 10),
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

      const exercise = await exerciseService.create(body);
      res.status(201);
      return { success: true, data: exercise };
    } catch (error) {
      console.error('Error creating exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to create exercise' };
    }
  }

  @Get('published')
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
  async update(@Param('id') id: string, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      const exercise = await exerciseService.update(id, body);
      return { success: true, data: exercise };
    } catch (error) {
      console.error('Error updating exercise:', error);
      res.status(500);
      return { success: false, error: 'Failed to update exercise' };
    }
  }

  @Delete(':id')
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
}
