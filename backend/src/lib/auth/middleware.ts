/**
 * Auth Middleware
 * ================
 * 
 * Helper functions for authentication in API routes.
 */

import type { Request } from 'express';
import { authService } from '@/modules/auth/auth.service';

export interface AuthenticatedRequest extends Request {
  userId?: string;
}

/**
 * Extract user ID from Authorization header
 */
export function getUserIdFromRequest(request: Request): string | null {
  const authHeader = request.headers.authorization;
  
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return null;
  }

  const token = authHeader.substring(7); // Remove 'Bearer ' prefix
  return authService.verifyToken(token);
}

/**
 * Get device info from request headers
 */
export function getDeviceInfo(request: Request): string | undefined {
  const userAgent = request.headers['user-agent'];
  const deviceName = request.headers['x-device-name'];
  
  if (typeof deviceName === 'string') return deviceName;
  if (typeof userAgent === 'string') return userAgent.substring(0, 100);
  return undefined;
}
