'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, DonutChart, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type TrainingAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber, formatPercent } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

interface ExerciseRow {
  exerciseId: string;
  name: string;
  sessions: number;
  dangerRepRate: number;
}

export default function TrainingAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<TrainingAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.training(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<ExerciseRow>[] = [
    { key: 'name', header: 'Exercise', cell: (row) => row.name },
    { key: 'sessions', header: 'Sessions', cell: (row) => formatNumber(row.sessions) },
    { key: 'danger', header: 'Danger Rep Rate', cell: (row) => formatPercent(row.dangerRepRate) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Training & Performance" description="Session volume, quality trends, and exercise risk signals." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Sessions" value={formatNumber(data?.volume.sessions)} />
        <StatCard title="Total Reps" value={formatNumber(data?.volume.totalReps)} />
        <StatCard title="Invalid Reps" value={formatNumber(data?.volume.invalidReps)} />
        <StatCard title="Total Minutes" value={formatNumber(data?.volume.totalMinutes)} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Sessions Trend" loading={loading} empty={!data?.trends.sessions?.length}>
          <LineTrend data={data?.trends.sessions ?? []} />
        </ChartCard>
        <ChartCard title="Form Score Trend" loading={loading} empty={!data?.trends.formScore?.length}>
          <LineTrend data={data?.trends.formScore ?? []} />
        </ChartCard>
        <ChartCard title="Session Contexts" loading={loading} empty={!data?.contexts?.length}>
          <DonutChart data={data?.contexts ?? []} />
        </ChartCard>
        <ChartCard title="Average Metrics" loading={loading} empty={!data?.averages}>
          <BarsChart
            data={Object.entries(data?.averages ?? {}).map(([name, value]) => ({ name, value }))}
          />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={data?.exercises ?? []}
        getRowKey={(row) => row.exerciseId}
        loading={loading}
        emptyTitle="No exercise usage"
        emptyDescription="No sessions matched the selected period."
      />
    </div>
  );
}
