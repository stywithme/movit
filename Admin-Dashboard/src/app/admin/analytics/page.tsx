'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Button, Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { PageHeader } from '@/components/common';
import {
  Users,
  BarChart3,
  ClipboardCheck,
  CalendarDays,
  TrendingUp,
  ArrowRight,
  RefreshCw,
} from 'lucide-react';

interface PlatformStats {
  totalUsers: number;
  activeThisMonth: number;
}

interface LevelDistribution {
  levelId: string;
  levelName: string;
  color: string;
  count: number;
}

interface AssessmentStats {
  totalAssessments: number;
  thisMonth: number;
  avgBodyScore: number;
}

interface ProgramStats {
  totalPrograms: number;
  completionRate: number;
}

interface RuleStats {
  rulesTriggered: number;
  mostEffectiveRule: string;
}

export default function AnalyticsOverviewPage() {
  const [platform, setPlatform] = useState<PlatformStats | null>(null);
  const [levels, setLevels] = useState<LevelDistribution[]>([]);
  const [assessments, setAssessments] = useState<AssessmentStats | null>(null);
  const [programs, setPrograms] = useState<ProgramStats | null>(null);
  const [rules, setRules] = useState<RuleStats | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [platformRes, levelsRes, assessmentsRes, programsRes, rulesRes] = await Promise.allSettled([
        fetch('/api/admin/analytics/platform').then((r) => r.json()),
        fetch('/api/admin/analytics/levels').then((r) => r.json()),
        fetch('/api/admin/analytics/assessments').then((r) => r.json()),
        fetch('/api/admin/analytics/programs').then((r) => r.json()),
        fetch('/api/admin/analytics/rules').then((r) => r.json()),
      ]);

      if (platformRes.status === 'fulfilled' && platformRes.value?.data) {
        setPlatform(platformRes.value.data);
      }
      if (levelsRes.status === 'fulfilled' && levelsRes.value?.data) {
        setLevels(Array.isArray(levelsRes.value.data.distribution) ? levelsRes.value.data.distribution : []);
      }
      if (assessmentsRes.status === 'fulfilled' && assessmentsRes.value?.data) {
        setAssessments(assessmentsRes.value.data);
      }
      if (programsRes.status === 'fulfilled' && programsRes.value?.data) {
        setPrograms(programsRes.value.data);
      }
      if (rulesRes.status === 'fulfilled' && rulesRes.value?.data) {
        setRules(rulesRes.value.data);
      }
    } catch (error) {
      console.error('Error fetching analytics:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAll();
  }, []);

  const maxLevelCount = Math.max(...levels.map((l) => l.count), 1);

  return (
    <div className="space-y-6">
      <PageHeader
        title="Analytics Overview"
        description="Platform-wide statistics and insights."
        actions={
          <Button type="button" variant="outline" onClick={fetchAll}>
          <RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
          </Button>
        }
      />

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 5 }).map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader>
                <div className="h-5 w-1/2 rounded bg-muted" />
              </CardHeader>
              <CardContent>
                <div className="mb-2 h-8 w-1/3 rounded bg-muted" />
                <div className="h-4 w-2/3 rounded bg-muted/60" />
              </CardContent>
            </Card>
          ))}
        </div>
      ) : (
        <>
          {/* Stat Cards Row */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {/* Users Stats */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-muted-foreground">Total Users</CardTitle>
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10">
                    <Users className="h-5 w-5 text-primary" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{platform?.totalUsers ?? '-'}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  <span className="font-medium text-success">{platform?.activeThisMonth ?? 0}</span>{' '}
                  active this month
                </p>
              </CardContent>
            </Card>

            {/* Assessment Stats */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-muted-foreground">Assessments</CardTitle>
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent">
                    <ClipboardCheck className="h-5 w-5 text-purple-600" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{assessments?.totalAssessments ?? '-'}</p>
                <div className="flex items-center justify-between mt-1">
                  <p className="text-sm text-muted-foreground">
                    <span className="text-purple-600 font-medium">{assessments?.thisMonth ?? 0}</span>{' '}
                    this month
                  </p>
                  <p className="text-sm text-muted-foreground">
                    Avg score:{' '}
                    <span className="font-medium text-foreground">
                      {assessments?.avgBodyScore != null ? assessments.avgBodyScore.toFixed(1) : '-'}
                    </span>
                  </p>
                </div>
              </CardContent>
            </Card>

            {/* Program Stats */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-muted-foreground">Programs</CardTitle>
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-success/10">
                    <CalendarDays className="h-5 w-5 text-success" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{programs?.totalPrograms ?? '-'}</p>
                <p className="mt-1 text-sm text-muted-foreground">
                  Completion rate:{' '}
                  <span className="font-medium text-success">
                    {programs?.completionRate != null ? `${programs.completionRate.toFixed(1)}%` : '-'}
                  </span>
                </p>
              </CardContent>
            </Card>

            {/* Rule Effectiveness */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-muted-foreground">Progression Rules</CardTitle>
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-warning/10">
                    <TrendingUp className="h-5 w-5 text-warning" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold">{rules?.rulesTriggered ?? '-'}</p>
                <p className="mt-1 truncate text-sm text-muted-foreground" title={rules?.mostEffectiveRule}>
                  Top rule:{' '}
                  <span className="font-medium text-foreground">{rules?.mostEffectiveRule ?? '-'}</span>
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Level Distribution Chart */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-accent">
                    <BarChart3 className="h-5 w-5 text-indigo-600" />
                  </div>
                  <div>
                    <CardTitle>Level Distribution</CardTitle>
                    <p className="mt-0.5 text-sm text-muted-foreground">Users per training level</p>
                  </div>
                </div>
                <Link
                  href="/admin/analytics/levels"
                  className="flex items-center gap-1 text-sm text-primary hover:text-primary/80"
                >
                  View details <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {levels.length === 0 ? (
                <p className="py-8 text-center text-sm text-muted-foreground">No level data available</p>
              ) : (
                <div className="space-y-3">
                  {levels.map((level) => (
                    <div key={level.levelId} className="flex items-center gap-3">
                      <div className="w-32 flex items-center gap-2 flex-shrink-0">
                        <div
                          className="h-3 w-3 rounded-full flex-shrink-0"
                          style={{ backgroundColor: level.color || 'hsl(var(--muted-foreground))' }}
                        />
                        <span className="truncate text-sm font-medium">
                          {level.levelName}
                        </span>
                      </div>
                      <div className="h-6 flex-1 overflow-hidden rounded-full bg-muted">
                        <div
                          className="h-full rounded-full transition-all duration-500 ease-out"
                          style={{
                            width: `${Math.max((level.count / maxLevelCount) * 100, 2)}%`,
                            backgroundColor: level.color || 'hsl(var(--muted-foreground))',
                            opacity: 0.8,
                          }}
                        />
                      </div>
                      <span className="w-10 flex-shrink-0 text-right text-sm font-semibold">
                        {level.count}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Quick Links */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Link href="/admin/analytics/programs" className="group">
              <Card interactive className="h-full">
                <CardContent className="pt-6 flex items-center gap-4">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-success/10 transition-colors group-hover:bg-success/20">
                    <CalendarDays className="h-6 w-6 text-success" />
                  </div>
                  <div>
                    <h3 className="font-semibold">Program Analytics</h3>
                    <p className="text-sm text-muted-foreground">Enrollment & completion rates</p>
                  </div>
                  <ArrowRight className="ml-auto h-5 w-5 text-muted-foreground transition-colors group-hover:text-foreground" />
                </CardContent>
              </Card>
            </Link>

            <Link href="/admin/analytics/levels" className="group">
              <Card interactive className="h-full">
                <CardContent className="pt-6 flex items-center gap-4">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent transition-colors group-hover:bg-accent/80">
                    <BarChart3 className="h-6 w-6 text-indigo-600" />
                  </div>
                  <div>
                    <h3 className="font-semibold">Level Analytics</h3>
                    <p className="text-sm text-muted-foreground">Distribution & transitions</p>
                  </div>
                  <ArrowRight className="ml-auto h-5 w-5 text-muted-foreground transition-colors group-hover:text-foreground" />
                </CardContent>
              </Card>
            </Link>

            <Link href="/admin/analytics/assessments" className="group">
              <Card interactive className="h-full">
                <CardContent className="pt-6 flex items-center gap-4">
                  <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-accent transition-colors group-hover:bg-accent/80">
                    <ClipboardCheck className="h-6 w-6 text-purple-600" />
                  </div>
                  <div>
                    <h3 className="font-semibold">Assessment Analytics</h3>
                    <p className="text-sm text-muted-foreground">Scores & domain averages</p>
                  </div>
                  <ArrowRight className="ml-auto h-5 w-5 text-muted-foreground transition-colors group-hover:text-foreground" />
                </CardContent>
              </Card>
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
