/**
 * Assessment Templates Mobile Controller
 * =======================================
 *
 *   GET /mobile/assessment-templates/resolve — Resolve the right template for the user
 *
 * Protected by mobile Bearer token auth.
 */

import { Controller, Get, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { assessmentTemplateService } from './assessment-templates-admin.service';

@Controller('mobile/assessment-templates')
export class AssessmentTemplatesMobileController {
  /**
   * GET /mobile/assessment-templates/resolve — Resolve template for the current user.
   *
   * Returns the best-matching published assessment template based on the user's
   * current level profile. Exercises are sorted: core first, then adaptive.
   */
  @Get('resolve')
  async resolve(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const data = await assessmentTemplateService.resolveForUser(authResult.userId);

      if (!data) {
        res.status(404);
        return {
          success: false,
          error: 'No published assessment template found. Contact your administrator.',
        };
      }

      return { success: true, data };
    } catch (error) {
      console.error('[AssessmentTemplates] Resolve Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to resolve assessment template' };
    }
  }
}
