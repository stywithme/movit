import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';
import { getUserIdFromRequest, unauthorizedResponse } from '@/lib/auth/middleware';

/**
 * DELETE /api/mobile/auth/account
 * Delete user account (soft delete)
 */
export async function DELETE(request: NextRequest) {
  try {
    const userId = getUserIdFromRequest(request);
    
    if (!userId) {
      return unauthorizedResponse();
    }

    await authService.deleteAccount(userId);

    return NextResponse.json({
      success: true,
      message: 'Account deleted successfully',
    });
  } catch (error) {
    console.error('Delete account error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete account' },
      { status: 500 }
    );
  }
}
