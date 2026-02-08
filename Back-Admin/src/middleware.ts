import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { verifyAdminTokenEdge, ADMIN_COOKIE_NAME } from '@/lib/auth/admin-edge';

const publicAdminPages = ['/admin/login', '/admin/reset-password'];
export function middleware(request: NextRequest) {
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
    return verifyAdminTokenEdge(token).then((payload) => {
      if (payload) return NextResponse.next();

      const loginUrl = request.nextUrl.clone();
      loginUrl.pathname = '/admin/login';
      loginUrl.searchParams.set('redirect', pathname);
      return NextResponse.redirect(loginUrl);
    });
  }

  const loginUrl = request.nextUrl.clone();
  loginUrl.pathname = '/admin/login';
  loginUrl.searchParams.set('redirect', pathname);
  return NextResponse.redirect(loginUrl);
}

export const config = {
  matcher: ['/admin/:path*'],
};
