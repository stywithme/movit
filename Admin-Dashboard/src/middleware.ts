import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { verifyAdminTokenEdge, signAdminTokenEdge, ADMIN_COOKIE_NAME } from '@/lib/auth/admin-edge';
import { isPublicAdminPage, matchProtectedAdminRoute } from '@/lib/admin-routes';

/**
 * Routes that require a specific CASL subject to be readable.
 * Key = route prefix | Value = required subject from JWT-readable permissions.
 * Note: The JWT only carries roleId & isSuperAdmin; real permission check is in the backend.
 * For the frontend we guard by checking isSuperAdmin OR whether roleId exists (lazy guard).
 * Fine-grained guard is done by CASL in the backend + Sidebar filtering.
 */
export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  // Always pass through API routes
  if (pathname.startsWith('/api/')) return NextResponse.next();

  if (isPublicAdminPage(pathname)) return NextResponse.next();

  const token = request.cookies.get(ADMIN_COOKIE_NAME)?.value;

  if (token) {
    const payload = await verifyAdminTokenEdge(token);

    if (payload) {
      // ── Super admin bypasses all frontend route guards ──
      if (!payload.isSuperAdmin) {
        // Check if this route requires a subject they might not have access to
        const matchedRoute = matchProtectedAdminRoute(pathname);

        // If no roleId → redirect to /admin (dashboard only)
        if (matchedRoute && !payload.roleId) {
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
