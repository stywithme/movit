'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { LogOut, Moon, Sun, UserCircle } from 'lucide-react';
import { useTheme } from 'next-themes';
import { AuthProvider } from '@/components/auth/AuthProvider';
import { Sidebar } from '@/components/layout/Sidebar';
import { Button } from '@/components/ui/Button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/DropdownMenu';
import { Toaster } from '@/components/ui/Sonner';
import { useAuthStore } from '@/lib/auth/auth-store';
import { isPublicAdminPage } from '@/lib/admin-routes';

function formatSegment(segment: string) {
  if (segment === 'admin') return 'Dashboard';
  return segment
    .replace(/\[[^\]]+\]/g, '')
    .replace(/-/g, ' ')
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function Topbar() {
  const pathname = usePathname();
  const { user } = useAuthStore();
  const { theme, setTheme } = useTheme();
  const segments = pathname.split('/').filter(Boolean);
  const currentTheme = theme === 'dark' ? 'dark' : 'light';

  return (
    <header className="flex h-16 shrink-0 items-center justify-between border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/70 lg:px-6">
      <div className="min-w-0">
        <nav className="flex items-center gap-2 text-sm text-muted-foreground">
          {segments.map((segment, index) => {
            const href = `/${segments.slice(0, index + 1).join('/')}`;
            const isLast = index === segments.length - 1;
            return (
              <span key={href} className="flex items-center gap-2">
                {index > 0 && <span>/</span>}
                {isLast ? (
                  <span className="truncate text-foreground">{formatSegment(segment)}</span>
                ) : (
                  <Link href={href} className="truncate hover:text-foreground">
                    {formatSegment(segment)}
                  </Link>
                )}
              </span>
            );
          })}
        </nav>
      </div>

      <div className="flex items-center gap-2">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => setTheme(currentTheme === 'dark' ? 'light' : 'dark')}
          aria-label="Toggle theme"
        >
          {currentTheme === 'dark' ? <Sun className="size-4" /> : <Moon className="size-4" />}
        </Button>

        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="h-9 gap-2 px-2">
              <div className="flex size-7 items-center justify-center rounded-full bg-accent text-xs font-semibold">
                {user?.name?.slice(0, 2).toUpperCase() || 'AD'}
              </div>
              <span className="hidden max-w-36 truncate text-sm sm:inline">{user?.name || 'Admin'}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-56">
            <DropdownMenuLabel>
              <div className="space-y-1">
                <p className="text-sm font-medium leading-none">{user?.name || 'Admin'}</p>
                <p className="truncate text-xs leading-none text-muted-foreground">{user?.email}</p>
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem asChild>
              <Link href="/admin/profile">
                <UserCircle className="size-4" />
                Profile
              </Link>
            </DropdownMenuItem>
            <DropdownMenuItem asChild>
              <Link href="/admin/logout">
                <LogOut className="size-4" />
                Logout
              </Link>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}

export function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const isAuthPage = isPublicAdminPage(pathname) || pathname === '/admin/unauthorized';

  if (isAuthPage) {
    return (
      <div className="min-h-screen bg-muted/30">
        <Toaster position="top-right" richColors />
        {children}
      </div>
    );
  }

  return (
    <AuthProvider>
      <div className="flex h-screen overflow-hidden bg-muted/30">
        <Toaster position="top-right" richColors />
        <Sidebar />
        <main className="flex min-w-0 flex-1 flex-col overflow-hidden">
          <Topbar />
          <div className="flex-1 overflow-y-auto">
            <div className="mx-auto w-full max-w-7xl px-4 py-6 lg:px-6">{children}</div>
          </div>
        </main>
      </div>
    </AuthProvider>
  );
}
