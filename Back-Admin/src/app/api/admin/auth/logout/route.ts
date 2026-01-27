import { NextResponse } from 'next/server';
import { clearAdminAuthCookie } from '@/lib/auth/admin';

/**
 * POST /api/admin/auth/logout
 * Logout admin and clear cookie
 */
export async function POST() {
  const response = NextResponse.json({
    success: true,
    message: 'Logged out successfully',
  });

  clearAdminAuthCookie(response);
  return response;
}
