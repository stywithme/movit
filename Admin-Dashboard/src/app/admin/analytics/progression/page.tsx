'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '@/components/common';
import { BarsChart, ChartCard, DonutChart, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type ProgressionAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

export default function ProgressionAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<ProgressionAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.progression(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <div className="space-y-6">
      <PageHeader title="Progression Engine" description="Rule activity, decision mix, and progression streak health." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Changes Applied" value={formatNumber(data?.changes)} help={analyticsTerms.progression} />
        <StatCard title="Active Rules" value={formatNumber(data?.activeRules)} help={analyticsTerms.activeRules} />
        <StatCard title="Avg Success Streak" value={(data?.streaks.avgSuccess ?? 0).toFixed(1)} help="Average consecutive successful workouts toward progression." />
        <StatCard title="Avg Regression Streak" value={(data?.streaks.avgRegression ?? 0).toFixed(1)} help="Average consecutive workouts that may trigger a regression or deload." />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Progression Changes" loading={loading} empty={!data?.trend?.length} help={analyticsTerms.progression}>
          <LineTrend data={data?.trend ?? []} />
        </ChartCard>
        <ChartCard title="Decision Types" loading={loading} empty={!data?.decisions?.length} help="Shows the mix of progression decisions such as promotion, regression, or maintenance.">
          <DonutChart data={data?.decisions ?? []} />
        </ChartCard>
        <ChartCard title="Changed Fields" loading={loading} empty={!data?.fields?.length}>
          <BarsChart data={data?.fields ?? []} />
        </ChartCard>
        <ChartCard title="Top Rules" loading={loading} empty={!data?.topRules?.length}>
          <BarsChart data={data?.topRules ?? []} />
        </ChartCard>
      </div>
    </div>
  );
}
