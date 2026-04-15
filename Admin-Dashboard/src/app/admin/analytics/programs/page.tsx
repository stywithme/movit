'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui';
import { ArrowLeft, RefreshCw } from 'lucide-react';

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
            <h1 className="text-2xl font-bold text-gray-900">Program Analytics</h1>
            <p className="text-gray-600 mt-1">Enrollment, completion, and performance metrics</p>
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
        <Card className="animate-pulse">
          <CardContent className="pt-6">
            <div className="h-64 bg-gray-100 rounded" />
          </CardContent>
        </Card>
      ) : programs.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-gray-500 text-center py-12">No program analytics data available</p>
          </CardContent>
        </Card>
      ) : (
        <>
          {/* Completion Rate Chart */}
          <Card>
            <CardHeader>
              <CardTitle>Completion Rates by Program</CardTitle>
              <p className="text-sm text-gray-500">Percentage of enrolled users who completed each program</p>
            </CardHeader>
            <CardContent>
              <div className="space-y-3">
                {programs
                  .sort((a, b) => b.completionRate - a.completionRate)
                  .map((program) => (
                    <div key={program.id} className="flex items-center gap-3">
                      <span className="text-sm font-medium text-gray-700 w-48 truncate flex-shrink-0" title={program.name}>
                        {program.name}
                      </span>
                      <div className="flex-1 bg-gray-100 rounded-full h-7 overflow-hidden relative">
                        <div
                          className="h-full rounded-full bg-emerald-500 transition-all duration-500 ease-out flex items-center"
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

          {/* Programs Table */}
          <Card>
            <CardHeader>
              <CardTitle>Program Details</CardTitle>
            </CardHeader>
            <CardContent className="p-0">
              <div className="overflow-x-auto">
                <table className="w-full">
                  <thead className="bg-gray-50 border-y border-gray-200">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Program Name
                      </th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Enrollments
                      </th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Completion Rate
                      </th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Avg Score Improvement
                      </th>
                      <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase tracking-wider">
                        Avg Completion Time
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {programs.map((program) => (
                      <tr key={program.id} className="hover:bg-gray-50 transition-colors">
                        <td className="px-6 py-4">
                          <span className="font-medium text-gray-900">{program.name}</span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className="text-sm text-gray-700 font-medium">{program.enrollments}</span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span
                            className={`inline-flex px-2 py-1 text-xs font-semibold rounded-full ${
                              program.completionRate >= 70
                                ? 'bg-green-100 text-green-700'
                                : program.completionRate >= 40
                                ? 'bg-amber-100 text-amber-700'
                                : 'bg-red-100 text-red-700'
                            }`}
                          >
                            {program.completionRate.toFixed(1)}%
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span
                            className={`text-sm font-medium ${
                              program.avgBodyScoreImprovement > 0 ? 'text-green-600' : 'text-gray-600'
                            }`}
                          >
                            {program.avgBodyScoreImprovement > 0 ? '+' : ''}
                            {program.avgBodyScoreImprovement.toFixed(1)}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <span className="text-sm text-gray-600">{program.avgCompletionTimeDays} days</span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
