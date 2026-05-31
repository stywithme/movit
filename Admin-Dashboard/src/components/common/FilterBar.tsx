'use client';

import { Search, X } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Select, type SelectOption } from '@/components/ui/Select';
import { cn } from '@/lib/utils';

export interface FilterSelect {
  id: string;
  value: string;
  placeholder?: string;
  options: SelectOption[];
  onChange: (value: string) => void;
  className?: string;
}

interface FilterBarProps {
  searchValue?: string;
  searchPlaceholder?: string;
  onSearchChange?: (value: string) => void;
  selects?: FilterSelect[];
  onReset?: () => void;
  children?: React.ReactNode;
  className?: string;
}

export function FilterBar({
  searchValue,
  searchPlaceholder = 'Search...',
  onSearchChange,
  selects = [],
  onReset,
  children,
  className,
}: FilterBarProps) {
  return (
    <div className={cn('rounded-xl border bg-card p-4 shadow-sm', className)}>
      <div className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:flex xl:items-center">
        {onSearchChange && (
          <div className="relative min-w-0 md:col-span-2 xl:col-span-1 xl:min-w-80 xl:flex-1">
            <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={searchValue || ''}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder={searchPlaceholder}
              className="pl-9"
            />
          </div>
        )}

        {selects.map((select) => (
          <div key={select.id} className={cn('min-w-0 xl:w-48 xl:flex-none', select.className)}>
            <Select
              value={select.value}
              onChange={(event) => select.onChange(event.target.value)}
              options={select.options}
              placeholder={select.placeholder}
            />
          </div>
        ))}

        {children}

        {onReset && (
          <div className="md:col-span-2 xl:col-span-1 xl:flex-none">
            <Button type="button" variant="ghost" onClick={onReset} className="w-full justify-center xl:w-auto">
              <X className="size-4" />
              Reset
            </Button>
          </div>
        )}
      </div>
    </div>
  );
}
