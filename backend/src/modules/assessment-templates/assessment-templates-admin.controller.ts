/**
 * Assessment Templates Admin Controller
 * ======================================
 *
 *   GET    /admin/assessment-templates                          — List with filters
 *   POST   /admin/assessment-templates                          — Create
 *   GET    /admin/assessment-templates/:id                      — Get by ID
 *   PUT    /admin/assessment-templates/:id                      — Update
 *   DELETE /admin/assessment-templates/:id                      — Soft delete
 *   POST   /admin/assessment-templates/:id/publish              — Publish
 *   DELETE /admin/assessment-templates/:id/publish              — Unpublish
 *   POST   /admin/assessment-templates/:id/exercises            — Add exercise
 *   PUT    /admin/assessment-templates/:id/exercises/reorder    — Reorder exercises
 *   PUT    /admin/assessment-templates/:id/exercises/:entryId   — Update exercise
 *   DELETE /admin/assessment-templates/:id/exercises/:entryId   — Remove exercise
 *
 * All endpoints are protected by admin cookie-based auth.
 */

import { Controller, Get, Post, Put, Delete, Req, Res, Param, Body, Query, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { getAdminIdFromRequest } from '@/lib/auth/admin';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { assessmentTemplateService } from './assessment-templates-admin.service';

@UseGuards(CaslGuard)
@Controller('admin/assessment-templates')
export class AssessmentTemplatesAdminController {
  /**
   * GET /admin/assessment-templates — List templates with optional filters.
   */
  @Get()
  @CheckPermission('read', 'AssessmentTemplate')
  async list(
    @Req() req: Request,
    @Query('type') type: string | undefined,
    @Query('levelId') levelId: string | undefined,
    @Query('status') status: string | undefined,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const data = await assessmentTemplateService.list({ type, levelId, status });
      return { success: true, data };
    } catch (error) {
      console.error('[AssessmentTemplates] List Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch assessment templates' };
    }
  }

  /**
   * POST /admin/assessment-templates — Create a new template.
   */
  @Post()
  @CheckPermission('create', 'AssessmentTemplate')
  async create(
    @Req() req: Request,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response,
  ) {
    const adminId = getAdminIdFromRequest(req)!;
    try {
      if (!body?.name) {
        res.status(400);
        return { success: false, error: 'name is required' };
      }

      const data = await assessmentTemplateService.create(body, adminId);
      res.status(201);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] Create Error:', error);
      const message = error?.message || 'Failed to create assessment template';
      const isValidation =
        message.includes('required') ||
        message.includes('must') ||
        message.includes('weights');
      res.status(isValidation ? 400 : 500);
      return { success: false, error: message };
    }
  }

  /**
   * GET /admin/assessment-templates/:id — Get template by ID.
   */
  @Get(':id')
  @CheckPermission('read', 'AssessmentTemplate')
  async getById(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const data = await assessmentTemplateService.getById(id);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] GetById Error:', error);
      const message = error?.message || 'Failed to fetch assessment template';
      res.status(message === 'Assessment template not found' ? 404 : 500);
      return { success: false, error: message };
    }
  }

  /**
   * PUT /admin/assessment-templates/:id — Update template.
   */
  @Put(':id')
  @CheckPermission('update', 'AssessmentTemplate')
  async update(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response,
  ) {
    const adminId = getAdminIdFromRequest(req)!;
    try {
      const data = await assessmentTemplateService.update(id, body, adminId);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] Update Error:', error);
      const message = error?.message || 'Failed to update assessment template';
      if (message === 'Assessment template not found') {
        res.status(404);
      } else {
        const isValidation =
          message.includes('required') ||
          message.includes('must') ||
          message.includes('weights');
        res.status(isValidation ? 400 : 500);
      }
      return { success: false, error: message };
    }
  }

  /**
   * DELETE /admin/assessment-templates/:id — Soft delete template.
   */
  @Delete(':id')
  @CheckPermission('delete', 'AssessmentTemplate')
  async delete(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      await assessmentTemplateService.delete(id);
      return { success: true, data: { deleted: true } };
    } catch (error: any) {
      console.error('[AssessmentTemplates] Delete Error:', error);
      const message = error?.message || 'Failed to delete assessment template';
      if (message === 'Assessment template not found') {
        res.status(404);
      } else if (message.includes('Cannot delete')) {
        res.status(400);
      } else {
        res.status(500);
      }
      return { success: false, error: message };
    }
  }

  /**
   * POST /admin/assessment-templates/:id/publish — Publish template.
   */
  @Post(':id/publish')
  @CheckPermission('publish', 'AssessmentTemplate')
  async publish(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const data = await assessmentTemplateService.publish(id);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] Publish Error:', error);
      const message = error?.message || 'Failed to publish assessment template';
      if (message === 'Assessment template not found') {
        res.status(404);
      } else if (message.includes('Cannot publish')) {
        res.status(400);
      } else {
        res.status(500);
      }
      return { success: false, error: message };
    }
  }

  /**
   * DELETE /admin/assessment-templates/:id/publish — Unpublish template.
   */
  @Delete(':id/publish')
  @CheckPermission('publish', 'AssessmentTemplate')
  async unpublish(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const data = await assessmentTemplateService.unpublish(id);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] Unpublish Error:', error);
      const message = error?.message || 'Failed to unpublish assessment template';
      res.status(message === 'Assessment template not found' ? 404 : 500);
      return { success: false, error: message };
    }
  }

  /**
   * POST /admin/assessment-templates/:id/exercises — Add exercise to template.
   */
  @Post(':id/exercises')
  @CheckPermission('update', 'AssessmentTemplate')
  async addExercise(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      if (!body?.exerciseId || !body?.targetRegion) {
        res.status(400);
        return { success: false, error: 'exerciseId and targetRegion are required' };
      }

      const data = await assessmentTemplateService.addExercise(id, body);
      res.status(201);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] AddExercise Error:', error);
      const message = error?.message || 'Failed to add exercise';
      if (message.includes('not found')) {
        res.status(404);
      } else if (message.includes('Unique constraint')) {
        res.status(409);
        return { success: false, error: 'This exercise is already in the template' };
      } else {
        res.status(500);
      }
      return { success: false, error: message };
    }
  }

  /**
   * PUT /admin/assessment-templates/:id/exercises/reorder — Reorder exercises.
   * Body: { orderedIds: string[] }
   */
  @Put(':id/exercises/reorder')
  @CheckPermission('update', 'AssessmentTemplate')
  async reorderExercises(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      if (!Array.isArray(body?.orderedIds) || body.orderedIds.length === 0) {
        res.status(400);
        return { success: false, error: 'orderedIds array is required' };
      }

      const data = await assessmentTemplateService.reorderExercises(id, body.orderedIds);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] ReorderExercises Error:', error);
      const message = error?.message || 'Failed to reorder exercises';
      res.status(400);
      return { success: false, error: message };
    }
  }

  /**
   * PUT /admin/assessment-templates/:id/exercises/:entryId — Update exercise entry.
   */
  @Put(':id/exercises/:entryId')
  @CheckPermission('update', 'AssessmentTemplate')
  async updateExercise(
    @Req() req: Request,
    @Param('id') id: string,
    @Param('entryId') entryId: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      const data = await assessmentTemplateService.updateExercise(id, entryId, body);
      return { success: true, data };
    } catch (error: any) {
      console.error('[AssessmentTemplates] UpdateExercise Error:', error);
      const message = error?.message || 'Failed to update exercise entry';
      res.status(message.includes('not found') ? 404 : 500);
      return { success: false, error: message };
    }
  }

  /**
   * DELETE /admin/assessment-templates/:id/exercises/:entryId — Remove exercise entry.
   */
  @Delete(':id/exercises/:entryId')
  @CheckPermission('update', 'AssessmentTemplate')
  async removeExercise(
    @Req() req: Request,
    @Param('id') id: string,
    @Param('entryId') entryId: string,
    @Res({ passthrough: true }) res: Response,
  ) {
    try {
      await assessmentTemplateService.removeExercise(id, entryId);
      return { success: true, data: { deleted: true } };
    } catch (error: any) {
      console.error('[AssessmentTemplates] RemoveExercise Error:', error);
      const message = error?.message || 'Failed to remove exercise entry';
      res.status(message.includes('not found') ? 404 : 500);
      return { success: false, error: message };
    }
  }
}
