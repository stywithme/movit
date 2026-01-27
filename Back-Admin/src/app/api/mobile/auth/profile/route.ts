import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { updateProfileSchema } from '@/modules/auth/auth.types';
import { getUserIdFromRequest, unauthorizedResponse } from '@/lib/auth/middleware';

/**
 * GET /api/mobile/auth/profile
 * Get current user profile
 */
export async function GET(request: NextRequest) {
  try {
    const userId = getUserIdFromRequest(request);
    
    if (!userId) {
      return unauthorizedResponse();
    }

    const user = await authService.getProfile(userId);

    if (!user) {
      return NextResponse.json(
        { success: false, error: 'User not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      data: user,
    });
  } catch (error) {
    console.error('Get profile error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to get profile' },
      { status: 500 }
    );
  }
}

/**
 * PATCH /api/mobile/auth/profile
 * Update user profile (name, avatar)
 */
export async function PATCH(request: NextRequest) {
  try {
    const userId = getUserIdFromRequest(request);
    
    if (!userId) {
      return unauthorizedResponse();
    }

    const body = await request.json();

    // Validate input
    const parseResult = updateProfileSchema.safeParse(body);
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

    const user = await authService.updateProfile(userId, parseResult.data);

    return NextResponse.json({
      success: true,
      data: user,
    });
  } catch (error) {
    console.error('Update profile error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update profile' },
      { status: 500 }
    );
  }
}
