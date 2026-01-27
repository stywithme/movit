import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { refreshTokenSchema } from '@/modules/auth/auth.types';
import { getUserIdFromRequest } from '@/lib/auth/middleware';

/**
 * POST /api/mobile/auth/logout
 * Logout from current device (invalidate refresh token)
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const parseResult = refreshTokenSchema.safeParse(body);
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

    await authService.logout(parseResult.data.refreshToken);

    return NextResponse.json({
      success: true,
      message: 'Logged out successfully',
    });
  } catch (error) {
    console.error('Logout error:', error);
    return NextResponse.json(
      { success: false, error: 'Logout failed' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/mobile/auth/logout
 * Logout from all devices
 */
export async function DELETE(request: NextRequest) {
  try {
    const userId = getUserIdFromRequest(request);
    
    if (!userId) {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }

    await authService.logoutAll(userId);

    return NextResponse.json({
      success: true,
      message: 'Logged out from all devices',
    });
  } catch (error) {
    console.error('Logout all error:', error);
    return NextResponse.json(
      { success: false, error: 'Logout failed' },
      { status: 500 }
    );
  }
}
