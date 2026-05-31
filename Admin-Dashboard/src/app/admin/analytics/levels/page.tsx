'use client';

import { useEffect, useState } from 'react';
import { Badge, Button, Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { DataTable, PageHeader, type DataTableColumn } from '@/components/common';
import { RefreshCw } from 'lucide-react';

interface LevelDistribution {
  levelId: string;
  levelName: string;
  color: string;
  count: number;
}

interface LevelTransition {
  fromLevelName: string;
  fromLevelColor: string;
  toLevelName: string;
  toLevelColor: string;
  avgDays: number;
  transitionCount: number;
}

export default function LevelAnalyticsPage() {
  const [distribution, setDistribution] = useState<LevelDistribution[]>([]);
  const [transitions, setTransitions] = useState<LevelTransition[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    setLoading(true);
    try {
      const [levelsRes, transitionsRes] = await Promise.allSettled([
        fetch('/api/admin/analytics/levels').then((r) => r.json()),
        fetch('/api/admin/analytics/level-transitions').then((r) => r.json()),
      ]);

      if (levelsRes.status === 'fulfilled' && levelsRes.value?.data) {
        setDistribution(
          Array.isArray(levelsRes.value.data.distribution) ? levelsRes.value.data.distribution : []
        );
      }
      if (transitionsRes.status === 'fulfilled' && transitionsRes.value?.data) {
        setTransitions(
          Array.isArray(transitionsRes.value.data) ? transitionsRes.value.data : []
        );
      }
    } catch (error) {
      console.error('Error fetching level analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const totalUsers = distribution.reduce((sum, l) => sum + l.count, 0);
  const maxCount = Math.max(...distribution.map((l) => l.count), 1);

  const transitionColumns: DataTableColumn<LevelTransition>[] = [
    {
      key: 'transition',
      header: 'Transition',
      cell: (transition) => (
        <div className="flex min-w-[280px] items-center gap-2">
          <span className="flex items-center gap-1.5">
            <span
              className="inline-block h-2.5 w-2.5 rounded-full"
              style={{ backgroundColor: transition.fromLevelColor || 'hsl(var(--muted-foreground))' }}
            />
            <span className="text-sm font-medium">{transition.fromLevelName}</span>
          </span>
          <span className="text-muted-foreground">→</span>
          <span className="flex items-center gap-1.5">
            <span
              className="inline-block h-2.5 w-2.5 rounded-full"
              style={{ backgroundColor: transition.toLevelColor || 'hsl(var(--muted-foreground))' }}
            />
            <span className="text-sm font-medium">{transition.toLevelName}</span>
          </span>
        </div>
      ),
    },
    {
      key: 'avgDays',
      header: 'Avg Days',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (transition) => <span className="text-sm font-medium">{transition.avgDays.toFixed(1)} days</span>,
    },
    {
      key: 'count',
      header: 'Count',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (transition) => <Badge variant="secondary">{transition.transitionCount}</Badge>,
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Level Analytics"
        description="User distribution across levels and transition patterns."
        breadcrumbs={[
          { label: 'Analytics', href: '/admin/analytics' },
          { label: 'Levels' },
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
          <Card className="animate-pulse">
            <CardContent className="pt-6"><div className="h-64 rounded bg-muted" /></CardContent>
          </Card>
          <Card className="animate-pulse">
            <CardContent className="pt-6"><div className="h-64 rounded bg-muted" /></CardContent>
          </Card>
        </div>
      ) : (
        <>
          {/* Summary Row */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-muted-foreground">Total Users Assigned</p>
                <p className="mt-1 text-3xl font-bold">{totalUsers}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-muted-foreground">Active Levels</p>
                <p className="mt-1 text-3xl font-bold">{distribution.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-muted-foreground">Recorded Transitions</p>
                <p className="mt-1 text-3xl font-bold">
                  {transitions.reduce((sum, t) => sum + t.transitionCount, 0)}
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Level Distribution */}
          <Card>
            <CardHeader>
              <CardTitle>Level Distribution</CardTitle>
              <p className="text-sm text-muted-foreground">Number of users currently at each level</p>
            </CardHeader>
            <CardContent>
              {distribution.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">No level data available</p>
              ) : (
                <div className="space-y-4">
                  {distribution.map((level) => {
                    const pct = totalUsers > 0 ? ((level.count / totalUsers) * 100).toFixed(1) : '0';
                    return (
                      <div key={level.levelId} className="group">
                        <div className="flex items-center justify-between mb-1.5">
                          <div className="flex items-center gap-2">
                            <div
                              className="h-3.5 w-3.5 rounded-full border-2 border-white shadow-sm"
                              style={{ backgroundColor: level.color || 'hsl(var(--muted-foreground))' }}
                            />
                            <span className="text-sm font-semibold">{level.levelName}</span>
                          </div>
                          <div className="flex items-center gap-3">
                            <span className="text-xs text-muted-foreground">{pct}%</span>
                            <span className="text-sm font-bold">{level.count}</span>
                          </div>
                        </div>
                        <div className="h-5 overflow-hidden rounded-full bg-muted">
                          <div
                            className="h-full rounded-full transition-all duration-700 ease-out"
                            style={{
                              width: `${Math.max((level.count / maxCount) * 100, 2)}%`,
                              backgroundColor: level.color || 'hsl(var(--muted-foreground))',
                              opacity: 0.75,
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

          <DataTable
            columns={transitionColumns}
            data={transitions}
            getRowKey={(transition, index) => `${transition.fromLevelName}-${transition.toLevelName}-${index}`}
            emptyTitle="No transition data available"
            emptyDescription="Level transition analytics will appear after users move between levels."
          />
        </>
      )}
    </div>
  );
}
