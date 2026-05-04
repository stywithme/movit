'use client';

import { cn } from '@/lib/utils';
import type { ProgramEditorTabId } from './program-editor-tabs';

type TabDef = { id: ProgramEditorTabId; label: string };

interface ProgramEditorTabBarProps {
  active: ProgramEditorTabId;
  onChange: (id: ProgramEditorTabId) => void;
  tabs: readonly TabDef[];
  /** e.g. builder stats summary */
  right?: React.ReactNode;
  className?: string;
}

export function ProgramEditorTabBar({ active, onChange, tabs, right, className }: ProgramEditorTabBarProps) {
  return (
    <div className={cn('flex flex-wrap items-center justify-between gap-3', className)}>
      <div className="flex flex-wrap gap-1 border-b border-gray-200 pb-px" role="tablist" aria-label="Program editor sections">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={active === t.id}
            onClick={() => onChange(t.id)}
            className={cn(
              'rounded-t-lg px-4 py-2.5 text-sm font-medium transition-colors',
              active === t.id
                ? 'border border-b-0 border-gray-200 bg-white text-blue-700 shadow-sm'
                : 'border border-transparent text-gray-600 hover:bg-gray-50 hover:text-gray-900',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
      {right ? <div className="shrink-0 text-sm text-gray-500">{right}</div> : null}
    </div>
  );
}
