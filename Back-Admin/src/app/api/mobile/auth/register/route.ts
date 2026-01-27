import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { registerSchema } from '@/modules/auth/auth.types';
import { getDeviceInfo } from '@/lib/auth/middleware';

/**
 * POST /api/mobile/auth/register
 * Register a new user with email/password
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const parseResult = registerSchema.safeParse(body);
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
    const result = await authService.register(parseResult.data, deviceInfo);

    return NextResponse.json({
      success: true,
      data: result,
    }, { status: 201 });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Registration failed';
    
    // Handle specific errors
    if (message === 'Email already registered') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 409 }
      );
    }

    console.error('Registration error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
