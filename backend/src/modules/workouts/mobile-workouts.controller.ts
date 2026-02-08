import { Controller, Get, Res } from '@nestjs/common';
import type { Response } from 'express';
import { workoutService } from './workouts.service';

@Controller('mobile/workouts')
export class MobileWorkoutsController {
  @Get()
  async list(@Res({ passthrough: true }) res: Response) {
    try {
      const workouts = await workoutService.getPublishedForMobile();
      return {
        success: true,
        data: workouts,
        meta: {
          count: workouts.length,
          timestamp: new Date().toISOString(),
        },
      };
    } catch (error) {
      console.error('Error fetching workouts for mobile:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch workouts' };
    }
  }
}
