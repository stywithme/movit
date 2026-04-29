import { Controller, Get, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { exerciseService } from '@/modules/exercises/exercises.service';

@Controller('mobile/exercises')
export class MobileExercisesController {
  @Get('substitutions')
  async substitutions(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('slug') slug: string,
    @Query('limit') limit?: string,
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }
      if (!slug?.trim()) {
        res.status(400);
        return { success: false, error: 'slug query param is required' };
      }
      const lim = Math.min(50, Math.max(1, Number.parseInt(limit || '12', 10) || 12));
      const rows = await exerciseService.listSubstitutionCandidates(slug.trim(), lim);
      return { success: true, data: rows };
    } catch (error) {
      console.error('[MobileExercises] substitutions:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch substitutions' };
    }
  }
}
