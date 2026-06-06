'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '@/components/common';
import { BarsChart, ChartCard, CohortHeatmap, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type RetentionAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

export default function RetentionAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<RetentionAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.retention(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const churnData = data
    ? [
        { name: 'Inactive 7d', value: data.churnSignals.inactive7d ?? 0 },
        { name: 'Inactive 14d', value: data.churnSignals.inactive14d ?? 0 },
        { name: 'Inactive 30d', value: data.churnSignals.inactive30d ?? 0 },
      ]
    : [];

  return (
    <div className="space-y-6">
      <PageHeader title="Retention & Engagement" description="Cohorts, active users, stickiness, and churn signals." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Active Users" value={formatNumber(data?.activeUsers)} help={analyticsTerms.activeUsers} />
        <StatCard title="DAU" value={formatNumber(data?.stickiness.dau)} help={analyticsTerms.dau} />
        <StatCard title="WAU" value={formatNumber(data?.stickiness.wau)} help={analyticsTerms.wau} />
        <StatCard title="Executions / Active User" value={(data?.workoutExecutionsPerActiveUser ?? 0).toFixed(2)} help="Average number of workout executions per active user in the selected period." />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Retention Cohorts" className="xl:col-span-2" loading={loading} empty={!data?.cohorts?.length} help={analyticsTerms.cohort}>
          <CohortHeatmap data={data?.cohorts ?? []} />
        </ChartCard>
        <ChartCard title="Churn Signals" loading={loading} empty={!churnData.length} help={analyticsTerms.churnSignals}>
          <BarsChart data={churnData} />
        </ChartCard>
      </div>
    </div>
  );
}
