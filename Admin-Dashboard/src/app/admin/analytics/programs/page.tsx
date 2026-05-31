'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, StatCard } from '@/components/charts';
import { PeriodFilter } from '@/components/charts/PeriodFilter';
import { Badge } from '@/components/ui';
import { analyticsService } from '@/modules/analytics/analytics.service';
import { formatNumber, formatPercent } from '@/modules/analytics/format';
import { useAnalyticsPeriod } from '@/modules/analytics/period-store';

interface ProgramRow {
  id: string;
  name: Record<string, string> | string;
  type?: string;
  totalEnrollments: number;
  activeUsers: number;
  completedUsers: number;
  completionRate: number;
  avgFormScore: number;
  totalReports: number;
}

function nameOf(value: ProgramRow['name']) {
  return typeof value === 'string' ? value : value?.en || value?.ar || 'Program';
}

export default function ProgramsAnalyticsPage() {
  const params = useAnalyticsPeriod((state) => state.params);
  const [programs, setPrograms] = useState<ProgramRow[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await analyticsService.programs(params());
      setPrograms(Array.isArray(data) ? data : []);
    } finally {
      setLoading(false);
    }
  }, [params]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const avgCompletion = programs.length
    ? programs.reduce((sum, program) => sum + program.completionRate, 0) / programs.length
    : 0;

  const columns: DataTableColumn<ProgramRow>[] = [
    {
      key: 'name',
      header: 'Program',
      cell: (row) => (
        <Link className="font-medium text-primary hover:underline" href={`/admin/analytics/programs/${row.id}`}>
          {nameOf(row.name)}
        </Link>
      ),
    },
    { key: 'type', header: 'Type', cell: (row) => row.type || '-' },
    { key: 'enrollments', header: 'Enrollments', cell: (row) => formatNumber(row.totalEnrollments) },
    { key: 'active', header: 'Active', cell: (row) => formatNumber(row.activeUsers) },
    { key: 'completed', header: 'Completed', cell: (row) => formatNumber(row.completedUsers) },
    {
      key: 'completion',
      header: 'Completion',
      cell: (row) => <Badge variant={row.completionRate >= 50 ? 'success' : row.completionRate >= 25 ? 'warning' : 'secondary'}>{formatPercent(row.completionRate)}</Badge>,
    },
    { key: 'score', header: 'Avg Form', cell: (row) => row.avgFormScore?.toFixed(1) ?? '-' },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Program Analytics" description="Program enrollment, completion, and training quality." />
      <PeriodFilter onRefresh={fetchData} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Programs" value={formatNumber(programs.length)} />
        <StatCard title="Enrollments" value={formatNumber(programs.reduce((sum, row) => sum + row.totalEnrollments, 0))} />
        <StatCard title="Active Users" value={formatNumber(programs.reduce((sum, row) => sum + row.activeUsers, 0))} />
        <StatCard title="Avg Completion" value={formatPercent(avgCompletion)} />
      </div>

      <ChartCard title="Completion by Program" loading={loading} empty={!programs.length}>
        <BarsChart data={programs.map((program) => ({ name: nameOf(program.name), value: program.completionRate }))} />
      </ChartCard>

      <DataTable
        columns={columns}
        data={programs}
        getRowKey={(row) => row.id}
        loading={loading}
        emptyTitle="No program analytics"
        emptyDescription="No published programs have reportable data yet."
      />
    </div>
  );
}
