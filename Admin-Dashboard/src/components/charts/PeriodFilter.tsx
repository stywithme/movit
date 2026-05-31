'use client';

import { Select } from '@/components/ui/Select';
import { Input } from '@/components/ui/Input';
import { Button } from '@/components/ui/Button';
import { useAnalyticsPeriod, type AnalyticsPeriod } from '@/modules/analytics/period-store';

const periodOptions = [
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
  { value: '90d', label: 'Last 90 days' },
  { value: 'all', label: 'All time' },
  { value: 'custom', label: 'Custom range' },
];

export function PeriodFilter({ onRefresh }: { onRefresh?: () => void }) {
  const { period, from, to, setPeriod, setCustomRange } = useAnalyticsPeriod();

  return (
    <div className="flex flex-col gap-3 rounded-xl border bg-card p-3 sm:flex-row sm:items-end">
      <div className="w-full sm:w-48">
        <Select
          aria-label="Analytics period"
          value={period}
          onChange={(event) => setPeriod(event.target.value as AnalyticsPeriod)}
          options={periodOptions}
        />
      </div>

      {period === 'custom' && (
        <>
          <Input type="date" value={from} onChange={(event) => setCustomRange(event.target.value, to)} />
          <Input type="date" value={to} onChange={(event) => setCustomRange(from, event.target.value)} />
        </>
      )}

      {onRefresh && (
        <Button type="button" variant="outline" onClick={onRefresh} className="sm:ml-auto">
          Refresh
        </Button>
      )}
    </div>
  );
}
