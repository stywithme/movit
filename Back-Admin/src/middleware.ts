import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { verifyAdminTokenEdge, ADMIN_COOKIE_NAME } from '@/lib/auth/admin-edge';

const publicAdminPages = ['/admin/login', '/admin/reset-password'];
const publicAdminApi = [
  '/api/admin/auth/login',
  '/api/admin/auth/request-reset',
  '/api/admin/auth/reset-password',
];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;

  const isPublicPage = publicAdminPages.some((path) => pathname.startsWith(path));
  const isPublicApi = publicAdminApi.some((path) => pathname.startsWith(path));

  if (isPublicPage || isPublicApi) {
    return NextResponse.next();
  }

  const token = request.cookies.get(ADMIN_COOKIE_NAME)?.value;

  if (token) {
    return verifyAdminTokenEdge(token).then((payload) => {
      if (payload) return NextResponse.next();

      if (pathname.startsWith('/api/')) {
        return NextResponse.json(
          { success: false, error: 'Unauthorized' },
          { status: 401 }
        );
      }

      const loginUrl = request.nextUrl.clone();
      loginUrl.pathname = '/admin/login';
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    });
  }

  if (pathname.startsWith('/api/')) {
    return NextResponse.json(
      { success: false, error: 'Unauthorized' },
      { status: 401 }
    );
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = '/admin/login';
  loginUrl.searchParams.set('redirect', pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ['/admin/:path*', '/api/admins/:path*', '/api/admin/auth/:path*'],
};
