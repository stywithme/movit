import { Body, Controller, Get, Param, Post, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { activePlanService } from '@/modules/active-plan/active-plan.service';
import { buildAssignmentReason } from './program-assignment';
import { programService } from './programs.service';

@Controller('mobile/programs')
export class MobileProgramsController {
  @Get()
  async list(@Res({ passthrough: true }) res: Response) {
    try {
      const programs = await programService.getPublishedForMobile();
      return {
        success: true,
        data: programs,
        meta: {
          count: programs.length,
          timestamp: new Date().toISOString(),
        },
      };
    } catch (error) {
      console.error('Error fetching programs for mobile:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch programs' };
    }
  }

  @Get(':id/preview')
  async getPreview(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const program = await programService.getById(id);
      if (!program || !program.isPublished) {
        res.status(404);
        return { success: false, error: 'Program not found' };
      }
      const preview = programService.buildProgramPreview(program);
      if (!preview) {
        res.status(404);
        return { success: false, error: 'Program not found' };
      }
      return { success: true, data: preview };
    } catch (error) {
      console.error('Error fetching program preview for mobile:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch program preview' };
    }
  }

  @Get(':id')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const program = await programService.getById(id);
      if (!program || !program.isPublished) {
        res.status(404);
        return { success: false, error: 'Program not found' };
      }
      return { success: true, data: programService.buildProgramExport(program) };
    } catch (error) {
      console.error('Error fetching program for mobile:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch program' };
    }
  }

  @Post(':id/enroll')
  async enroll(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: { name?: { ar?: string; en?: string } },
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const program = await programService.getById(id);
      if (!program || !program.isPublished) {
        res.status(404);
        return { success: false, error: 'Program not found' };
      }

      const plan = await activePlanService.enrollProgram(authResult.userId, id, {
        name: body?.name as Record<string, string> | undefined,
        assignmentReason: buildAssignmentReason('manual_selection', ['user_choice'], null),
      });
      return { success: true, data: plan };
    } catch (error) {
      console.error('Error enrolling in program:', error);
      res.status(500);
      return { success: false, error: 'Failed to enroll in program' };
    }
  }

}
