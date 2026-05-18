'use client';

import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Select } from '@/components/ui';

interface Exercise {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  status: string;
  createdAt: string;
  updatedAt: string;
  category: {
    code: string;
    name: LocalizedText;
  };
  countingMethod: {
    code: string;
    name: LocalizedText;
  };
  media: { url: string }[];
  _count: {
    poseVariants: number;
  };
}

interface Pagination {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export default function ExercisesListPage() {
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(searchQuery), 400);
    return () => window.clearTimeout(t);
  }, [searchQuery]);

  const fetchExercises = useCallback(
    async (page = 1) => {
      setLoading(true);
      try {
        const params = new URLSearchParams();
        params.set('page', page.toString());
        if (statusFilter) params.set('status', statusFilter);
        if (debouncedSearch.trim()) params.set('search', debouncedSearch.trim());

        const res = await fetch(`/api/exercises?${params}`);
        const data = await res.json();

        if (data.success) {
          setExercises(data.data);
          setPagination(data.pagination);
        }
      } catch (error) {
        console.error('Error fetching exercises:', error);
      } finally {
        setLoading(false);
      }
    },
    [statusFilter, debouncedSearch],
  );

  useEffect(() => {
    fetchExercises();
  }, [fetchExercises]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setDebouncedSearch(searchQuery.trim());
  };

  const handlePublish = async (id: string) => {
    try {
      const res = await fetch(`/api/exercises/${id}/publish`, { method: 'PUT' });
      if (res.ok) fetchExercises(pagination?.page || 1);
    } catch (error) {
      console.error('Error publishing:', error);
    }
  };

  const handleUnpublish = async (id: string) => {
    try {
      const res = await fetch(`/api/exercises/${id}/publish`, { method: 'DELETE' });
      if (res.ok) fetchExercises(pagination?.page || 1);
    } catch (error) {
      console.error('Error unpublishing:', error);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this exercise?')) return;
    try {
      const res = await fetch(`/api/exercises/${id}`, { method: 'DELETE' });
      if (res.ok) fetchExercises(pagination?.page || 1);
    } catch (error) {
      console.error('Error deleting:', error);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Exercises</h1>
          <p className="text-gray-600 mt-1">Manage training exercises</p>
        </div>
        <Link
          href="/admin/exercises/new"
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Exercise
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
                placeholder="Name, slug, category, muscles..."
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

      {/* Exercises Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading...</div>
        ) : exercises.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <p>No exercises found.</p>
            <Link href="/admin/exercises/new" className="text-blue-600 hover:underline mt-2 inline-block">
              Create your first exercise
            </Link>
          </div>
        ) : (
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Exercise
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Category
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Method
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                  Variants
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
              {exercises.map((exercise) => (
                <tr key={exercise.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      {exercise.media[0] ? (
                        <img
                          src={exercise.media[0].url}
                          alt=""
                          className="w-10 h-10 rounded object-cover"
                        />
                      ) : (
                        <div className="w-10 h-10 bg-gray-200 rounded flex items-center justify-center">
                          <svg className="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
                          </svg>
                        </div>
                      )}
                      <div>
                        <p className="font-medium text-gray-900">{exercise.name.en}</p>
                        <p className="text-sm text-gray-500">{exercise.name.ar}</p>
                      </div>
                    </div>
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {(exercise.category.name as LocalizedText).en}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {exercise.countingMethod.code.replace('_', ' ')}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-600">
                    {exercise._count.poseVariants}
                  </td>
                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                        exercise.status === 'published'
                          ? 'bg-green-100 text-green-800'
                          : 'bg-yellow-100 text-yellow-800'
                      }`}
                    >
                      {exercise.status}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-right">
                    <div className="flex justify-end gap-2">
                      <Link
                        href={`/admin/exercises/${exercise.id}/edit`}
                        className="text-blue-600 hover:text-blue-800 text-sm"
                      >
                        Edit
                      </Link>
                      {exercise.status === 'draft' ? (
                        <button
                          onClick={() => handlePublish(exercise.id)}
                          className="text-green-600 hover:text-green-800 text-sm"
                        >
                          Publish
                        </button>
                      ) : (
                        <button
                          onClick={() => handleUnpublish(exercise.id)}
                          className="text-yellow-600 hover:text-yellow-800 text-sm"
                        >
                          Unpublish
                        </button>
                      )}
                      <button
                        onClick={() => handleDelete(exercise.id)}
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
                onClick={() => fetchExercises(pagination.page - 1)}
                disabled={pagination.page === 1}
                className="px-3 py-1.5 border border-gray-300 bg-white text-gray-800 rounded text-sm hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400 disabled:border-gray-200 disabled:cursor-not-allowed"
              >
                Previous
              </button>
              <button
                onClick={() => fetchExercises(pagination.page + 1)}
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

