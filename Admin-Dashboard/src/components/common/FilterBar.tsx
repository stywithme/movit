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
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
        {onSearchChange && (
          <div className="relative min-w-0 flex-1">
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
          <Select
            key={select.id}
            value={select.value}
            onChange={(event) => select.onChange(event.target.value)}
            options={select.options}
            placeholder={select.placeholder}
            className={cn('lg:w-48', select.className)}
          />
        ))}

        {children}

        {onReset && (
          <Button type="button" variant="ghost" onClick={onReset}>
            <X className="size-4" />
            Reset
          </Button>
        )}
      </div>
    </div>
  );
}
