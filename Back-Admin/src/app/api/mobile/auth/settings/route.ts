import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { updateSettingsSchema } from '@/modules/auth/auth.types';
import { getUserIdFromRequest, unauthorizedResponse } from '@/lib/auth/middleware';

/**
 * PATCH /api/mobile/auth/settings
 * Update user settings (language, voice feedback, notifications)
 */
export async function PATCH(request: NextRequest) {
  try {
    const userId = getUserIdFromRequest(request);
    
    if (!userId) {
      return unauthorizedResponse();
    }

    const body = await request.json();

    // Validate input
    const parseResult = updateSettingsSchema.safeParse(body);
    if (!parseResult.success) {
      return NextResponse.json(
        { 
          success: false, 
          error: 'Validation failed',
          details: parseResult.error.flatten().fieldErrors,
        },
        { status: 400 }
      );
    }

    const user = await authService.updateSettings(userId, parseResult.data);

    return NextResponse.json({
      success: true,
      data: user,
    });
  } catch (error) {
    console.error('Update settings error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update settings' },
      { status: 500 }
    );
  }
}
