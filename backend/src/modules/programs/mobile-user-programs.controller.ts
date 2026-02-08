import { Body, Controller, Get, Param, Put, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import { programService } from './programs.service';

@Controller('mobile/user-programs')
export class MobileUserProgramsController {
  @Put(':id')
  async updateUserProgram(
    @Req() req: Request,
    @Param('id') id: string,
    @Body() body: { name?: { ar?: string; en?: string }; customizations?: Record<string, unknown>; isActive?: boolean },
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const result = await programService.updateUserProgram(id, authResult.userId, body as any);
      if (result.count === 0) {
        res.status(404);
        return { success: false, error: 'User program not found' };
      }
      return { success: true, data: { updated: result.count } };
    } catch (error) {
      console.error('Error updating user program:', error);
      res.status(500);
      return { success: false, error: 'Failed to update user program' };
    }
  }

  @Get(':id/today')
  async getTodayPlan(
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

      const plan = await programService.getTodayPlan(authResult.userId);
      if (!plan || plan.userProgramId !== id) {
        res.status(404);
        return { success: false, error: 'Plan not found' };
      }
      return { success: true, data: plan };
    } catch (error) {
      console.error('Error fetching today plan:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch today plan' };
    }
  }
}
