import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { loginSchema } from '@/modules/auth/auth.types';
import { getDeviceInfo } from '@/lib/auth/middleware';

/**
 * POST /api/mobile/auth/login
 * Login with email/password
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const parseResult = loginSchema.safeParse(body);
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

    const deviceInfo = getDeviceInfo(request);
    const result = await authService.login(parseResult.data, deviceInfo);

    return NextResponse.json({
      success: true,
      data: result,
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Login failed';
    
    // Handle specific errors
    if (message === 'Invalid email or password' || message === 'Account is deactivated') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 401 }
      );
    }

    console.error('Login error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
