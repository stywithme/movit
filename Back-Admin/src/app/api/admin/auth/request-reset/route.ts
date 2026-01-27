import { NextRequest, NextResponse } from 'next/server';
import { adminAuthService } from '@/modules/admin-auth/admin-auth.service';
import { adminRequestResetSchema } from '@/modules/admin-auth/admin-auth.types';

/**
 * POST /api/admin/auth/request-reset
 * Request admin password reset
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    const parseResult = adminRequestResetSchema.safeParse(body);
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

    await adminAuthService.requestReset(parseResult.data.email);

    return NextResponse.json({
      success: true,
      message: 'If the email exists, a reset link will be sent.',
    });
  } catch (error) {
    console.error('Admin reset request error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to request reset' },
      { status: 500 }
    );
  }
}
