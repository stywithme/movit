import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { verifyAdminTokenEdge, signAdminTokenEdge, ADMIN_COOKIE_NAME } from '@/lib/auth/admin-edge';

const publicAdminPages = ['/admin/login', '/admin/reset-password'];

/**
 * Routes that require a specific CASL subject to be readable.
 * Key = route prefix | Value = required subject from JWT-readable permissions.
 * Note: The JWT only carries roleId & isSuperAdmin; real permission check is in the backend.
 * For the frontend we guard by checking isSuperAdmin OR whether roleId exists (lazy guard).
 * Fine-grained guard is done by CASL in the backend + Sidebar filtering.
 */
const PROTECTED_ROUTE_PREFIXES: Record<string, string> = {
  '/admin/roles': 'Role',
  '/admin/admins': 'Admin',
  '/admin/users': 'User',
  '/admin/exercises': 'Exercise',
  '/admin/workouts': 'Workout',
  '/admin/programs/map': 'ProgramMap',
  '/admin/programs': 'Program',
  '/admin/attributes': 'Attribute',
  '/admin/messages': 'FeedbackMessage',
  '/admin/camera-positions': 'PosePosition',
  '/admin/levels': 'Level',
  '/admin/assessment-templates': 'AssessmentTemplate',
  '/admin/progression-rules': 'ProgressionRule',
  '/admin/analytics/programs': 'ProgramAnalytics',
  '/admin/analytics/levels': 'LevelAnalytics',
  '/admin/analytics/assessments': 'AssessmentAnalytics',
  '/admin/analytics': 'Analytics',
  '/admin/uploads': 'Upload',
};

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Always pass through API routes
  if (pathname.startsWith('/api/')) return NextResponse.next();

  const isPublicPage = publicAdminPages.some((path) => pathname.startsWith(path));
  if (isPublicPage) return NextResponse.next();

  const token = request.cookies.get(ADMIN_COOKIE_NAME)?.value;

  if (token) {
    const payload = await verifyAdminTokenEdge(token);

    if (payload) {
      // ── Super admin bypasses all frontend route guards ──
      if (!payload.isSuperAdmin) {
        // Check if this route requires a subject they might not have access to
        const matchedPrefix = Object.keys(PROTECTED_ROUTE_PREFIXES).find(
          (prefix) => pathname.startsWith(prefix)
        );

        // If no roleId → redirect to /admin (dashboard only)
        if (matchedPrefix && !payload.roleId) {
          const dashboardUrl = request.nextUrl.clone();
          dashboardUrl.pathname = '/admin';
          return NextResponse.redirect(dashboardUrl);
        }
      }

      const response = NextResponse.next();

      // Sliding Expiration: Refresh token if less than 3 days remaining
      const now = Math.floor(Date.now() / 1000);
      const threeDaysSeconds = 3 * 24 * 60 * 60;

      if (payload.exp && (payload.exp - now) < threeDaysSeconds) {
        const newToken = await signAdminTokenEdge({
          adminId: payload.adminId,
          email: payload.email,
          roleId: payload.roleId,
          isSuperAdmin: payload.isSuperAdmin,
        });

        response.cookies.set(ADMIN_COOKIE_NAME, newToken, {
          httpOnly: true,
          sameSite: 'lax',
          secure: process.env.NODE_ENV === 'production',
          path: '/',
          maxAge: 7 * 24 * 60 * 60,
        });
      }

      return response;
    }

    // Token invalid → redirect to login
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = '/admin/login';
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  // No token → redirect to login
  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = '/admin/login';
  loginUrl.searchParams.set('redirect', pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ['/admin/:path*'],
};
