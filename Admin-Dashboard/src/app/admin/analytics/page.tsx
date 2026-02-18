'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
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
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Analytics Overview</h1>
          <p className="text-gray-600 mt-1">Platform-wide statistics and insights</p>
        </div>
        <button
          onClick={fetchAll}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 5 }).map((_, i) => (
            <Card key={i} className="animate-pulse">
              <CardHeader>
                <div className="h-5 bg-gray-200 rounded w-1/2" />
              </CardHeader>
              <CardContent>
                <div className="h-8 bg-gray-200 rounded w-1/3 mb-2" />
                <div className="h-4 bg-gray-100 rounded w-2/3" />
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
                  <CardTitle className="text-sm font-medium text-gray-500">Total Users</CardTitle>
                  <div className="h-9 w-9 rounded-lg bg-blue-50 flex items-center justify-center">
                    <Users className="h-5 w-5 text-blue-600" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold text-gray-900">{platform?.totalUsers ?? '—'}</p>
                <p className="text-sm text-gray-500 mt-1">
                  <span className="text-green-600 font-medium">{platform?.activeThisMonth ?? 0}</span>{' '}
                  active this month
                </p>
              </CardContent>
            </Card>

            {/* Assessment Stats */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-gray-500">Assessments</CardTitle>
                  <div className="h-9 w-9 rounded-lg bg-purple-50 flex items-center justify-center">
                    <ClipboardCheck className="h-5 w-5 text-purple-600" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold text-gray-900">{assessments?.totalAssessments ?? '—'}</p>
                <div className="flex items-center justify-between mt-1">
                  <p className="text-sm text-gray-500">
                    <span className="text-purple-600 font-medium">{assessments?.thisMonth ?? 0}</span>{' '}
                    this month
                  </p>
                  <p className="text-sm text-gray-500">
                    Avg score:{' '}
                    <span className="font-medium text-gray-700">
                      {assessments?.avgBodyScore != null ? assessments.avgBodyScore.toFixed(1) : '—'}
                    </span>
                  </p>
                </div>
              </CardContent>
            </Card>

            {/* Program Stats */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-gray-500">Programs</CardTitle>
                  <div className="h-9 w-9 rounded-lg bg-emerald-50 flex items-center justify-center">
                    <CalendarDays className="h-5 w-5 text-emerald-600" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold text-gray-900">{programs?.totalPrograms ?? '—'}</p>
                <p className="text-sm text-gray-500 mt-1">
                  Completion rate:{' '}
                  <span className="font-medium text-emerald-600">
                    {programs?.completionRate != null ? `${programs.completionRate.toFixed(1)}%` : '—'}
                  </span>
                </p>
              </CardContent>
            </Card>

            {/* Rule Effectiveness */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between">
                  <CardTitle className="text-sm font-medium text-gray-500">Progression Rules</CardTitle>
                  <div className="h-9 w-9 rounded-lg bg-amber-50 flex items-center justify-center">
                    <TrendingUp className="h-5 w-5 text-amber-600" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <p className="text-3xl font-bold text-gray-900">{rules?.rulesTriggered ?? '—'}</p>
                <p className="text-sm text-gray-500 mt-1 truncate" title={rules?.mostEffectiveRule}>
                  Top rule:{' '}
                  <span className="font-medium text-gray-700">{rules?.mostEffectiveRule ?? '—'}</span>
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Level Distribution Chart */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="h-9 w-9 rounded-lg bg-indigo-50 flex items-center justify-center">
                    <BarChart3 className="h-5 w-5 text-indigo-600" />
                  </div>
                  <div>
                    <CardTitle>Level Distribution</CardTitle>
                    <p className="text-sm text-gray-500 mt-0.5">Users per training level</p>
                  </div>
                </div>
                <Link
                  href="/admin/analytics/levels"
                  className="text-sm text-blue-600 hover:text-blue-700 flex items-center gap-1"
                >
                  View details <ArrowRight className="h-4 w-4" />
                </Link>
              </div>
            </CardHeader>
            <CardContent>
              {levels.length === 0 ? (
                <p className="text-gray-500 text-sm text-center py-8">No level data available</p>
              ) : (
                <div className="space-y-3">
                  {levels.map((level) => (
                    <div key={level.levelId} className="flex items-center gap-3">
                      <div className="w-32 flex items-center gap-2 flex-shrink-0">
                        <div
                          className="h-3 w-3 rounded-full flex-shrink-0"
                          style={{ backgroundColor: level.color || '#6B7280' }}
                        />
                        <span className="text-sm font-medium text-gray-700 truncate">
                          {level.levelName}
                        </span>
                      </div>
                      <div className="flex-1 bg-gray-100 rounded-full h-6 overflow-hidden">
                        <div
                          className="h-full rounded-full transition-all duration-500 ease-out"
                          style={{
                            width: `${Math.max((level.count / maxLevelCount) * 100, 2)}%`,
                            backgroundColor: level.color || '#6B7280',
                            opacity: 0.8,
                          }}
                        />
                      </div>
                      <span className="text-sm font-semibold text-gray-900 w-10 text-right flex-shrink-0">
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
                  <div className="h-12 w-12 rounded-xl bg-emerald-50 flex items-center justify-center group-hover:bg-emerald-100 transition-colors">
                    <CalendarDays className="h-6 w-6 text-emerald-600" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">Program Analytics</h3>
                    <p className="text-sm text-gray-500">Enrollment & completion rates</p>
                  </div>
                  <ArrowRight className="h-5 w-5 text-gray-400 ml-auto group-hover:text-gray-600 transition-colors" />
                </CardContent>
              </Card>
            </Link>

            <Link href="/admin/analytics/levels" className="group">
              <Card interactive className="h-full">
                <CardContent className="pt-6 flex items-center gap-4">
                  <div className="h-12 w-12 rounded-xl bg-indigo-50 flex items-center justify-center group-hover:bg-indigo-100 transition-colors">
                    <BarChart3 className="h-6 w-6 text-indigo-600" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">Level Analytics</h3>
                    <p className="text-sm text-gray-500">Distribution & transitions</p>
                  </div>
                  <ArrowRight className="h-5 w-5 text-gray-400 ml-auto group-hover:text-gray-600 transition-colors" />
                </CardContent>
              </Card>
            </Link>

            <Link href="/admin/analytics/assessments" className="group">
              <Card interactive className="h-full">
                <CardContent className="pt-6 flex items-center gap-4">
                  <div className="h-12 w-12 rounded-xl bg-purple-50 flex items-center justify-center group-hover:bg-purple-100 transition-colors">
                    <ClipboardCheck className="h-6 w-6 text-purple-600" />
                  </div>
                  <div>
                    <h3 className="font-semibold text-gray-900">Assessment Analytics</h3>
                    <p className="text-sm text-gray-500">Scores & domain averages</p>
                  </div>
                  <ArrowRight className="h-5 w-5 text-gray-400 ml-auto group-hover:text-gray-600 transition-colors" />
                </CardContent>
              </Card>
            </Link>
          </div>
        </>
      )}
    </div>
  );
}
