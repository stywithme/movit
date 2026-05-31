'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { AreaTrend, BarsChart, ChartCard, DonutChart, FunnelChart, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { Badge } from '@/components/ui';
import { analyticsService, type RevenueAnalytics } from '@/modules/analytics/analytics.service';
import { formatCurrency, formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

type PlanRow = RevenueAnalytics['plans'][number];

export default function RevenueAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<RevenueAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.revenue(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<PlanRow>[] = [
    { key: 'name', header: 'Plan', cell: (row) => row.name },
    { key: 'status', header: 'Status', cell: (row) => <Badge variant={row.isActive ? 'success' : 'secondary'}>{row.isActive ? 'Active' : 'Inactive'}</Badge> },
    { key: 'monthly', header: 'Monthly', cell: (row) => formatCurrency(row.monthlyPrice, row.currency) },
    { key: 'yearly', header: 'Yearly', cell: (row) => formatCurrency(row.yearlyPrice, row.currency) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Subscriptions & Revenue" description="Revenue trend, subscription mix, checkout conversion, and plan economics." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Total Revenue" value={formatCurrency(data?.summary.totalRevenue)} />
        <StatCard title="Subscription Revenue" value={formatCurrency(data?.summary.subscriptionRevenue)} />
        <StatCard title="Booking Revenue" value={formatCurrency(data?.summary.bookingRevenue)} />
        <StatCard title="ARPU" value={formatCurrency(data?.summary.arpu)} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Revenue Trend" loading={loading} empty={!data?.revenueTrend?.length}>
          <AreaTrend data={data?.revenueTrend ?? []} />
        </ChartCard>
        <ChartCard title="Checkout Funnel" loading={loading} empty={!data?.checkoutFunnel?.length}>
          <FunnelChart data={data?.checkoutFunnel ?? []} />
        </ChartCard>
        <ChartCard title="Subscriptions by Status" loading={loading} empty={!data?.subscriptionsByStatus?.length}>
          <DonutChart data={data?.subscriptionsByStatus ?? []} />
        </ChartCard>
        <ChartCard title="Subscriptions by Plan" loading={loading} empty={!data?.subscriptionsByPlan?.length}>
          <BarsChart data={data?.subscriptionsByPlan ?? []} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={data?.plans ?? []}
        getRowKey={(row) => row.id}
        loading={loading}
        emptyTitle="No plans"
        emptyDescription="No subscription plans are configured yet."
      />
    </div>
  );
}
