import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { resetPasswordSchema } from '@/modules/auth/auth.types';

/**
 * POST /api/mobile/auth/reset-password
 * Reset password using token from email
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const parseResult = resetPasswordSchema.safeParse(body);
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

    await authService.resetPassword(parseResult.data.token, parseResult.data.password);

    return NextResponse.json({
      success: true,
      message: 'Password reset successfully',
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Password reset failed';
    
    if (message.includes('token')) {
      return NextResponse.json(
        { success: false, error: message },
        { status: 400 }
      );
    }

    console.error('Reset password error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
