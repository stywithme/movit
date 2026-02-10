/**
 * Auth Middleware
 * ================
 * 
 * Helper functions for authentication in API routes.
 */

import { NextRequest, NextResponse } from 'next/server';
import { authService } from '@/modules/auth/auth.service';

export interface AuthenticatedRequest extends NextRequest {
  userId?: string;
}

/**
 * Extract user ID from Authorization header
 */
export function getUserIdFromRequest(request: NextRequest): string | null {
  const authHeader = request.headers.get('Authorization');
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }

  const token = authHeader.substring(7); // Remove 'Bearer ' prefix
  return authService.verifyToken(token);
}

/**
 * Create unauthorized response
 */
export function unauthorizedResponse(message = 'Unauthorized') {
  return NextResponse.json(
    { success: false, error: message },
    { status: 401 }
  );
}

/**
 * Get device info from request headers
 */
export function getDeviceInfo(request: NextRequest): string | undefined {
  const userAgent = request.headers.get('User-Agent');
  const deviceName = request.headers.get('X-Device-Name');
  
  if (deviceName) return deviceName;
  if (userAgent) return userAgent.substring(0, 100);
  return undefined;
}
