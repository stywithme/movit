import { NextRequest, NextResponse } from 'next/server';
import { adminAuthService } from '@/modules/admin-auth/admin-auth.service';
import { adminResetPasswordSchema } from '@/modules/admin-auth/admin-auth.types';

/**
 * POST /api/admin/auth/reset-password
 * Reset admin password using token
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    const parseResult = adminResetPasswordSchema.safeParse(body);
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

    await adminAuthService.resetPassword(parseResult.data.token, parseResult.data.password);

    return NextResponse.json({
      success: true,
      message: 'Password reset successfully',
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Failed to reset password';
    if (message === 'Invalid or expired reset token') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 400 }
      );
    }
    console.error('Admin reset password error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to reset password' },
      { status: 500 }
    );
  }
}
