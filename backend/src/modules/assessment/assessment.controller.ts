/**
 * Assessment Controller - Body Scan
 * ==================================
 *
 * REST endpoints for Body Scan assessment results.
 */

import { Body, Controller, Delete, Get, Param, Post, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { assessmentService } from './assessment.service';
import type { BodyScanResultCreate } from './assessment.types';

@Controller('assessment')
export class AssessmentController {
  /**
   * POST /assessment - Create a new Body Scan assessment
   */
  @Post()
  async create(
    @Req() req: Request,
    @Body() body: BodyScanResultCreate,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      if (!body?.bodyScore && body?.bodyScore !== 0) {
        res.status(400);
        return { success: false, error: 'bodyScore is required' };
      }

      const result = await assessmentService.create({
        ...body,
        userId: authResult.userId,
      });

      res.status(201);
      return { success: true, data: result };
    } catch (error) {
      console.error('Error creating assessment:', error);
      res.status(500);
      return { success: false, error: 'Failed to create assessment' };
    }
  }

  /**
   * GET /assessment/latest - Get the latest assessment for the authenticated user
   */
  @Get('latest')
  async getLatest(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const result = await assessmentService.getLatest(authResult.userId);
      if (!result) {
        res.status(404);
        return { success: false, error: 'No assessment found' };
      }

      return { success: true, data: result };
    } catch (error) {
      console.error('Error fetching latest assessment:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch latest assessment' };
    }
  }

  /**
   * GET /assessment/history - Get all assessments for the authenticated user
   */
  @Get('history')
  async getHistory(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const results = await assessmentService.getHistory(authResult.userId);
      return { success: true, data: results };
    } catch (error) {
      console.error('Error fetching assessment history:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch assessment history' };
    }
  }

  /**
   * GET /assessment/progress - Compare latest vs previous assessment
   */
  @Get('progress')
  async getProgress(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const progress = await assessmentService.getProgress(authResult.userId);
      if (!progress) {
        res.status(404);
        return { success: false, error: 'No assessments found' };
      }

      return { success: true, data: progress };
    } catch (error) {
      console.error('Error fetching assessment progress:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch assessment progress' };
    }
  }

  /**
   * GET /assessment/:id - Get a specific assessment by ID
   */
  @Get(':id')
  async getById(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const result = await assessmentService.getById(id);
      if (!result) {
        res.status(404);
        return { success: false, error: 'Assessment not found' };
      }

      // Ensure user can only access their own assessments
      if (result.userId !== authResult.userId) {
        res.status(403);
        return { success: false, error: 'Forbidden' };
      }

      return { success: true, data: result };
    } catch (error) {
      console.error('Error fetching assessment:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch assessment' };
    }
  }

  /**
   * DELETE /assessment/:id - Delete an assessment
   */
  @Delete(':id')
  async delete(
    @Req() req: Request,
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      // Verify ownership before deleting
      const existing = await assessmentService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Assessment not found' };
      }

      if (existing.userId !== authResult.userId) {
        res.status(403);
        return { success: false, error: 'Forbidden' };
      }

      await assessmentService.delete(id);
      return { success: true, message: 'Assessment deleted successfully' };
    } catch (error) {
      console.error('Error deleting assessment:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete assessment' };
    }
  }
}
