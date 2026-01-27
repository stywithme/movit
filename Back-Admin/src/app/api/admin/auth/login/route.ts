import { NextRequest, NextResponse } from 'next/server';
import { adminAuthService } from '@/modules/admin-auth/admin-auth.service';
import { adminLoginSchema } from '@/modules/admin-auth/admin-auth.types';
import { setAdminAuthCookie, signAdminToken } from '@/lib/auth/admin';

/**
 * POST /api/admin/auth/login
 * Login for dashboard admins
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    const parseResult = adminLoginSchema.safeParse(body);
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

    const admin = await adminAuthService.login(parseResult.data);
    const token = signAdminToken({
      adminId: admin.id,
      email: admin.email,
      role: admin.role,
    });

    const response = NextResponse.json({
      success: true,
      data: admin,
    });

    setAdminAuthCookie(response, token);
    return response;
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Login failed';
    if (message === 'Invalid email or password' || message === 'Account is deactivated') {
      return NextResponse.json(
        { success: false, error: message },
        { status: 401 }
      );
    }
    console.error('Admin login error:', error);
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    );
  }
}
