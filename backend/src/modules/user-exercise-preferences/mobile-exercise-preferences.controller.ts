import { Body, Controller, Delete, Get, Param, Put, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import {
  deleteUserExercisePreference,
  listUserExercisePreferences,
  upsertUserExercisePreference,
  type UserExercisePreferencePatch,
} from './user-exercise-preferences.service';

@Controller('mobile/exercise-preferences')
export class MobileExercisePreferencesController {
  @Get()
  async list(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const data = await listUserExercisePreferences(authResult.userId);
      return { success: true, data };
    } catch (error) {
      console.error('[ExercisePreferences] list error:', error);
      res.status(500);
      return { success: false, error: 'Failed to load preferences' };
    }
  }

  @Put(':exerciseId')
  async upsert(
    @Req() req: Request,
    @Param('exerciseId') exerciseId: string,
    @Body() body: UserExercisePreferencePatch,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const patch: UserExercisePreferencePatch = {
        customReps: body?.customReps,
        customDurationSec: body?.customDurationSec,
        customWeightKg: body?.customWeightKg,
      };

      const result = await upsertUserExercisePreference(authResult.userId, exerciseId, patch);
      if (!result.ok) {
        res.status(404);
        return { success: false, error: 'Exercise not found' };
      }
      return { success: true, data: result.data };
    } catch (error) {
      console.error('[ExercisePreferences] upsert error:', error);
      res.status(500);
      return { success: false, error: 'Failed to save preference' };
    }
  }

  @Delete(':exerciseId')
  async remove(
    @Req() req: Request,
    @Param('exerciseId') exerciseId: string,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const authResult = await verifyMobileToken(req);
      if (!authResult.success || !authResult.userId) {
        res.status(401);
        return { success: false, error: authResult.error || 'Unauthorized' };
      }

      const { deleted } = await deleteUserExercisePreference(authResult.userId, exerciseId);
      return { success: true, data: { deleted } };
    } catch (error) {
      console.error('[ExercisePreferences] delete error:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete preference' };
    }
  }
}
