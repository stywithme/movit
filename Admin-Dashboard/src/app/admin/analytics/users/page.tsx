'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '@/components/common';
import { AreaTrend, BarsChart, ChartCard, DonutChart, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type UsersGrowthAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

export default function UsersGrowthAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<UsersGrowthAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.users(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <div className="space-y-6">
      <PageHeader title="Users & Growth" description="User acquisition, profile completion, and audience composition." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <StatCard title="Users in Period" value={formatNumber(data?.total)} />
        <StatCard title="Avg Height" value={`${(data?.demographics.avgHeightCm ?? 0).toFixed(1)} cm`} />
        <StatCard title="Avg Weight" value={`${(data?.demographics.avgWeightKg ?? 0).toFixed(1)} kg`} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="New Users" loading={loading} empty={!data?.trend?.length}>
          <AreaTrend data={data?.trend ?? []} />
        </ChartCard>
        <ChartCard title="Cumulative Growth" loading={loading} empty={!data?.cumulative?.length}>
          <AreaTrend data={data?.cumulative ?? []} />
        </ChartCard>
        <ChartCard title="User Status" loading={loading} empty={!data?.status?.length}>
          <DonutChart data={data?.status ?? []} />
        </ChartCard>
        <ChartCard title="Training Goals" loading={loading} empty={!data?.trainingGoals?.length}>
          <BarsChart data={data?.trainingGoals ?? []} />
        </ChartCard>
        <ChartCard title="Age Buckets" loading={loading} empty={!data?.demographics.ageBuckets?.length}>
          <BarsChart data={data?.demographics.ageBuckets ?? []} />
        </ChartCard>
        <ChartCard title="Available Days / Week" loading={loading} empty={!data?.demographics.availableDays?.length}>
          <BarsChart data={data?.demographics.availableDays ?? []} />
        </ChartCard>
      </div>
    </div>
  );
}
