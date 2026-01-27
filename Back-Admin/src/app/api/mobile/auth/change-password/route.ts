import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { changePasswordSchema } from '@/modules/auth/auth.types';
import { getUserIdFromRequest, unauthorizedResponse } from '@/lib/auth/middleware';

/**
 * POST /api/mobile/auth/change-password
 * Change user password (requires current password)
 */
export async function POST(request: NextRequest) {
  try {
    const userId = getUserIdFromRequest(request);
    
    if (!userId) {
      return unauthorizedResponse();
    }

    const body = await request.json();

    // Validate input
    const parseResult = changePasswordSchema.safeParse(body);
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

    await authService.changePassword(userId, parseResult.data);

    return NextResponse.json({
      success: true,
      message: 'Password changed successfully. Please login again.',
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Password change failed';
    
    if (message === 'Current password is incorrect') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 400 }
      );
    }

    if (message.includes('OAuth')) {
      return NextResponse.json(
        { success: false, error: message },
        { status: 400 }
      );
    }

    console.error('Change password error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
