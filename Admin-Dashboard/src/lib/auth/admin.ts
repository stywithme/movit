/**
 * Admin Auth Helpers
 * ==================
 * 
 * JWT helpers and cookie utilities for admin sessions.
 */

import jwt from 'jsonwebtoken';
import type { NextRequest } from 'next/server';
import { NextResponse } from 'next/server';

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

export function getAdminIdFromRequest(request: NextRequest): string | null {
  const token = request.cookies.get(ADMIN_COOKIE_NAME)?.value;
  if (!token) return null;
  const payload = verifyAdminToken(token);
  return payload?.adminId || null;
}

export function setAdminAuthCookie(response: NextResponse, token: string) {
  response.cookies.set(ADMIN_COOKIE_NAME, token, {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: 60 * 60 * 24 * 7,
  });
}

export function clearAdminAuthCookie(response: NextResponse) {
  response.cookies.set(ADMIN_COOKIE_NAME, '', {
    httpOnly: true,
    sameSite: 'lax',
    secure: process.env.NODE_ENV === 'production',
    path: '/',
    maxAge: 0,
  });
}
