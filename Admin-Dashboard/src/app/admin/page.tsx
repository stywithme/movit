'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { Activity, ClipboardCheck, DollarSign, ShieldAlert, TrendingUp, Users } from 'lucide-react';
import { PageHeader } from '@/components/common';
import { AreaTrend, ChartCard, DonutChart, FunnelChart, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui';
import { analyticsService, type OverviewAnalytics } from '@/modules/analytics/analytics.service';
import { formatCurrency, formatNumber, formatPercent, metricDelta, metricValue } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

const quickReports = [
  { href: '/admin/analytics/activation', title: 'Activation Funnel', description: 'Signup to first value' },
  { href: '/admin/analytics/retention', title: 'Retention', description: 'Cohorts and engagement' },
  { href: '/admin/analytics/training', title: 'Training Quality', description: 'Sessions, form and safety' },
  { href: '/admin/analytics/revenue', title: 'Revenue', description: 'MRR and conversion' },
];

export default function AdminDashboard() {
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
      <PageHeader title="Command Center" description="Operational view of acquisition, activation, training quality, revenue, and safety." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard title="Total Users" value={formatNumber(metricValue(data?.kpis.totalUsers))} icon={<Users className="size-5" />} />
        <StatCard title="New Users" value={formatNumber(metricValue(data?.kpis.newUsers))} delta={metricDelta(data?.kpis.newUsers)} />
        <StatCard title="Revenue" value={formatCurrency(metricValue(data?.kpis.revenue))} delta={metricDelta(data?.kpis.revenue)} icon={<DollarSign className="size-5" />} />
        <StatCard title="Avg Form Score" value={metricValue(data?.kpis.avgFormScore).toFixed(1)} delta={metricDelta(data?.kpis.avgFormScore)} icon={<Activity className="size-5" />} />
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">North Star</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{formatPercent(data?.northStar.rate)}</p>
            <p className="mt-2 text-sm text-muted-foreground">
              {formatNumber(data?.northStar.completed)} of {formatNumber(data?.northStar.eligibleUsers)} users completed 3 correct sessions in their first 7 days.
            </p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Safety Snapshot</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-3">
              <ShieldAlert className="size-9 text-warning" />
              <div>
                <p className="text-3xl font-bold">{formatPercent(data?.safety.dangerRepRate)}</p>
                <p className="text-sm text-muted-foreground">Danger rep rate</p>
              </div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Action Alerts</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2 text-sm">
            <p>{formatNumber(data?.alerts.pendingReassessments)} pending reassessments</p>
            <p>{formatNumber(data?.alerts.failedCheckouts)} failed checkouts</p>
            <p>{formatNumber(data?.alerts.abandonedReports)} abandoned sessions</p>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Activation Funnel" loading={loading} empty={!data?.activationFunnel?.length}>
          <FunnelChart data={data?.activationFunnel ?? []} />
        </ChartCard>
        <ChartCard title="Sessions Trend" loading={loading} empty={!data?.trends.sessions?.length}>
          <LineTrend data={data?.trends.sessions ?? []} />
        </ChartCard>
        <ChartCard title="Revenue Trend" loading={loading} empty={!data?.trends.revenue?.length}>
          <AreaTrend data={data?.trends.revenue ?? []} />
        </ChartCard>
        <ChartCard title="Level Distribution" loading={loading} empty={!data?.distributions.levels?.length}>
          <DonutChart data={data?.distributions.levels ?? []} />
        </ChartCard>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {quickReports.map((report) => (
          <Link key={report.href} href={report.href}>
            <Card interactive className="h-full">
              <CardContent className="flex items-center gap-3 pt-6">
                <TrendingUp className="size-5 text-primary" />
                <div>
                  <p className="font-semibold">{report.title}</p>
                  <p className="text-sm text-muted-foreground">{report.description}</p>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}

