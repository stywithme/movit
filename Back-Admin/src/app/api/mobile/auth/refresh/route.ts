import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { refreshTokenSchema } from '@/modules/auth/auth.types';

/**
 * POST /api/mobile/auth/refresh
 * Refresh access token using refresh token
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

    const tokens = await authService.refreshAccessToken(parseResult.data.refreshToken);

    return NextResponse.json({
      success: true,
      data: tokens,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Token refresh failed';
    
    // Handle token errors
    if (message.includes('token') || message === 'Account is deactivated') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 401 }
      );
    }

    console.error('Token refresh error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
