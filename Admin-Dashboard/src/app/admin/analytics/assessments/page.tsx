'use client';

import { useCallback, useEffect, useState } from 'react';
import { PageHeader, DataTable, type DataTableColumn } from '@/components/common';
import { BarsChart, ChartCard, StatCard } from '@/components/charts';
import { analyticsService } from '@/modules/analytics/analytics.service';
import { formatNumber } from '@/modules/analytics/format';

interface TemplateRow {
  templateId: string | null;
  templateName: Record<string, string> | string | null;
  avgBodyScore: number;
  count: number;
}

function templateName(value: TemplateRow['templateName']) {
  if (!value) return 'No template';
  return typeof value === 'string' ? value : value.en || value.ar || 'Template';
}

export default function AssessmentsAnalyticsPage() {
  const [data, setData] = useState<any | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setData(await analyticsService.assessments());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const templates: TemplateRow[] = data?.avgBodyScorePerTemplate ?? [];
  const domains = data?.domainAverages
    ? Object.entries(data.domainAverages)
        .filter(([, value]) => value != null)
        .map(([name, value]) => ({ name, value: Number(value) }))
    : [];

  const columns: DataTableColumn<TemplateRow>[] = [
    { key: 'template', header: 'Template', cell: (row) => templateName(row.templateName) },
    { key: 'count', header: 'Assessments', cell: (row) => formatNumber(row.count) },
    { key: 'score', header: 'Avg Body Score', cell: (row) => row.avgBodyScore.toFixed(1) },
  ];

  return (
    <div className="space-y-6">
      <PageHeader title="Assessment Analytics" description="Body scan volume, domain averages, and template quality." />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <StatCard title="Total Assessments" value={formatNumber(data?.totalAssessments)} />
        <StatCard title="This Week" value={formatNumber(data?.assessmentsThisWeek)} />
        <StatCard title="This Month" value={formatNumber(data?.assessmentsThisMonth)} />
      </div>

      <div className="grid grid-cols-1 gap-6 xl:grid-cols-2">
        <ChartCard title="Domain Averages" loading={loading} empty={!domains.length}>
          <BarsChart data={domains} />
        </ChartCard>
        <ChartCard title="Template Scores" loading={loading} empty={!templates.length}>
          <BarsChart data={templates.map((template) => ({ name: templateName(template.templateName), value: template.avgBodyScore }))} />
        </ChartCard>
      </div>

      <DataTable
        columns={columns}
        data={templates}
        getRowKey={(row, index) => row.templateId ?? `template-${index}`}
        loading={loading}
        emptyTitle="No assessment templates"
        emptyDescription="No body scan results matched existing templates yet."
      />
    </div>
  );
}
