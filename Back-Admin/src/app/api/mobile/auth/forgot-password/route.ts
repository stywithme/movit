import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { forgotPasswordSchema } from '@/modules/auth/auth.types';

/**
 * POST /api/mobile/auth/forgot-password
 * Request password reset email
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const parseResult = forgotPasswordSchema.safeParse(body);
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

    await authService.forgotPassword(parseResult.data.email);

    // Always return success to prevent email enumeration
    return NextResponse.json({
      success: true,
      message: 'If your email is registered, you will receive a password reset link',
    });
  } catch (error) {
    console.error('Forgot password error:', error);
    // Still return success to prevent email enumeration
    return NextResponse.json({
      success: true,
      message: 'If your email is registered, you will receive a password reset link',
    });
  }
}
