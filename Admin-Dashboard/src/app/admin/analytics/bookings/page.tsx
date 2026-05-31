'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, DonutChart, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService, type BookingAnalytics } from '@/modules/analytics/analytics.service';
import { formatCurrency, formatNumber, formatPercent } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';
import { analyticsTerms } from '@/modules/analytics/terms';

type DoctorRow = BookingAnalytics['doctors'][number];

export default function BookingsAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<BookingAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.bookings(params()));
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<DoctorRow>[] = [
    { key: 'name', header: 'Doctor', cell: (row) => row.name },
    { key: 'bookings', header: 'Bookings', cell: (row) => formatNumber(row.value) },
    { key: 'reports', header: 'Reports', cell: (row) => formatNumber(row.reports) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Bookings & Clinic" description="Booking volume, payment status, doctor workload, and session report completion." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Bookings" value={formatNumber(data?.summary.totalBookings)} help="All bookings created during the selected period." />
        <StatCard title="Paid Revenue" value={formatCurrency(data?.summary.paidRevenue)} help="Revenue from paid or authorized booking payments." />
        <StatCard title="Reports Completed" value={formatNumber(data?.summary.reportsCompleted)} help="Booking reports completed by doctors/admins." />
        <StatCard title="Cancellation Rate" value={formatPercent(data?.summary.cancellationRate)} help="Cancelled bookings divided by total bookings." />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Bookings Trend" loading={loading} empty={!data?.trend?.length} help="Daily or weekly booking volume in the selected period.">
          <LineTrend data={data?.trend ?? []} />
        </ChartCard>
        <ChartCard title="Booking Statuses" loading={loading} empty={!data?.statuses?.length}>
          <DonutChart data={data?.statuses ?? []} />
        </ChartCard>
        <ChartCard title="Payment Statuses" loading={loading} empty={!data?.paymentStatuses?.length} help={analyticsTerms.checkoutFunnel}>
          <BarsChart data={data?.paymentStatuses ?? []} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={data?.doctors ?? []}
        getRowKey={(row) => row.name}
        loading={loading}
        emptyTitle="No doctor activity"
        emptyDescription="No bookings matched the selected period."
      />
    </div>
  );
}
