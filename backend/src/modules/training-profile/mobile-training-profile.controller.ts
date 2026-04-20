import { Body, Controller, Get, Put, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { getTrainingProfileForUser, upsertTrainingProfile } from './training-profile.service';
import type { TrainingProfilePayload } from './training-profile.types';

@Controller('mobile/training-profile')
export class MobileTrainingProfileController {
  @Get()
  async get(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const auth = await verifyMobileToken(req);
      if (!auth.success || !auth.userId) {
        res.status(401);
        return { success: false, error: auth.error || 'Unauthorized' };
      }
      const data = await getTrainingProfileForUser(auth.userId);
      return { success: true, data };
    } catch (error) {
      console.error('[TrainingProfile] get error:', error);
      res.status(500);
      return { success: false, error: 'Failed to load training profile' };
    }
  }

  @Put()
  async put(
    @Req() req: Request,
    @Body() body: TrainingProfilePayload,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const auth = await verifyMobileToken(req);
      if (!auth.success || !auth.userId) {
        res.status(401);
        return { success: false, error: auth.error || 'Unauthorized' };
      }
      const data = await upsertTrainingProfile(auth.userId, body ?? {});
      return { success: true, data };
    } catch (error) {
      console.error('[TrainingProfile] put error:', error);
      res.status(500);
      return { success: false, error: 'Failed to save training profile' };
    }
  }
}
