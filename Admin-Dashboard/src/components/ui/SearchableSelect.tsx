'use client';

/**
 * SearchableSelect — combobox-style picker using Radix Popover + local filter.
 */

import * as React from 'react';
import * as Popover from '@radix-ui/react-popover';
import { ChevronDown } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from './Input';

export interface SearchableSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SearchableSelectProps {
  options: SearchableSelectOption[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  searchPlaceholder?: string;
  disabled?: boolean;
  className?: string;
  id?: string;
  'aria-label'?: string;
  /** When true, first option is "Server default" with empty value */
  allowEmpty?: boolean;
  emptyLabel?: string;
}

export function SearchableSelect({
  options,
  value,
  onChange,
  placeholder = 'Select…',
  searchPlaceholder = 'Search…',
  disabled = false,
  className,
  id,
  'aria-label': ariaLabel,
  allowEmpty = false,
  emptyLabel = 'Server default',
}: SearchableSelectProps) {
  const [open, setOpen] = React.useState(false);
  const [query, setQuery] = React.useState('');
  const listRef = React.useRef<HTMLDivElement>(null);

  const allOptions = React.useMemo(() => {
    const base: SearchableSelectOption[] = allowEmpty
      ? [{ value: '', label: emptyLabel, disabled: false }, ...options]
      : [...options];
    return base;
  }, [options, allowEmpty, emptyLabel]);

  const filtered = React.useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return allOptions;
    return allOptions.filter((o) => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q));
  }, [allOptions, query]);

  const selectedLabel = React.useMemo(() => {
    const found = allOptions.find((o) => o.value === value);
    return found?.label ?? placeholder;
  }, [allOptions, value, placeholder]);

  const handleSelect = (next: string) => {
    onChange(next);
    setOpen(false);
    setQuery('');
  };

  React.useEffect(() => {
    if (!open) setQuery('');
  }, [open]);

  return (
    <Popover.Root open={open} onOpenChange={setOpen} modal={false}>
      <Popover.Trigger asChild>
        <button
          type="button"
          id={id}
          disabled={disabled}
          aria-label={ariaLabel}
          className={cn(
            'relative flex h-9 w-full items-center justify-between gap-2 rounded-md border border-input bg-transparent px-3 py-1 pr-9 text-left text-sm shadow-xs transition-[color,box-shadow] outline-none',
            'focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50',
            'disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50',
            className,
          )}
        >
          <span className={cn('truncate', value ? 'text-foreground' : 'text-muted-foreground')}>
            {selectedLabel}
          </span>
          <ChevronDown className="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          className={cn(
            'z-[100] w-[var(--radix-popover-trigger-width)] rounded-lg border-2 border-gray-200 bg-white p-2 shadow-xl',
            'data-[state=open]:animate-in data-[state=closed]:animate-out'
          )}
          sideOffset={4}
          align="start"
          onOpenAutoFocus={(e) => e.preventDefault()}
        >
          <Input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={searchPlaceholder}
            className="mb-2 py-2 text-sm"
            autoComplete="off"
          />
          <div
            ref={listRef}
            role="listbox"
            className="max-h-56 overflow-y-auto rounded-md border border-gray-200 bg-white"
          >
            {filtered.length === 0 ? (
              <div className="px-3 py-2 text-sm text-gray-600">No matches</div>
            ) : (
              filtered.map((opt) => (
                <button
                  key={`${opt.value}-${opt.label}`}
                  type="button"
                  role="option"
                  aria-selected={value === opt.value}
                  disabled={opt.disabled}
                  onClick={() => !opt.disabled && handleSelect(opt.value)}
                  className={cn(
                    'w-full bg-white text-left px-3 py-2 text-sm text-gray-900 transition-colors',
                    'hover:bg-blue-50 focus:bg-blue-50 focus:outline-none',
                    value === opt.value && 'bg-blue-100 text-blue-950 font-medium',
                    opt.disabled && 'cursor-not-allowed text-gray-400'
                  )}
                >
                  {opt.label}
                </button>
              ))
            )}
          </div>
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  );
}
