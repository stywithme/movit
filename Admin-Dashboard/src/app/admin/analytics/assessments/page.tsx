'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { ArrowLeft, RefreshCw } from 'lucide-react';

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

const DOMAIN_CONFIG: Record<string, { label: string; color: string; bgColor: string }> = {
  mobility: { label: 'Mobility', color: '#3B82F6', bgColor: '#DBEAFE' },
  control: { label: 'Control', color: '#8B5CF6', bgColor: '#EDE9FE' },
  symmetry: { label: 'Symmetry', color: '#10B981', bgColor: '#D1FAE5' },
  safety: { label: 'Safety', color: '#F59E0B', bgColor: '#FEF3C7' },
};

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

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-4">
          <Link
            href="/admin/analytics"
            className="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Assessment Analytics</h1>
            <p className="text-gray-600 mt-1">Scores, templates, and domain performance</p>
          </div>
        </div>
        <button
          onClick={fetchData}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {Array.from({ length: 4 }).map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardContent className="pt-6"><div className="h-40 bg-gray-100 rounded" /></CardContent>
            </Card>
          ))}
        </div>
      ) : !data ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-gray-500 text-center py-12">No assessment data available</p>
          </CardContent>
        </Card>
      ) : (
        <>
          {/* Summary Stats */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-gray-500">Total Assessments</p>
                <p className="text-3xl font-bold text-gray-900 mt-1">{data.totalAssessments}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-gray-500">This Month</p>
                <p className="text-3xl font-bold text-purple-600 mt-1">{data.assessmentsThisMonth}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-gray-500">Avg Body Score</p>
                <p className="text-3xl font-bold text-blue-600 mt-1">
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
                <p className="text-sm text-gray-500">Performance across assessment domains</p>
              </CardHeader>
              <CardContent>
                {domainArray.length === 0 ? (
                  <p className="text-gray-500 text-sm text-center py-8">No domain data available</p>
                ) : (
                  <div className="space-y-5">
                    {domainArray.map((domain) => {
                      const config = DOMAIN_CONFIG[domain.domain.toLowerCase()] || {
                        label: domain.domain,
                        color: '#6B7280',
                        bgColor: '#F3F4F6',
                      };
                      const pctOfMax = (domain.average / maxDomainAvg) * 100;
                      return (
                        <div key={domain.domain}>
                          <div className="flex items-center justify-between mb-2">
                            <div className="flex items-center gap-2">
                              <div
                                className="h-3 w-3 rounded-sm"
                                style={{ backgroundColor: config.color }}
                              />
                              <span className="text-sm font-semibold text-gray-800">{config.label}</span>
                            </div>
                            <span className="text-sm font-bold text-gray-900">
                              {domain.average.toFixed(1)}
                            </span>
                          </div>
                          <div
                            className="rounded-full h-4 overflow-hidden"
                            style={{ backgroundColor: config.bgColor }}
                          >
                            <div
                              className="h-full rounded-full transition-all duration-700 ease-out"
                              style={{
                                width: `${Math.max(pctOfMax, 3)}%`,
                                backgroundColor: config.color,
                              }}
                            />
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Average Body Score by Template */}
            <Card>
              <CardHeader>
                <CardTitle>Average Score by Template</CardTitle>
                <p className="text-sm text-gray-500">Body score averages per assessment template</p>
              </CardHeader>
              <CardContent>
                {(!data.avgBodyScorePerTemplate || data.avgBodyScorePerTemplate.length === 0) ? (
                  <p className="text-gray-500 text-sm text-center py-8">No template data available</p>
                ) : (
                  <div className="space-y-4">
                    {data.avgBodyScorePerTemplate.map((template) => (
                      <div
                        key={template.templateId ?? 'no-template'}
                        className="flex items-center justify-between p-3 bg-gray-50 rounded-lg hover:bg-gray-100 transition-colors"
                      >
                        <div>
                          <p className="text-sm font-medium text-gray-900">
                            {template.templateName
                              ? typeof template.templateName === 'object'
                                ? (template.templateName as Record<string, string>).en || (template.templateName as Record<string, string>).ar || 'Template'
                                : String(template.templateName)
                              : 'Unknown Template'}
                          </p>
                          <p className="text-xs text-gray-500">{template.count} assessments</p>
                        </div>
                        <div className="flex items-center gap-3">
                          <div className="w-24 bg-gray-200 rounded-full h-2 overflow-hidden">
                            <div
                              className="h-full rounded-full bg-blue-500 transition-all duration-500"
                              style={{ width: `${Math.min(template.avgBodyScore, 100)}%` }}
                            />
                          </div>
                          <span className="text-sm font-bold text-gray-900 w-12 text-right">
                            {template.avgBodyScore.toFixed(1)}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
