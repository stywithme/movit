import { NextRequest, NextResponse } from 'next/server';
import { adminAuthService } from '@/modules/admin-auth/admin-auth.service';
import { adminUpdateProfileSchema } from '@/modules/admin-auth/admin-auth.types';
import { getAdminIdFromRequest } from '@/lib/auth/admin';

/**
 * GET /api/admin/auth/profile
 * Get admin profile
 */
export async function GET(request: NextRequest) {
  try {
    const adminId = getAdminIdFromRequest(request);
    if (!adminId) {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }

    const admin = await adminAuthService.getProfile(adminId);
    if (!admin) {
      return NextResponse.json(
        { success: false, error: 'Admin not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      data: admin,
    });
  } catch (error) {
    console.error('Error fetching admin profile:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch profile' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/admin/auth/profile
 * Update admin profile
 */
export async function PUT(request: NextRequest) {
  try {
    const adminId = getAdminIdFromRequest(request);
    if (!adminId) {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }

    const body = await request.json();
    const parseResult = adminUpdateProfileSchema.safeParse(body);
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

    const admin = await adminAuthService.updateProfile(adminId, parseResult.data);

    return NextResponse.json({
      success: true,
      data: admin,
    });
  } catch (error) {
    console.error('Error updating admin profile:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update profile' },
      { status: 500 }
    );
  }
}
