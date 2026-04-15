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
            'w-full flex items-center justify-between gap-2 px-4 py-3 rounded-lg border-2 transition-colors text-left',
            'text-black font-medium',
            'focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500',
            'disabled:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-60',
            'border-gray-300 bg-gray-50/50',
            className
          )}
        >
          <span className="truncate">{selectedLabel}</span>
          <ChevronDown className="h-4 w-4 shrink-0 text-gray-500" />
        </button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          className={cn(
            'z-[100] w-[var(--radix-popover-trigger-width)] rounded-lg border-2 border-gray-200 bg-white p-2 shadow-lg',
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
            className="max-h-56 overflow-y-auto rounded-md border border-gray-100"
          >
            {filtered.length === 0 ? (
              <div className="px-3 py-2 text-sm text-gray-500">No matches</div>
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
                    'w-full text-left px-3 py-2 text-sm transition-colors',
                    'hover:bg-blue-50 focus:bg-blue-50 focus:outline-none',
                    value === opt.value && 'bg-blue-100 font-medium',
                    opt.disabled && 'opacity-50 cursor-not-allowed'
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
