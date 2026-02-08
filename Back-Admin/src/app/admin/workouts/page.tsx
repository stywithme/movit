'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Select } from '@/components/ui';

interface Workout {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  slug: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  status: string;
  createdAt: string;
  updatedAt: string;
  _count: {
    exercises: number;
  };
}

interface Pagination {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export default function WorkoutsListPage() {
  const [workouts, setWorkouts] = useState<Workout[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');

  const fetchWorkouts = async (page = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page.toString());
      if (statusFilter) params.set('status', statusFilter);
      if (searchQuery) params.set('search', searchQuery);

      const res = await fetch(`/api/workouts?${params}`);
      const data = await res.json();

      if (data.success) {
        setWorkouts(data.data);
        setPagination(data.pagination);
      }
    } catch (error) {
      console.error('Error fetching workouts:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWorkouts();
  }, [statusFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchWorkouts();
  };

  const handlePublish = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}/publish`, { method: 'POST' });
      const data = await res.json();
      if (data.success) {
        fetchWorkouts(pagination?.page || 1);
      } else {
        alert(data.errors?.join('\n') || data.error || 'Failed to publish');
      }
    } catch (error) {
      console.error('Error publishing:', error);
    }
  };

  const handleUnpublish = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}/publish`, { method: 'DELETE' });
      if (res.ok) fetchWorkouts(pagination?.page || 1);
    } catch (error) {
      console.error('Error unpublishing:', error);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this workout?')) return;
    try {
      const res = await fetch(`/api/workouts/${id}`, { method: 'DELETE' });
      if (res.ok) fetchWorkouts(pagination?.page || 1);
    } catch (error) {
      console.error('Error deleting:', error);
    }
  };

  const handleDuplicate = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}/duplicate`, { method: 'POST' });
      if (res.ok) fetchWorkouts(pagination?.page || 1);
    } catch (error) {
      console.error('Error duplicating:', error);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Workouts</h1>
          <p className="text-gray-600 mt-1">Manage workout templates</p>
        </div>
        <Link
          href="/admin/workouts/new"
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Workout
        </Link>
      </div>

      {/* Filters */}
      <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
        <div className="flex flex-wrap gap-4 items-end">
          {/* Search */}
          <form onSubmit={handleSearch} className="flex-1 min-w-[200px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
            <div className="flex gap-2">
              <Input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search workouts..."
                className="flex-1"
              />
              <button
                type="submit"
                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200"
              >
                Search
              </button>
            </div>
          </form>

          {/* Status Filter */}
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
        </div>
      </div>

      {/* Workouts Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading...</div>
        ) : workouts.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <p>No workouts found.</p>
            <Link href="/admin/workouts/new" className="text-blue-600 hover:underline mt-2 inline-block">
              Create your first workout
            </Link>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Workout
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Difficulty
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Exercises
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
              {workouts.map((workout) => (
                <tr key={workout.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <div>
                      <p className="font-medium text-gray-900">{workout.name.en}</p>
                      <p className="text-sm text-gray-500">{workout.name.ar}</p>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex px-2 py-1 text-xs font-medium rounded-full bg-gray-100 text-gray-700">
                      {workout.difficulty}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {workout._count.exercises}
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                        workout.status === 'published'
                          ? 'bg-green-100 text-green-800'
                          : 'bg-yellow-100 text-yellow-800'
                      }`}
                    >
                      {workout.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex justify-end gap-2">
                      <Link
                        href={`/admin/workouts/${workout.id}/edit`}
                        className="text-blue-600 hover:text-blue-800 text-sm"
                      >
                        Edit
                      </Link>
                      <button
                        onClick={() => handleDuplicate(workout.id)}
                        className="text-gray-600 hover:text-gray-800 text-sm"
                      >
                        Duplicate
                      </button>
                      {workout.status === 'draft' ? (
                        <button
                          onClick={() => handlePublish(workout.id)}
                          className="text-green-600 hover:text-green-800 text-sm"
                        >
                          Publish
                        </button>
                      ) : (
                        <button
                          onClick={() => handleUnpublish(workout.id)}
                          className="text-yellow-600 hover:text-yellow-800 text-sm"
                        >
                          Unpublish
                        </button>
                      )}
                      <button
                        onClick={() => handleDelete(workout.id)}
                        className="text-red-600 hover:text-red-800 text-sm"
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
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
                onClick={() => fetchWorkouts(pagination.page - 1)}
                disabled={pagination.page === 1}
                className="px-3 py-1 border rounded text-sm disabled:opacity-50"
              >
                Previous
              </button>
              <button
                onClick={() => fetchWorkouts(pagination.page + 1)}
                disabled={pagination.page === pagination.totalPages}
                className="px-3 py-1 border rounded text-sm disabled:opacity-50"
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
