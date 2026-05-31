'use client';

import { useCallback, useEffect, useState } from 'react';
import { Activity, ClipboardCheck, DollarSign, Users } from 'lucide-react';
import { PageHeader } from '@/components/common';
import { AreaTrend, ChartCard, DonutChart, FunnelChart, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type OverviewAnalytics } from '@/modules/analytics/analytics.service';
import { formatCurrency, formatNumber, formatPercent, metricDelta, metricValue } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

export default function AnalyticsOverviewPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<OverviewAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.overview(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <div className="space-y-6">
      <PageHeader title="Analytics Overview" description="Platform-wide health across acquisition, activation, revenue, and training quality." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Total Users" value={formatNumber(metricValue(data?.kpis.totalUsers))} icon={<Users className="size-5" />} help={analyticsTerms.totalUsers} />
        <StatCard title="New Users" value={formatNumber(metricValue(data?.kpis.newUsers))} delta={metricDelta(data?.kpis.newUsers)} help={analyticsTerms.newUsers} />
        <StatCard title="Assessments" value={formatNumber(metricValue(data?.kpis.assessments))} delta={metricDelta(data?.kpis.assessments)} icon={<ClipboardCheck className="size-5" />} help={analyticsTerms.assessment} />
        <StatCard title="Sessions" value={formatNumber(metricValue(data?.kpis.sessions))} delta={metricDelta(data?.kpis.sessions)} icon={<Activity className="size-5" />} help={analyticsTerms.sessions} />
        <StatCard title="Revenue" value={formatCurrency(metricValue(data?.kpis.revenue))} delta={metricDelta(data?.kpis.revenue)} icon={<DollarSign className="size-5" />} help={analyticsTerms.revenue} />
        <StatCard title="Pro Users" value={formatNumber(metricValue(data?.kpis.proUsers))} help={analyticsTerms.proUsers} />
        <StatCard title="Active Users" value={formatNumber(metricValue(data?.kpis.activeUsers))} help={analyticsTerms.activeUsers} />
        <StatCard title="Avg Form Score" value={metricValue(data?.kpis.avgFormScore).toFixed(1)} delta={metricDelta(data?.kpis.avgFormScore)} help={analyticsTerms.avgFormScore} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Activation Funnel" description={`North Star: ${formatPercent(data?.northStar.rate)} achieved`} loading={loading} empty={!data?.activationFunnel?.length} help={analyticsTerms.activation}>
          <FunnelChart data={data?.activationFunnel ?? []} />
        </ChartCard>
        <ChartCard title="Sessions Trend" loading={loading} empty={!data?.trends.sessions?.length} help={analyticsTerms.sessions}>
          <LineTrend data={data?.trends.sessions ?? []} />
        </ChartCard>
        <ChartCard title="Revenue Trend" loading={loading} empty={!data?.trends.revenue?.length} help={analyticsTerms.revenue}>
          <AreaTrend data={data?.trends.revenue ?? []} />
        </ChartCard>
        <ChartCard title="Level Distribution" loading={loading} empty={!data?.distributions.levels?.length}>
          <DonutChart data={data?.distributions.levels ?? []} />
        </ChartCard>
      </div>
    </div>
  );
}
