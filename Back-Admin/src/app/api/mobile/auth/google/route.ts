import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { googleAuthSchema } from '@/modules/auth/auth.types';
import { getDeviceInfo } from '@/lib/auth/middleware';

/**
 * POST /api/mobile/auth/google
 * Login/Register with Google OAuth
 * 
 * Note: In production, you should verify the Google ID token
 * using Google's OAuth2 library or Firebase Admin SDK.
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const parseResult = googleAuthSchema.safeParse(body);
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

    // TODO: Verify Google ID token in production
    // For now, we expect the client to send verified user data
    const { idToken } = parseResult.data;
    
    // In production, decode and verify the idToken
    // For development, we'll expect additional fields
    const { googleId, email, name, avatarUrl } = body;

    if (!googleId || !email || !name) {
      return NextResponse.json(
        { success: false, error: 'Missing Google user data' },
        { status: 400 }
      );
    }

    const deviceInfo = getDeviceInfo(request);
    const result = await authService.googleAuth(
      googleId,
      email,
      name,
      avatarUrl,
      deviceInfo
    );

    return NextResponse.json({
      success: true,
      data: result,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Google auth failed';
    
    if (message === 'Account is deactivated') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 401 }
      );
    }

    console.error('Google auth error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
