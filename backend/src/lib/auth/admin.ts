/**
 * Admin Auth Helpers
 * ==================
 * 
 * JWT helpers and cookie utilities for admin sessions.
 */

import type { Request, Response } from 'express';
import jwt from 'jsonwebtoken';

const ADMIN_JWT_SECRET = process.env.ADMIN_JWT_SECRET || 'your-admin-jwt-secret-change-in-production';
const ADMIN_TOKEN_EXPIRY = '7d';
export const ADMIN_COOKIE_NAME = 'admin_token';

export interface AdminJwtPayload {
  adminId: string;
  email: string;
  role: string;
  type: 'admin';
  iat?: number;
  exp?: number;
}

export function signAdminToken(payload: Omit<AdminJwtPayload, 'type'>) {
  return jwt.sign({ ...payload, type: 'admin' }, ADMIN_JWT_SECRET, { expiresIn: ADMIN_TOKEN_EXPIRY });
}

export function verifyAdminToken(token: string): AdminJwtPayload | null {
  try {
    const payload = jwt.verify(token, ADMIN_JWT_SECRET) as AdminJwtPayload;
    if (payload.type !== 'admin') return null;
    return payload;
  } catch {
    return null;
  }
}

export function getAdminIdFromRequest(request: Request): string | null {
  const token = request.cookies?.[ADMIN_COOKIE_NAME];
  if (!token) return null;
  const payload = verifyAdminToken(token);
  return payload?.adminId || null;
}

export function setAdminAuthCookie(response: Response, token: string) {
  response.cookie(ADMIN_COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  });
}

export function clearAdminAuthCookie(response: Response) {
  response.cookie(ADMIN_COOKIE_NAME, '', {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: 0,
  });
}
