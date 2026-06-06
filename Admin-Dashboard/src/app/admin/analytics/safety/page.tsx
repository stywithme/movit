'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type SafetyAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber, formatPercent } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

type RiskyExercise = SafetyAnalytics['riskyExercises'][number];

export default function SafetyAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<SafetyAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.safety(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<RiskyExercise>[] = [
    { key: 'name', header: 'Exercise', cell: (row) => row.name },
    { key: 'danger', header: 'Danger Reps', cell: (row) => formatNumber(row.danger) },
    { key: 'total', header: 'Total Reps', cell: (row) => formatNumber(row.total) },
    { key: 'rate', header: 'Danger Rate', cell: (row) => formatPercent(row.dangerRate) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Safety & Risk" description="Danger reps, abandoned planned workouts, injuries, and high-risk exercises." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-5">
        <StatCard title="Danger Rep Rate" value={formatPercent(data?.summary.dangerRepRate)} help={analyticsTerms.dangerRepRate} />
        <StatCard title="High-Risk Executions" value={formatNumber(data?.summary.highRiskExecutions)} help={analyticsTerms.highRiskExecutions} />
        <StatCard title="Avg Safety Score" value={(data?.summary.avgSafetyScore ?? 0).toFixed(1)} help={analyticsTerms.safetyScore} />
        <StatCard title="Abandoned Planned Workouts" value={formatNumber(data?.summary.abandonedPlannedWorkouts)} help="Planned workouts marked as abandoned in the selected period." />
        <StatCard title="Known Injuries" value={formatNumber(data?.summary.usersWithKnownInjuries)} help={analyticsTerms.knownInjuries} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Danger Reps Trend" loading={loading} empty={!data?.trends.dangerReps?.length} help={analyticsTerms.dangerRepRate}>
          <LineTrend data={data?.trends.dangerReps ?? []} />
        </ChartCard>
        <ChartCard title="Safety Score Trend" loading={loading} empty={!data?.trends.safetyScore?.length} help={analyticsTerms.safetyScore}>
          <LineTrend data={data?.trends.safetyScore ?? []} />
        </ChartCard>
        <ChartCard title="Known Injury Buckets" loading={loading} empty={!data?.injuries?.length} help={analyticsTerms.knownInjuries}>
          <BarsChart data={data?.injuries ?? []} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={data?.riskyExercises ?? []}
        getRowKey={(row) => row.name}
        loading={loading}
        emptyTitle="No risk signals"
        emptyDescription="No rep-level risk signals were found for the selected period."
      />
    </div>
  );
}
