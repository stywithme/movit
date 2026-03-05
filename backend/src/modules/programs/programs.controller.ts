import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { programService } from './programs.service';
import {
  validateCreateProgram,
  validateDayInput,
  validateSessionInput,
  validateSessionItemInput,
  validateUpdateProgram,
  validateWeekInput,
} from './programs.validation';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@UseGuards(CaslGuard)
@Controller('programs')
export class ProgramsController {
  @Get()
  @CheckPermission('read', 'Program')
  async list(
    @Query('status') status?: string,
    @Query('search') search?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    try {
      const result = await programService.list({
        status: (status as 'draft' | 'published') || undefined,
        search: search || undefined,
        page: Number.parseInt(page || '1', 10),
        limit: Number.parseInt(limit || '20', 10),
      });

      return {
        success: true,
        data: result.programs,
        pagination: result.pagination,
      };
    } catch (error) {
      console.error('Error fetching programs:', error);
      return { success: false, error: 'Failed to fetch programs' };
    }
  }

  @Post()
  @CheckPermission('create', 'Program')
  async create(@Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      const errors = validateCreateProgram(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const program = await programService.create(body);
      res.status(201);
      return { success: true, data: program };
    } catch (error) {
      console.error('Error creating program:', error);
      res.status(500);
      return { success: false, error: 'Failed to create program' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'Program')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const program = await programService.getById(id);
      if (!program) {
        res.status(404);
        return { success: false, error: 'Program not found' };
      }
      return { success: true, data: program };
    } catch (error) {
      console.error('Error fetching program:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch program' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'Program')
  async update(@Param('id') id: string, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      const errors = validateUpdateProgram(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const program = await programService.update(id, body);
      return { success: true, data: program };
    } catch (error) {
      console.error('Error updating program:', error);
      res.status(500);
      return { success: false, error: 'Failed to update program' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'Program')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await programService.delete(id);
      return { success: true, message: 'Program deleted successfully' };
    } catch (error) {
      console.error('Error deleting program:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete program' };
    }
  }

  @Post(':id/publish')
  @CheckPermission('publish', 'Program')
  async publish(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const program = await programService.publish(id);
      return { success: true, data: program };
    } catch (error) {
      console.error('Error publishing program:', error);
      res.status(500);
      return { success: false, error: 'Failed to publish program' };
    }
  }

  @Delete(':id/publish')
  @CheckPermission('publish', 'Program')
  async unpublish(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const program = await programService.unpublish(id);
      return { success: true, data: program };
    } catch (error) {
      console.error('Error unpublishing program:', error);
      res.status(500);
      return { success: false, error: 'Failed to unpublish program' };
    }
  }

  @Post(':id/duplicate')
  @CheckPermission('create', 'Program')
  async duplicate(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const program = await programService.duplicate(id);
      res.status(201);
      return { success: true, data: program };
    } catch (error) {
      console.error('Error duplicating program:', error);
      res.status(500);
      return { success: false, error: 'Failed to duplicate program' };
    }
  }

  @Post(':id/weeks')
  @CheckPermission('update', 'Program')
  async createWeek(
    @Param('id') programId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateWeekInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const week = await programService.createWeek(programId, body);
      if (!week) {
        res.status(404);
        return { success: false, error: 'Program not found' };
      }
      return { success: true, data: week };
    } catch (error) {
      console.error('Error creating week:', error);
      res.status(500);
      return { success: false, error: 'Failed to create week' };
    }
  }

  @Put(':id/weeks/:weekId')
  @CheckPermission('update', 'Program')
  async updateWeek(
    @Param('id') programId: string,
    @Param('weekId') weekId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateWeekInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }

      const week = await programService.updateWeek(programId, weekId, body);
      if (!week) {
        res.status(404);
        return { success: false, error: 'Week not found' };
      }
      return { success: true, data: week };
    } catch (error) {
      console.error('Error updating week:', error);
      res.status(500);
      return { success: false, error: 'Failed to update week' };
    }
  }

  @Delete(':id/weeks/:weekId')
  @CheckPermission('update', 'Program')
  async deleteWeek(
    @Param('id') programId: string,
    @Param('weekId') weekId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const result = await programService.deleteWeek(programId, weekId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'Week not found' };
      }
      return { success: true, message: 'Week deleted successfully' };
    } catch (error) {
      console.error('Error deleting week:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete week' };
    }
  }

  @Post(':id/weeks/:weekId/copy-to/:targetWeek')
  @CheckPermission('update', 'Program')
  async copyWeek(
    @Param('id') programId: string,
    @Param('weekId') weekId: string,
    @Param('targetWeek') targetWeek: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const targetWeekNumber = Number.parseInt(targetWeek, 10);
      if (!Number.isFinite(targetWeekNumber) || targetWeekNumber <= 0) {
        res.status(400);
        return { success: false, error: 'Invalid target week number' };
      }
      const week = await programService.copyWeek(programId, weekId, targetWeekNumber);
      if (!week) {
        res.status(404);
        return { success: false, error: 'Week not found' };
      }
      res.status(201);
      return { success: true, data: week };
    } catch (error) {
      console.error('Error copying week:', error);
      res.status(500);
      return { success: false, error: 'Failed to copy week' };
    }
  }

  @Post(':programId/weeks/:weekId/days')
  @CheckPermission('update', 'Program')
  async createDay(
    @Param('programId') programId: string,
    @Param('weekId') weekId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateDayInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }
      const day = await programService.createDay(programId, weekId, body);
      if (!day) {
        res.status(404);
        return { success: false, error: 'Week not found' };
      }
      return { success: true, data: day };
    } catch (error) {
      console.error('Error creating day:', error);
      res.status(500);
      return { success: false, error: 'Failed to create day' };
    }
  }

  @Put(':programId/weeks/:weekId/days/:dayId')
  @CheckPermission('update', 'Program')
  async updateDay(
    @Param('programId') programId: string,
    @Param('dayId') dayId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateDayInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }
      const day = await programService.updateDay(programId, dayId, body);
      if (!day) {
        res.status(404);
        return { success: false, error: 'Day not found' };
      }
      return { success: true, data: day };
    } catch (error) {
      console.error('Error updating day:', error);
      res.status(500);
      return { success: false, error: 'Failed to update day' };
    }
  }

  @Delete(':programId/weeks/:weekId/days/:dayId')
  @CheckPermission('update', 'Program')
  async deleteDay(
    @Param('programId') programId: string,
    @Param('dayId') dayId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const result = await programService.deleteDay(programId, dayId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'Day not found' };
      }
      return { success: true, message: 'Day deleted successfully' };
    } catch (error) {
      console.error('Error deleting day:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete day' };
    }
  }

  @Post(':programId/weeks/:weekId/days/:dayId/sessions')
  @CheckPermission('update', 'Program')
  async createSession(
    @Param('programId') programId: string,
    @Param('dayId') dayId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateSessionInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }
      const session = await programService.createSession(programId, dayId, body);
      if (!session) {
        res.status(404);
        return { success: false, error: 'Day not found' };
      }
      return { success: true, data: session };
    } catch (error) {
      console.error('Error creating session:', error);
      res.status(500);
      return { success: false, error: 'Failed to create session' };
    }
  }

  @Put(':programId/sessions/:sessionId')
  @CheckPermission('update', 'Program')
  async updateSession(
    @Param('programId') programId: string,
    @Param('sessionId') sessionId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateSessionInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }
      const session = await programService.updateSession(programId, sessionId, body);
      if (!session) {
        res.status(404);
        return { success: false, error: 'Session not found' };
      }
      return { success: true, data: session };
    } catch (error) {
      console.error('Error updating session:', error);
      res.status(500);
      return { success: false, error: 'Failed to update session' };
    }
  }

  @Delete(':programId/sessions/:sessionId')
  @CheckPermission('update', 'Program')
  async deleteSession(
    @Param('programId') programId: string,
    @Param('sessionId') sessionId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const result = await programService.deleteSession(programId, sessionId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'Session not found' };
      }
      return { success: true, message: 'Session deleted successfully' };
    } catch (error) {
      console.error('Error deleting session:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete session' };
    }
  }

  @Post(':programId/sessions/:sessionId/items')
  @CheckPermission('update', 'Program')
  async createSessionItem(
    @Param('programId') programId: string,
    @Param('sessionId') sessionId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateSessionItemInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }
      const item = await programService.createSessionItem(programId, sessionId, body);
      if (!item) {
        res.status(404);
        return { success: false, error: 'Session not found' };
      }
      return { success: true, data: item };
    } catch (error) {
      console.error('Error creating session item:', error);
      res.status(500);
      return { success: false, error: 'Failed to create session item' };
    }
  }

  @Put(':programId/sessions/:sessionId/items/:itemId')
  @CheckPermission('update', 'Program')
  async updateSessionItem(
    @Param('programId') programId: string,
    @Param('itemId') itemId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const errors = validateSessionItemInput(body);
      if (errors.length > 0) {
        res.status(400);
        return { success: false, errors };
      }
      const item = await programService.updateSessionItem(programId, itemId, body);
      if (!item) {
        res.status(404);
        return { success: false, error: 'Item not found' };
      }
      return { success: true, data: item };
    } catch (error) {
      console.error('Error updating session item:', error);
      res.status(500);
      return { success: false, error: 'Failed to update session item' };
    }
  }

  @Delete(':programId/sessions/:sessionId/items/:itemId')
  @CheckPermission('update', 'Program')
  async deleteSessionItem(
    @Param('programId') programId: string,
    @Param('itemId') itemId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const result = await programService.deleteSessionItem(programId, itemId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'Item not found' };
      }
      return { success: true, message: 'Item deleted successfully' };
    } catch (error) {
      console.error('Error deleting session item:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete session item' };
    }
  }

  @Post(':programId/sessions/:sessionId/import-workout/:workoutId')
  @CheckPermission('update', 'Program')
  async importWorkout(
    @Param('programId') programId: string,
    @Param('sessionId') sessionId: string,
    @Param('workoutId') workoutId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const session = await programService.importWorkoutToSession(programId, sessionId, workoutId);
      if (!session) {
        res.status(404);
        return { success: false, error: 'Session or workout not found' };
      }
      return { success: true, data: session };
    } catch (error) {
      console.error('Error importing workout into session:', error);
      res.status(500);
      return { success: false, error: 'Failed to import workout' };
    }
  }
}
