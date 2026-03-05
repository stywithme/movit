/**
 * Levels Admin Controller
 * =======================
 *
 *   GET    /admin/levels           — List all levels with user counts
 *   POST   /admin/levels           — Create a new level
 *   PUT    /admin/levels/reorder   — Reorder levels
 *   PUT    /admin/levels/:id       — Update a level
 *   DELETE /admin/levels/:id       — Delete a level
 *
 * All endpoints are protected by admin cookie-based auth.
 */

import { Controller, Get, Post, Put, Delete, Req, Res, Param, Body, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { levelsAdminService } from './levels-admin.service';

@UseGuards(CaslGuard)
@Controller('admin/levels')
export class LevelsAdminController {
  /**
   * GET /admin/levels — List all levels ordered by number, with user counts.
   */
  @Get()
  @CheckPermission('read', 'Level')
  async list(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const data = await levelsAdminService.list();
      return { success: true, data };
    } catch (error) {
      console.error('[Levels] List Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch levels' };
    }
  }

  /**
   * POST /admin/levels — Create a new level.
   */
  @Post()
  @CheckPermission('create', 'Level')
  async create(@Req() req: Request, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.number || !body?.code || !body?.name) {
        res.status(400);
        return { success: false, error: 'number, code, and name are required' };
      }
      if (body.entryThreshold == null) {
        res.status(400);
        return { success: false, error: 'entryThreshold is required' };
      }

      const data = await levelsAdminService.create(body);
      res.status(201);
      return { success: true, data };
    } catch (error: any) {
      console.error('[Levels] Create Error:', error);
      const message = error?.message || 'Failed to create level';
      const isValidation = message.includes('already exists') || message.includes('Threshold') || message.includes('must have');
      res.status(isValidation ? 400 : 500);
      return { success: false, error: message };
    }
  }

  /**
   * PUT /admin/levels/reorder — Reorder levels.
   * Body: { orderedIds: string[] }
   */
  @Put('reorder')
  @CheckPermission('update', 'Level')
  async reorder(@Req() req: Request, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      if (!Array.isArray(body?.orderedIds) || body.orderedIds.length === 0) {
        res.status(400);
        return { success: false, error: 'orderedIds array is required' };
      }

      const data = await levelsAdminService.reorder(body.orderedIds);
      return { success: true, data };
    } catch (error: any) {
      console.error('[Levels] Reorder Error:', error);
      const message = error?.message || 'Failed to reorder levels';
      res.status(400);
      return { success: false, error: message };
    }
  }

  /**
   * PUT /admin/levels/:id — Update a level.
   */
  @Put(':id')
  @CheckPermission('update', 'Level')
  async update(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const data = await levelsAdminService.update(id, body);
      return { success: true, data };
    } catch (error: any) {
      console.error('[Levels] Update Error:', error);
      const message = error?.message || 'Failed to update level';
      if (message === 'Level not found') {
        res.status(404);
      } else {
        const isValidation = message.includes('already exists') || message.includes('Threshold') || message.includes('must have');
        res.status(isValidation ? 400 : 500);
      }
      return { success: false, error: message };
    }
  }

  /**
   * DELETE /admin/levels/:id — Delete a level.
   */
  @Delete(':id')
  @CheckPermission('delete', 'Level')
  async delete(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      await levelsAdminService.delete(id);
      return { success: true, data: { deleted: true } };
    } catch (error: any) {
      console.error('[Levels] Delete Error:', error);
      const message = error?.message || 'Failed to delete level';
      if (message === 'Level not found') {
        res.status(404);
      } else if (message.includes('Cannot delete')) {
        res.status(400);
      } else {
        res.status(500);
      }
      return { success: false, error: message };
    }
  }
}
