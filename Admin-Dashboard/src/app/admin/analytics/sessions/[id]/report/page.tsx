'use client';

import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, StatCard } from '@/components/charts';
import { analyticsService } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';

interface RepRow {
  id: string;
  repNumber: number;
  worstState: number;
  score: number;
  rom: number;
  stability: number;
  formScore: number;
  alignmentAccuracy: number;
}

export default function SessionDetailReportPage() {
  const params = useParams<{ id: string }>();
  const [data, setData] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    if (!params.id) return;
    setLoading(true);
    try {
      setData(await analyticsService.sessionReport(params.id));
    } finally {
      setLoading(false);
    }
  }, [params.id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const columns: DataTableColumn<RepRow>[] = [
    { key: 'rep', header: 'Rep', cell: (row) => row.repNumber },
    { key: 'state', header: 'Worst State', cell: (row) => row.worstState },
    { key: 'score', header: 'Score', cell: (row) => row.score },
    { key: 'rom', header: 'ROM', cell: (row) => row.rom },
    { key: 'stability', header: 'Stability', cell: (row) => row.stability },
    { key: 'form', header: 'Form Score', cell: (row) => row.formScore },
    { key: 'alignment', header: 'Alignment', cell: (row) => row.alignmentAccuracy },
  ];

  const repScores = (data?.reps ?? []).map((rep: RepRow) => ({ name: `#${rep.repNumber}`, value: rep.formScore }));

  return (
    <div className="space-y-6">
      <PageHeader title="Session Report" description={`${data?.exercise?.name ?? ''} — ${data?.user?.name ?? ''}`} />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <StatCard title="Total Reps" value={formatNumber(data?.totals?.totalReps)} />
        <StatCard title="Counted Reps" value={formatNumber(data?.totals?.countedReps)} />
        <StatCard title="Invalid Reps" value={formatNumber(data?.totals?.invalidReps)} />
        <StatCard title="Avg Form Score" value={(data?.metrics?.avgFormScore ?? 0).toFixed(1)} />
      </div>

      <ChartCard title="Rep Form Scores" loading={loading} empty={!repScores.length}>
        <BarsChart data={repScores} />
      </ChartCard>

      <DataTable
        columns={columns}
        data={data?.reps ?? []}
        getRowKey={(row) => row.id}
        loading={loading}
        emptyTitle="No rep metrics"
        emptyDescription="This session has no rep-level metrics."
      />
    </div>
  );
}
