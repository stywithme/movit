'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { ArrowLeft, RefreshCw } from 'lucide-react';

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

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div className="flex items-center gap-4">
          <Link
            href="/admin/analytics"
            className="p-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors"
          >
            <ArrowLeft className="h-5 w-5" />
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Level Analytics</h1>
            <p className="text-gray-600 mt-1">User distribution across levels and transition patterns</p>
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
          <Card className="animate-pulse">
            <CardContent className="pt-6"><div className="h-64 bg-gray-100 rounded" /></CardContent>
          </Card>
          <Card className="animate-pulse">
            <CardContent className="pt-6"><div className="h-64 bg-gray-100 rounded" /></CardContent>
          </Card>
        </div>
      ) : (
        <>
          {/* Summary Row */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-gray-500">Total Users Assigned</p>
                <p className="text-3xl font-bold text-gray-900 mt-1">{totalUsers}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-gray-500">Active Levels</p>
                <p className="text-3xl font-bold text-gray-900 mt-1">{distribution.length}</p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="pt-6 text-center">
                <p className="text-sm text-gray-500">Recorded Transitions</p>
                <p className="text-3xl font-bold text-gray-900 mt-1">
                  {transitions.reduce((sum, t) => sum + t.transitionCount, 0)}
                </p>
              </CardContent>
            </Card>
          </div>

          {/* Level Distribution */}
          <Card>
            <CardHeader>
              <CardTitle>Level Distribution</CardTitle>
              <p className="text-sm text-gray-500">Number of users currently at each level</p>
            </CardHeader>
            <CardContent>
              {distribution.length === 0 ? (
                <p className="text-gray-500 text-sm text-center py-8">No level data available</p>
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
                              style={{ backgroundColor: level.color || '#6B7280' }}
                            />
                            <span className="text-sm font-semibold text-gray-800">{level.levelName}</span>
                          </div>
                          <div className="flex items-center gap-3">
                            <span className="text-xs text-gray-400">{pct}%</span>
                            <span className="text-sm font-bold text-gray-900">{level.count}</span>
                          </div>
                        </div>
                        <div className="bg-gray-100 rounded-full h-5 overflow-hidden">
                          <div
                            className="h-full rounded-full transition-all duration-700 ease-out"
                            style={{
                              width: `${Math.max((level.count / maxCount) * 100, 2)}%`,
                              backgroundColor: level.color || '#6B7280',
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

          {/* Level Transitions Table */}
          <Card>
            <CardHeader>
              <CardTitle>Level Transitions</CardTitle>
              <p className="text-sm text-gray-500">How users move between levels over time</p>
            </CardHeader>
            <CardContent className="p-0">
              {transitions.length === 0 ? (
                <p className="text-gray-500 text-sm text-center py-8 px-6">No transition data available</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full">
                    <thead className="bg-gray-50 border-y border-gray-200">
                      <tr>
                        <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Transition
                        </th>
                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Avg Days
                        </th>
                        <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                          Count
                        </th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {transitions.map((t, idx) => (
                        <tr key={idx} className="hover:bg-gray-50 transition-colors">
                          <td className="px-6 py-4">
                            <div className="flex items-center gap-2">
                              <span className="flex items-center gap-1.5">
                                <span
                                  className="h-2.5 w-2.5 rounded-full inline-block"
                                  style={{ backgroundColor: t.fromLevelColor || '#6B7280' }}
                                />
                                <span className="text-sm font-medium text-gray-700">{t.fromLevelName}</span>
                              </span>
                              <svg className="h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 8l4 4m0 0l-4 4m4-4H3" />
                              </svg>
                              <span className="flex items-center gap-1.5">
                                <span
                                  className="h-2.5 w-2.5 rounded-full inline-block"
                                  style={{ backgroundColor: t.toLevelColor || '#6B7280' }}
                                />
                                <span className="text-sm font-medium text-gray-700">{t.toLevelName}</span>
                              </span>
                            </div>
                          </td>
                          <td className="px-6 py-4 text-right">
                            <span className="text-sm font-medium text-gray-900">
                              {t.avgDays.toFixed(1)} days
                            </span>
                          </td>
                          <td className="px-6 py-4 text-right">
                            <span className="inline-flex px-2.5 py-0.5 text-xs font-semibold rounded-full bg-blue-50 text-blue-700">
                              {t.transitionCount}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
