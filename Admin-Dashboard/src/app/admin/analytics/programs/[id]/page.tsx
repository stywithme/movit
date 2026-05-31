'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, LineTrend, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { analyticsService } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

interface ProgramUserRow {
  userId: string;
  name: string;
  email: string;
  isActive: boolean;
  startDate: string;
}

function nameOf(program: any) {
  const name = program?.name;
  if (!name) return 'Program Detail';
  return typeof name === 'string' ? name : name.en || name.ar || 'Program Detail';
}

export default function ProgramDetailAnalyticsPage() {
  const params = useParams<{ id: string }>();
  const periodParams = useAnalyticsPeriod((state) => state.params);
  const [data, setData] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    if (!params.id) return;
    setLoading(true);
    try {
      setData(await analyticsService.programDetail(params.id, periodParams()));
    } finally {
      setLoading(false);
    }
  }, [params.id, periodParams]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<ProgramUserRow>[] = [
    { key: 'name', header: 'User', cell: (row) => row.name },
    { key: 'email', header: 'Email', cell: (row) => row.email },
    { key: 'active', header: 'Active', cell: (row) => (row.isActive ? 'Yes' : 'No') },
    { key: 'start', header: 'Start Date', cell: (row) => new Date(row.startDate).toLocaleDateString() },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title={nameOf(data?.program)} description="Program-level drill-down: enrollment, completion, reports, and weekly drop-off." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-5">
        <StatCard title="Enrollments" value={formatNumber(data?.summary.enrollments)} />
        <StatCard title="Active Users" value={formatNumber(data?.summary.activeUsers)} />
        <StatCard title="Completed Sessions" value={formatNumber(data?.summary.completedSessions)} />
        <StatCard title="Reports" value={formatNumber(data?.summary.reports)} />
        <StatCard title="Avg Form Score" value={(data?.summary.avgFormScore ?? 0).toFixed(1)} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Reports Trend" loading={loading} empty={!data?.trend?.length}>
          <LineTrend data={data?.trend ?? []} />
        </ChartCard>
        <ChartCard title="Progress by Week" loading={loading} empty={!data?.dropoffByWeek?.length}>
          <BarsChart data={data?.dropoffByWeek ?? []} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={data?.users ?? []}
        getRowKey={(row) => row.userId}
        loading={loading}
        emptyTitle="No enrolled users"
        emptyDescription="No users are enrolled in this program yet."
      />
    </div>
  );
}
