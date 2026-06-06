'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, DonutChart, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type ContentAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

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
    { key: 'executions', header: 'Executions', cell: (row) => formatNumber(row.executions) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Content & Catalog" description="Content coverage, status, and actual exercise usage." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-5">
        <StatCard title="Exercises" value={formatNumber(data?.exercises.total)} help={analyticsTerms.contentCoverage} />
        <StatCard title="Workout Templates" value={formatNumber(data?.workoutTemplates.total)} help="Workout templates available in the catalog." />
        <StatCard title="Programs" value={formatNumber(data?.programs.total)} help="Training programs configured in the system." />
        <StatCard title="Messages" value={formatNumber(data?.messages.total)} help="Feedback message templates available for coaching cues." />
        <StatCard title="Camera Positions" value={formatNumber(data?.cameraPositions.total)} help="Camera position definitions used by pose variants." />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Exercise Status" loading={loading} empty={!data?.exercises.byStatus?.length} help="Draft versus published exercise content.">
          <DonutChart data={data?.exercises.byStatus ?? []} />
        </ChartCard>
        <ChartCard title="Exercise Categories" loading={loading} empty={!data?.exercises.byCategory?.length}>
          <BarsChart data={data?.exercises.byCategory ?? []} />
        </ChartCard>
        <ChartCard title="Workout Template Difficulty" loading={loading} empty={!data?.workoutTemplates.byDifficulty?.length}>
          <DonutChart data={data?.workoutTemplates.byDifficulty ?? []} />
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
        emptyDescription="No workout executions matched the selected period."
      />
    </div>
  );
}
