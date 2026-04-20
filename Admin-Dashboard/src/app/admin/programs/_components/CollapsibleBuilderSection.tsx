'use client';

import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import { Badge } from '@/components/ui';
import { cn } from '@/lib/utils';

type MetaVariant = 'default' | 'primary' | 'success' | 'warning' | 'error' | 'outline';

interface BuilderMeta {
  label: string;
  variant?: MetaVariant;
}

interface CollapsibleBuilderSectionProps {
  title: string;
  subtitle?: string;
  meta?: BuilderMeta[];
  actions?: React.ReactNode;
  defaultOpen?: boolean;
  className?: string;
  children: React.ReactNode;
}

export function CollapsibleBuilderSection({
  title,
  subtitle,
  meta = [],
  actions,
  defaultOpen = false,
  className,
  children,
}: CollapsibleBuilderSectionProps) {
  const [open, setOpen] = useState(defaultOpen);

  return (
    <div className={cn('rounded-xl border border-gray-200 bg-white', className)}>
      <div className="flex items-start justify-between gap-4 p-4">
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          className="flex-1 text-left"
          aria-expanded={open}
        >
          <div className="flex items-start gap-3">
            <ChevronDown
              className={cn(
                'mt-0.5 h-4 w-4 shrink-0 text-gray-400 transition-transform',
                open && 'rotate-180'
              )}
            />
            <div className="min-w-0">
              <p className="text-sm font-semibold text-gray-900">{title}</p>
              {subtitle ? <p className="mt-1 text-sm text-gray-500">{subtitle}</p> : null}
              {meta.length > 0 ? (
                <div className="mt-3 flex flex-wrap gap-2">
                  {meta.map((item) => (
                    <Badge key={item.label} variant={item.variant ?? 'default'} size="sm">
                      {item.label}
                    </Badge>
                  ))}
                </div>
              ) : null}
            </div>
          </div>
        </button>

        {actions ? <div className="shrink-0">{actions}</div> : null}
      </div>

      {open ? <div className="border-t border-gray-100 p-4">{children}</div> : null}
    </div>
  );
}
