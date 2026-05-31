'use client';

import { useEffect, useState } from 'react';
import { Button, Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { DataTable, PageHeader, type DataTableColumn } from '@/components/common';
import { RefreshCw } from 'lucide-react';

interface TemplateScore {
  templateId: string | null;
  templateName: unknown;
  avgBodyScore: number;
  count: number;
}

interface AssessmentAnalytics {
  totalAssessments: number;
  assessmentsThisWeek: number;
  assessmentsThisMonth: number;
  avgBodyScorePerTemplate: TemplateScore[];
  domainAverages: Record<string, number | null>;
  lowestScoringDomains: { domain: string; avgScore: number }[];
}

function domainAvgToArray(domainAverages: Record<string, number | null>): { domain: string; average: number }[] {
  return Object.entries(domainAverages)
    .filter(([, v]) => v != null)
    .map(([domain, avg]) => ({ domain, average: avg as number }));
}

const DOMAIN_CONFIG: Record<string, { label: string; dotClass: string; trackClass: string; barClass: string }> = {
  mobility: { label: 'Mobility', dotClass: 'bg-primary', trackClass: 'bg-primary/10', barClass: 'bg-primary' },
  control: { label: 'Control', dotClass: 'bg-violet-500', trackClass: 'bg-violet-100', barClass: 'bg-violet-500' },
  symmetry: { label: 'Symmetry', dotClass: 'bg-success', trackClass: 'bg-success/10', barClass: 'bg-success' },
  safety: { label: 'Safety', dotClass: 'bg-warning', trackClass: 'bg-warning/10', barClass: 'bg-warning' },
};

function templateNameToString(templateName: unknown): string {
  if (!templateName) return 'Unknown Template';
  if (typeof templateName === 'object') {
    const localized = templateName as Record<string, string>;
    return localized.en || localized.ar || 'Template';
  }
  return String(templateName);
}

export default function AssessmentAnalyticsPage() {
  const [data, setData] = useState<AssessmentAnalytics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/analytics/assessments');
      const json = await res.json();
      if (json.success && json.data) {
        setData(json.data);
      }
    } catch (error) {
      console.error('Error fetching assessment analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const domainArray = data?.domainAverages ? domainAvgToArray(data.domainAverages) : [];
  const maxDomainAvg = domainArray.length > 0
    ? Math.max(...domainArray.map((d) => d.average), 1)
    : 1;

  const templateColumns: DataTableColumn<TemplateScore>[] = [
    {
      key: 'template',
      header: 'Template',
      cell: (template) => (
        <div>
          <p className="font-medium">{templateNameToString(template.templateName)}</p>
          <p className="text-xs text-muted-foreground">{template.count} assessments</p>
        </div>
      ),
    },
    {
      key: 'score',
      header: 'Average Score',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (template) => (
        <div className="ml-auto flex max-w-[180px] items-center justify-end gap-3">
          <div className="h-2 w-24 overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary transition-all duration-500"
              style={{ width: `${Math.min(template.avgBodyScore, 100)}%` }}
            />
          </div>
          <span className="w-12 text-right text-sm font-bold">{template.avgBodyScore.toFixed(1)}</span>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Assessment Analytics"
        description="Scores, templates, and domain performance."
        breadcrumbs={[
          { label: 'Analytics', href: '/admin/analytics' },
          { label: 'Assessments' },
        ]}
        actions={
          <Button type="button" variant="outline" onClick={fetchData}>
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        }
      />

      {loading ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardContent className="pt-6"><div className="h-40 rounded bg-muted" /></CardContent>
            </Card>
          ))}
        </div>
      ) : !data ? (
        <Card>
          <CardContent className="pt-6">
            <p className="py-12 text-center text-muted-foreground">No assessment data available</p>
          </CardContent>
        </Card>
      ) : (
        <>
          {/* Summary Stats */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-muted-foreground">Total Assessments</p>
                <p className="mt-1 text-3xl font-bold">{data.totalAssessments}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-muted-foreground">This Month</p>
                <p className="text-3xl font-bold text-purple-600 mt-1">{data.assessmentsThisMonth}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-muted-foreground">Avg Body Score</p>
                <p className="mt-1 text-3xl font-bold text-primary">
                  {data.avgBodyScorePerTemplate.length > 0
                    ? (data.avgBodyScorePerTemplate.reduce((s, t) => s + t.avgBodyScore * t.count, 0) /
                        Math.max(data.avgBodyScorePerTemplate.reduce((s, t) => s + t.count, 0), 1)).toFixed(1)
                    : '—'}
                </p>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Domain Averages */}
            <Card>
              <CardHeader>
                <CardTitle>Domain Averages</CardTitle>
                <p className="text-sm text-muted-foreground">Performance across assessment domains</p>
              </CardHeader>
              <CardContent>
                {domainArray.length === 0 ? (
                  <p className="py-8 text-center text-sm text-muted-foreground">No domain data available</p>
                ) : (
                  <div className="space-y-5">
                    {domainArray.map((domain) => {
                      const config = DOMAIN_CONFIG[domain.domain.toLowerCase()] || {
                        label: domain.domain,
                        dotClass: 'bg-muted-foreground',
                        trackClass: 'bg-muted',
                        barClass: 'bg-muted-foreground',
                      };
                      const pctOfMax = (domain.average / maxDomainAvg) * 100;
                      return (
                        <div key={domain.domain}>
                          <div className="flex items-center justify-between mb-2">
                            <div className="flex items-center gap-2">
                              <div className={`h-3 w-3 rounded-sm ${config.dotClass}`} />
                              <span className="text-sm font-semibold">{config.label}</span>
                            </div>
                            <span className="text-sm font-bold">
                              {domain.average.toFixed(1)}
                            </span>
                          </div>
                          <div className={`h-4 overflow-hidden rounded-full ${config.trackClass}`}>
                            <div
                              className={`h-full rounded-full transition-all duration-700 ease-out ${config.barClass}`}
                              style={{ width: `${Math.max(pctOfMax, 3)}%` }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>

            <DataTable
              columns={templateColumns}
              data={data.avgBodyScorePerTemplate || []}
              getRowKey={(template, index) => template.templateId ?? `no-template-${index}`}
              emptyTitle="No template data available"
              emptyDescription="Template averages will appear after assessments are completed."
            />
          </div>
        </>
      )}
    </div>
  );
}
