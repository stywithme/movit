'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, DonutChart, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type ContentAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

type UsedExercise = ContentAnalytics['mostUsedExercises'][number];

export default function ContentAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<ContentAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.content(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<UsedExercise>[] = [
    { key: 'name', header: 'Exercise', cell: (row) => row.name },
    { key: 'sessions', header: 'Sessions', cell: (row) => formatNumber(row.sessions) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Content & Catalog" description="Content coverage, status, and actual exercise usage." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-5">
        <StatCard title="Exercises" value={formatNumber(data?.exercises.total)} />
        <StatCard title="Workouts" value={formatNumber(data?.workouts.total)} />
        <StatCard title="Programs" value={formatNumber(data?.programs.total)} />
        <StatCard title="Messages" value={formatNumber(data?.messages.total)} />
        <StatCard title="Camera Positions" value={formatNumber(data?.cameraPositions.total)} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Exercise Status" loading={loading} empty={!data?.exercises.byStatus?.length}>
          <DonutChart data={data?.exercises.byStatus ?? []} />
        </ChartCard>
        <ChartCard title="Exercise Categories" loading={loading} empty={!data?.exercises.byCategory?.length}>
          <BarsChart data={data?.exercises.byCategory ?? []} />
        </ChartCard>
        <ChartCard title="Workout Difficulty" loading={loading} empty={!data?.workouts.byDifficulty?.length}>
          <DonutChart data={data?.workouts.byDifficulty ?? []} />
        </ChartCard>
        <ChartCard title="Program Types" loading={loading} empty={!data?.programs.byType?.length}>
          <BarsChart data={data?.programs.byType ?? []} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={data?.mostUsedExercises ?? []}
        getRowKey={(row) => row.exerciseId}
        loading={loading}
        emptyTitle="No exercise usage"
        emptyDescription="No sessions matched the selected period."
      />
    </div>
  );
}
