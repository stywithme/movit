'use client';

import { useEffect, useState } from 'react';
import { Badge, Button, Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { DataTable, PageHeader, type DataTableColumn } from '@/components/common';
import { RefreshCw } from 'lucide-react';

interface ProgramAnalytics {
  id: string;
  name: string;
  enrollments: number;
  completionRate: number;
  avgBodyScoreImprovement: number;
  avgCompletionTimeDays: number;
}

export default function ProgramAnalyticsPage() {
  const [programs, setPrograms] = useState<ProgramAnalytics[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/admin/analytics/programs');
      const data = await res.json();
      if (data.success && data.data?.programs) {
        setPrograms(data.data.programs);
      }
    } catch (error) {
      console.error('Error fetching program analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const maxCompletionRate = Math.max(...programs.map((p) => p.completionRate), 1);

  const columns: DataTableColumn<ProgramAnalytics>[] = [
    {
      key: 'name',
      header: 'Program Name',
      cell: (program) => <span className="font-medium">{program.name}</span>,
    },
    {
      key: 'enrollments',
      header: 'Enrollments',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (program) => <span className="text-sm font-medium text-muted-foreground">{program.enrollments}</span>,
    },
    {
      key: 'completion',
      header: 'Completion Rate',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (program) => (
        <Badge
          variant={
            program.completionRate >= 70 ? 'success' : program.completionRate >= 40 ? 'warning' : 'destructive'
          }
        >
          {program.completionRate.toFixed(1)}%
        </Badge>
      ),
    },
    {
      key: 'score',
      header: 'Avg Score Improvement',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (program) => (
        <span className={`text-sm font-medium ${program.avgBodyScoreImprovement > 0 ? 'text-success' : 'text-muted-foreground'}`}>
          {program.avgBodyScoreImprovement > 0 ? '+' : ''}
          {program.avgBodyScoreImprovement.toFixed(1)}
        </span>
      ),
    },
    {
      key: 'time',
      header: 'Avg Completion Time',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (program) => <span className="text-sm text-muted-foreground">{program.avgCompletionTimeDays} days</span>,
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Program Analytics"
        description="Enrollment, completion, and performance metrics."
        breadcrumbs={[
          { label: 'Analytics', href: '/admin/analytics' },
          { label: 'Programs' },
        ]}
        actions={
          <Button type="button" variant="outline" onClick={fetchData}>
            <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
        }
      />

      {loading ? (
        <Card className="animate-pulse">
          <CardContent className="pt-6">
            <div className="h-64 rounded bg-muted" />
          </CardContent>
        </Card>
      ) : programs.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="py-12 text-center text-muted-foreground">No program analytics data available</p>
          </CardContent>
        </Card>
      ) : (
        <>
          {/* Completion Rate Chart */}
          <Card>
            <CardHeader>
              <CardTitle>Completion Rates by Program</CardTitle>
              <p className="text-sm text-muted-foreground">Percentage of enrolled users who completed each program</p>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {programs
                  .sort((a, b) => b.completionRate - a.completionRate)
                  .map((program) => (
                    <div key={program.id} className="flex items-center gap-3">
                      <span className="w-48 flex-shrink-0 truncate text-sm font-medium" title={program.name}>
                        {program.name}
                      </span>
                      <div className="relative h-7 flex-1 overflow-hidden rounded-full bg-muted">
                        <div
                          className="flex h-full items-center rounded-full bg-success transition-all duration-500 ease-out"
                          style={{ width: `${Math.max((program.completionRate / maxCompletionRate) * 100, 3)}%` }}
                        />
                        <span className="absolute inset-y-0 left-3 flex items-center text-xs font-semibold text-white mix-blend-difference">
                          {program.completionRate.toFixed(1)}%
                        </span>
                      </div>
                    </div>
                  ))}
              </div>
            </CardContent>
          </Card>

          <DataTable
            columns={columns}
            data={programs}
            getRowKey={(program) => program.id}
            emptyTitle="No program analytics data"
            emptyDescription="Program analytics will appear after enrollments are recorded."
          />
        </>
      )}
    </div>
  );
}
