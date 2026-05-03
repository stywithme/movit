'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Map } from 'lucide-react';
import { toast } from 'react-hot-toast';
import { LocalizedText } from '@/lib/types/localized';
import { Button, Input, Select } from '@/components/ui';
import { getAutoAssignmentReadiness } from './_lib/auto-assignment';

function formatProgramLevelRange(program: Program): string {
  const min = program.levelRangeMin;
  const max = program.levelRangeMax;
  if (min != null && max != null) {
    return min === max ? `${min}` : `${min}–${max}`;
  }
  if (min != null) return `${min}`;
  if (max != null) return `${max}`;
  return '—';
}

interface Program {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  slug: string;
  coverImageUrl: string | null;
  durationWeeks: number;
  programType?: string;
  autoAssignable?: boolean;
  version?: number;
  levelRangeMin?: number | null;
  levelRangeMax?: number | null;
  prescriptionPriority?: number | null;
  /** Present on list/detail from API; used by client readiness when `autoAssignmentReadiness` is absent. */
  programAttributes?: Array<{
    mode: string;
    attributeValue?: {
      code: string;
      attribute?: { code: string | null } | null;
    } | null;
  }>;
  autoAssignmentReadiness?: {
    ready: boolean;
    entersAutoAssignment: boolean;
    missingFields: string[];
    status: 'ready' | 'incomplete' | 'manual_only';
  };
  activeEnrollmentCount?: number;
  isPublished: boolean;
  createdAt: string;
  updatedAt: string;
}

interface Pagination {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export default function ProgramsListPage() {
  const [programs, setPrograms] = useState<Program[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [readinessFilter, setReadinessFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const hasActiveFilters = !!(statusFilter || readinessFilter || searchQuery);

  const fetchPrograms = async (page = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page.toString());
      if (statusFilter) params.set('status', statusFilter);
      if (readinessFilter) params.set('readiness', readinessFilter);
      if (searchQuery) params.set('search', searchQuery);

      const res = await fetch(`/api/programs?${params}`);
      const data = await res.json();

      if (data.success) {
        setPrograms(data.data);
        setPagination(data.pagination);
      }
    } catch (error) {
      console.error('Error fetching programs:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const timer = window.setTimeout(() => {
      fetchPrograms(1);
    }, searchQuery ? 250 : 0);

    return () => window.clearTimeout(timer);
  }, [statusFilter, readinessFilter, searchQuery]);

  const handlePublish = async (id: string) => {
    try {
      const res = await fetch(`/api/programs/${id}/publish`, { method: 'POST' });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success) {
        toast.success('Program published');
        fetchPrograms(pagination?.page || 1);
      } else {
        toast.error(data?.error || 'Failed to publish program');
      }
    } catch (error) {
      console.error('Error publishing:', error);
      toast.error('Failed to publish program');
    }
  };

  const handleUnpublish = async (id: string) => {
    try {
      const res = await fetch(`/api/programs/${id}/publish`, { method: 'DELETE' });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success) {
        toast.success('Program moved to draft');
        fetchPrograms(pagination?.page || 1);
      } else {
        toast.error(data?.error || 'Failed to unpublish program');
      }
    } catch (error) {
      console.error('Error unpublishing:', error);
      toast.error('Failed to unpublish program');
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this program?')) return;
    try {
      const res = await fetch(`/api/programs/${id}`, { method: 'DELETE' });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success) {
        toast.success('Program deleted');
        fetchPrograms(pagination?.page || 1);
      } else {
        toast.error(data?.error || 'Failed to delete program');
      }
    } catch (error) {
      console.error('Error deleting:', error);
      toast.error('Failed to delete program');
    }
  };

  const handleDuplicate = async (id: string) => {
    try {
      const res = await fetch(`/api/programs/${id}/duplicate`, { method: 'POST' });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success) {
        toast.success('Program duplicated');
        fetchPrograms(pagination?.page || 1);
      } else {
        toast.error(data?.error || 'Failed to duplicate program');
      }
    } catch (error) {
      console.error('Error duplicating:', error);
      toast.error('Failed to duplicate program');
    }
  };

  const resetFilters = () => {
    setStatusFilter('');
    setReadinessFilter('');
    setSearchQuery('');
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Programs</h1>
          <p className="text-gray-600 mt-1">Manage training programs</p>
        </div>
        <div className="flex items-center gap-3">
          <Link href="/admin/programs/new?programDomain=TRAINING">
            <Button variant="secondary">New Training</Button>
          </Link>
          <Link href="/admin/programs/new?programDomain=MOBILITY">
            <Button variant="secondary">New Mobility</Button>
          </Link>
          <Link href="/admin/programs/new?programDomain=THERAPEUTIC">
            <Button variant="secondary">New Therapeutic</Button>
          </Link>
          <Link href="/admin/programs/map">
            <Button variant="outline" icon={<Map className="h-4 w-4" />}>
              Open Map
            </Button>
          </Link>
          <Link
            href="/admin/programs/new"
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Program
          </Link>
        </div>
      </div>

      {/* Filters */}
      <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
        <div className="flex flex-wrap gap-4 items-end">
          <div className="flex-1 min-w-[240px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
            <Input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search programs..."
              className="flex-1"
            />
            <p className="mt-1 text-xs text-gray-400">Search updates automatically.</p>
          </div>

          <div className="w-40">
            <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={[
                { value: '', label: 'All' },
                { value: 'draft', label: 'Draft' },
                { value: 'published', label: 'Published' },
              ]}
            />
          </div>

          <div className="w-44">
            <label className="block text-sm font-medium text-gray-700 mb-1">Readiness</label>
            <Select
              value={readinessFilter}
              onChange={(e) => setReadinessFilter(e.target.value)}
              options={[
                { value: '', label: 'All' },
                { value: 'ready', label: 'Ready' },
                { value: 'incomplete', label: 'Incomplete' },
                { value: 'manual_only', label: 'Manual only' },
              ]}
            />
          </div>

          {hasActiveFilters ? (
            <Button type="button" variant="ghost" onClick={resetFilters}>
              Reset
            </Button>
          ) : null}
        </div>
      </div>

      {/* Programs Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading...</div>
        ) : programs.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <p>No programs found.</p>
            <Link href="/admin/programs/new" className="text-blue-600 hover:underline mt-2 inline-block">
              Create your first program
            </Link>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Program
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Level range
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Version
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Readiness
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Enrollments
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Status
                </th>
                <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {programs.map((program) => {
                const readiness =
                  program.autoAssignmentReadiness ?? getAutoAssignmentReadiness(program);

                return (
                  <tr key={program.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <div>
                      <p className="font-medium text-gray-900">{program.name.en}</p>
                      <p className="text-sm text-gray-500">{program.name.ar}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    <p>{formatProgramLevelRange(program)}</p>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    v{program.version ?? 1}
                  </td>
                  <td className="px-6 py-4">
                    <div className="space-y-1">
                      <span
                        className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                          readiness.status === 'ready'
                            ? 'bg-green-100 text-green-800'
                            : readiness.status === 'incomplete'
                              ? 'bg-yellow-100 text-yellow-800'
                              : 'bg-gray-100 text-gray-700'
                        }`}
                      >
                        {readiness.status === 'ready'
                          ? 'ready'
                          : readiness.status === 'incomplete'
                            ? 'incomplete'
                            : 'manual only'}
                      </span>
                      {readiness.status === 'incomplete' && readiness.missingFields.length > 0 ? (
                        <p className="text-xs text-gray-400">
                          Missing: {readiness.missingFields.join(', ')}
                        </p>
                      ) : null}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {typeof program.activeEnrollmentCount === 'number' &&
                    program.activeEnrollmentCount > 0 ? (
                      <span className="inline-flex px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-800">
                        {program.activeEnrollmentCount} active
                      </span>
                    ) : (
                      <span className="text-xs text-gray-400">0</span>
                    )}
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                        program.isPublished
                          ? 'bg-green-100 text-green-800'
                          : 'bg-yellow-100 text-yellow-800'
                      }`}
                    >
                      {program.isPublished ? 'published' : 'draft'}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex justify-end gap-2">
                      <Link
                        href={`/admin/programs/${program.id}/edit`}
                        className="text-blue-600 hover:text-blue-800 text-sm"
                      >
                        Edit
                      </Link>
                      <Link
                        href={`/admin/programs/map?programId=${program.id}`}
                        className="text-indigo-600 hover:text-indigo-800 text-sm"
                      >
                        View on Map
                      </Link>
                      <button
                        onClick={() => handleDuplicate(program.id)}
                        className="text-gray-600 hover:text-gray-800 text-sm"
                      >
                        Duplicate
                      </button>
                      {program.isPublished ? (
                        <button
                          onClick={() => handleUnpublish(program.id)}
                          className="text-yellow-600 hover:text-yellow-800 text-sm"
                        >
                          Unpublish
                        </button>
                      ) : (
                        <button
                          onClick={() => handlePublish(program.id)}
                          className="text-green-600 hover:text-green-800 text-sm"
                        >
                          Publish
                        </button>
                      )}
                      <button
                        onClick={() => handleDelete(program.id)}
                        className="text-red-600 hover:text-red-800 text-sm"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        {/* Pagination */}
        {pagination && pagination.totalPages > 1 && (
          <div className="px-6 py-4 border-t border-gray-200 flex justify-between items-center">
            <p className="text-sm text-gray-600">
              Showing {(pagination.page - 1) * pagination.limit + 1} to{' '}
              {Math.min(pagination.page * pagination.limit, pagination.total)} of {pagination.total}
            </p>
            <div className="flex gap-2">
              <button
                onClick={() => fetchPrograms(pagination.page - 1)}
                disabled={pagination.page === 1}
                className="px-3 py-1.5 border border-gray-300 bg-white text-gray-800 rounded text-sm hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400 disabled:border-gray-200 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                onClick={() => fetchPrograms(pagination.page + 1)}
                disabled={pagination.page === pagination.totalPages}
                className="px-3 py-1.5 border border-gray-300 bg-white text-gray-800 rounded text-sm hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400 disabled:border-gray-200 disabled:cursor-not-allowed"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
