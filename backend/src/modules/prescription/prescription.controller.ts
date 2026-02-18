/**
 * Prescription Controller
 * =======================
 *
 *   POST /mobile/prescription/recommend — Get recommended program
 */

import { Controller, Post, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { prescriptionService } from './prescription.service';

@Controller('mobile/prescription')
export class PrescriptionController {
  /**
   * POST /mobile/prescription/recommend
   * Returns a recommended program based on the user's latest assessment.
   */
  @Post('recommend')
  async recommend(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const result = await prescriptionService.recommend(authResult.userId);
      return { success: true, data: result };
    } catch (error) {
      console.error('[Prescription] Error:', error);
      res.status(500);
      return { success: false, error: 'Failed to generate recommendation' };
    }
  }
}
