'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ChevronDown, ChevronRight, Menu, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { isNavItemActive, mainNav, navGroups, type NavItem } from '@/lib/navigation';

export function Sidebar() {
  const pathname = usePathname();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [openGroups, setOpenGroups] = useState<string[]>(
    navGroups.map((group) => group.title)
  );
  const { user } = useAuthStore();
  const { can } = usePermissions();

  if (!user) return null;

  const toggleGroup = (title: string) => {
    setOpenGroups((prev) =>
      prev.includes(title) ? prev.filter((item) => item !== title) : [...prev, title]
    );
  };

  const canSeeItem = (item: NavItem) => {
    if (!item.requiredSubject) return true;

    const bookingSubjects = ['Booking', 'DoctorWorkTime', 'CloseTime', 'BookingReport'];
    if (user.isDoctor && bookingSubjects.includes(item.requiredSubject)) return true;

    return can('read', item.requiredSubject);
  };

  const NavItemLink = ({ item }: { item: NavItem }) => {
    const isActive = isNavItemActive(pathname, item);

    return (
      <Link
        href={item.href}
        onClick={() => setIsMobileMenuOpen(false)}
        className={cn(
          'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
          isActive
            ? 'bg-accent text-accent-foreground'
            : 'text-muted-foreground hover:bg-accent/70 hover:text-foreground'
        )}
      >
        <item.icon className="size-4" />
        <span className="truncate">{item.title}</span>
      </Link>
    );
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
        className="fixed left-4 top-4 z-50 rounded-md border bg-background p-2 shadow-sm lg:hidden"
        aria-label="Toggle navigation"
      >
        {isMobileMenuOpen ? <X className="size-5" /> : <Menu className="size-5" />}
      </button>

      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-40 flex w-72 flex-col border-r bg-background transition-transform duration-200 lg:static lg:translate-x-0',
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="border-b p-5">
          <Link href="/admin" className="flex items-center gap-3">
            <div className="flex size-9 items-center justify-center rounded-xl bg-primary text-sm font-semibold text-primary-foreground">
              FF
            </div>
            <div>
              <p className="text-sm font-semibold leading-none">Fix Fit</p>
              <p className="mt-1 text-xs text-muted-foreground">Admin Dashboard</p>
            </div>
          </Link>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4">
          <div className="space-y-1">
            {mainNav.filter(canSeeItem).map((item) => (
              <NavItemLink key={item.href} item={item} />
            ))}
          </div>

          <div className="mt-6 space-y-5">
            {navGroups.map((group) => {
              const visibleItems = group.items.filter(canSeeItem);
              if (visibleItems.length === 0) return null;

              const isOpen = openGroups.includes(group.title);
              return (
                <section key={group.title} className="space-y-1">
                  <button
                    type="button"
                    onClick={() => toggleGroup(group.title)}
                    className="flex w-full items-center justify-between rounded-md px-3 py-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground hover:text-foreground"
                  >
                    <span>{group.title}</span>
                    {isOpen ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                  </button>

                  {isOpen && (
                    <div className="space-y-1">
                      {visibleItems.map((item) => (
                        <NavItemLink key={item.href} item={item} />
                      ))}
                    </div>
                  )}
                </section>
              );
            })}
          </div>
        </nav>
      </aside>

      {isMobileMenuOpen && (
        <div
          className="fixed inset-0 z-30 bg-black/50 lg:hidden"
          onClick={() => setIsMobileMenuOpen(false)}
        />
      )}
    </>
  );
}
