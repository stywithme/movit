import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { verifyAdminTokenEdge, signAdminTokenEdge, ADMIN_COOKIE_NAME } from '@/lib/auth/admin-edge';

const publicAdminPages = ['/admin/login', '/admin/reset-password'];

export async function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const isPublicPage = publicAdminPages.some((path) => pathname.startsWith(path));
  if (pathname.startsWith('/api/')) {
    return NextResponse.next();
  }

  if (isPublicPage) {
    return NextResponse.next();
  }

  const token = request.cookies.get(ADMIN_COOKIE_NAME)?.value;

  if (token) {
    const payload = await verifyAdminTokenEdge(token);

    if (payload) {
      const response = NextResponse.next();

      // Sliding Expiration: Refresh token if less than 3 days remaining
      // (Total lifetime is 7 days)
      const now = Math.floor(Date.now() / 1000);
      const threeDaysSeconds = 3 * 24 * 60 * 60;

      if (payload.exp && (payload.exp - now) < threeDaysSeconds) {
        // Create new token
        const newToken = await signAdminTokenEdge({
          adminId: payload.adminId,
          email: payload.email,
          role: payload.role
        });

        // Update cookie
        response.cookies.set(ADMIN_COOKIE_NAME, newToken, {
          httpOnly: true,
          sameSite: 'lax',
          secure: process.env.NODE_ENV === 'production',
          path: '/',
          maxAge: 7 * 24 * 60 * 60, // 7 days
        });
      }

      return response;
    }

    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = '/admin/login';
    loginUrl.searchParams.set('redirect', pathname);
    return NextResponse.redirect(loginUrl);
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = '/admin/login';
  loginUrl.searchParams.set('redirect', pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ['/admin/:path*'],
};
