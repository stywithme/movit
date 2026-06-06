'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, StatCard } from '@/components/charts';
import { analyticsService } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';

interface WorkoutExecutionRow {
  id: string;
  exercise: string;
  timestamp: string;
  totalReps: number;
  invalidReps: number;
  avgFormScore: number | null;
}

export default function UserDetailReportPage() {
  const params = useParams<{ id: string }>();
  const [data, setData] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    if (!params.id) return;
    setLoading(true);
    try {
      setData(await analyticsService.userReport(params.id));
    } finally {
      setLoading(false);
    }
  }, [params.id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<WorkoutExecutionRow>[] = [
    { key: 'exercise', header: 'Exercise', cell: (row) => row.exercise },
    { key: 'date', header: 'Date', cell: (row) => new Date(row.timestamp).toLocaleString() },
    { key: 'reps', header: 'Reps', cell: (row) => formatNumber(row.totalReps) },
    { key: 'invalid', header: 'Invalid', cell: (row) => formatNumber(row.invalidReps) },
    { key: 'score', header: 'Form Score', cell: (row) => row.avgFormScore?.toFixed(1) ?? '-' },
  ];

  const levelTrend = (data?.levels ?? []).map((level: any) => ({
    name: `L${level.overallLevel}`,
    value: level.bodyScore,
  }));

  return (
    <div className="space-y-6">
      <PageHeader title={data?.profile?.name ?? 'User Report'} description={data?.profile?.email ?? 'User-level drill-down'} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Total Executions" value={formatNumber(data?.profile?.totalWorkoutExecutions)} />
        <StatCard title="Total Minutes" value={formatNumber(data?.profile?.totalMinutes)} />
        <StatCard title="Assessments" value={formatNumber(data?.assessments?.length)} />
        <StatCard title="Recent Executions" value={formatNumber(data?.workoutExecutions?.length)} />
      </div>

      <ChartCard title="Body Score Snapshots" loading={loading} empty={!levelTrend.length}>
        <BarsChart data={levelTrend} />
      </ChartCard>

      <DataTable
        columns={columns}
        data={data?.workoutExecutions ?? []}
        getRowKey={(row) => row.id}
        loading={loading}
        emptyTitle="No workout executions"
        emptyDescription="This user does not have recent workout executions."
      />
    </div>
  );
}
