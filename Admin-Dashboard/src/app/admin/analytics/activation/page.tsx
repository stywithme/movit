'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader } from '@/components/common';
import { BarsChart, ChartCard, FunnelChart, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type ActivationAnalytics } from '@/modules/analytics/analytics.service';
import { formatNumber, formatPercent } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

export default function ActivationAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<ActivationAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.activation(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <div className="space-y-6">
      <PageHeader title="Activation Funnel" description="Where users drop off before the first meaningful value." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <StatCard title="North Star Rate" value={formatPercent(data?.northStar.rate)} help={analyticsTerms.northStar} />
        <StatCard title="North Star Users" value={formatNumber(data?.northStar.completed)} help={analyticsTerms.northStar} />
        <StatCard title="Avg Time to First Workout" value={`${(data?.timeToFirstWorkout.avgHours ?? 0).toFixed(1)}h`} help="Average time between signup and the user's first recorded workout execution." />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Activation Steps" loading={loading} empty={!data?.funnel?.length} help={analyticsTerms.activation}>
          <FunnelChart data={data?.funnel ?? []} />
        </ChartCard>
        <ChartCard title="Time to First Workout" loading={loading} empty={!data?.timeToFirstWorkout.buckets?.length} help="Buckets users by how quickly they reach their first workout execution after signup.">
          <BarsChart data={data?.timeToFirstWorkout.buckets ?? []} />
        </ChartCard>
      </div>
    </div>
  );
}
