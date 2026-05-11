'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { LocalizedText } from '@/lib/types/localized';
import { Input, Select, Button, Badge } from '@/components/ui';
import { buttonVariants } from '@/components/ui/Button';
import { cn } from '@/lib/utils';
import { Plus, Search, Star } from 'lucide-react';

interface Workout {
  id: string;
  name: LocalizedText;
  description: LocalizedText | null;
  slug: string;
  difficulty: 'beginner' | 'intermediate' | 'advanced';
  status: string;
  isFeatured: boolean;
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
  const [featuredFilter, setFeaturedFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');

  const fetchWorkouts = async (page = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page.toString());
      if (statusFilter) params.set('status', statusFilter);
      if (featuredFilter === 'true') params.set('featured', 'true');
      if (featuredFilter === 'false') params.set('featured', 'false');
      if (searchQuery.trim()) params.set('search', searchQuery.trim());

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
  }, [statusFilter, featuredFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchWorkouts(1);
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

  const difficultyLabel = (d: string) =>
    d === 'beginner' ? 'Beginner' : d === 'intermediate' ? 'Intermediate' : 'Advanced';

  return (
    <div className="mx-auto max-w-6xl space-y-8 pb-12">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-zinc-900">Workouts</h1>
          <p className="mt-1 text-sm text-zinc-500">Create and manage workout templates for programs and Explore.</p>
        </div>
        <Link
          href="/admin/workouts/new"
          className={cn(buttonVariants({ variant: 'primary', size: 'md' }), 'inline-flex items-center gap-2')}
        >
          <Plus className="h-4 w-4" aria-hidden />
          New workout
        </Link>
      </div>

      <section className="rounded-2xl border border-zinc-200/90 bg-white p-5 shadow-sm">
        <form onSubmit={handleSearch} className="flex flex-col gap-4 lg:flex-row lg:items-end">
          <div className="min-w-0 flex-1 space-y-1.5">
            <label htmlFor="workout-search" className="text-xs font-medium uppercase tracking-wide text-zinc-500">
              Search
            </label>
            <div className="flex gap-2">
              <div className="relative min-w-0 flex-1">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
                <Input
                  id="workout-search"
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Name (EN or AR)…"
                  className="pl-9"
                />
              </div>
              <Button type="submit" variant="secondary" size="md">
                Apply
              </Button>
            </div>
          </div>
          <div className="grid w-full gap-4 sm:grid-cols-2 lg:w-auto lg:min-w-[280px] lg:grid-cols-2">
            <div className="space-y-1.5">
              <span className="text-xs font-medium uppercase tracking-wide text-zinc-500">Status</span>
              <Select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                options={[
                  { value: '', label: 'All statuses' },
                  { value: 'draft', label: 'Draft' },
                  { value: 'published', label: 'Published' },
                ]}
              />
            </div>
            <div className="space-y-1.5">
              <span className="text-xs font-medium uppercase tracking-wide text-zinc-500">Featured</span>
              <Select
                value={featuredFilter}
                onChange={(e) => setFeaturedFilter(e.target.value)}
                options={[
                  { value: '', label: 'All' },
                  { value: 'true', label: 'Featured only' },
                  { value: 'false', label: 'Not featured' },
                ]}
              />
            </div>
          </div>
        </form>
      </section>

      <section className="overflow-hidden rounded-2xl border border-zinc-200/90 bg-white shadow-sm">
        {loading ? (
          <div className="py-20 text-center text-sm text-zinc-500">Loading workouts…</div>
        ) : workouts.length === 0 ? (
          <div className="py-16 text-center">
            <p className="text-sm text-zinc-600">No workouts match your filters.</p>
            <Link href="/admin/workouts/new" className="mt-3 inline-block text-sm font-medium text-blue-600 hover:text-blue-700">
              Create a workout
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="border-b border-zinc-100 bg-zinc-50/80">
                  <th className="px-5 py-3 font-medium text-zinc-600">Workout</th>
                  <th className="px-5 py-3 font-medium text-zinc-600">Difficulty</th>
                  <th className="hidden px-5 py-3 font-medium text-zinc-600 sm:table-cell">Exercises</th>
                  <th className="px-5 py-3 font-medium text-zinc-600">Featured</th>
                  <th className="px-5 py-3 font-medium text-zinc-600">Status</th>
                  <th className="px-5 py-3 text-right font-medium text-zinc-600">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100">
                {workouts.map((workout) => (
                  <tr key={workout.id} className="transition-colors hover:bg-zinc-50/60">
                    <td className="max-w-[240px] px-5 py-4 align-top">
                      <p className="truncate font-medium text-zinc-900">{workout.name.en}</p>
                      <p className="truncate text-zinc-500" dir="rtl">
                        {workout.name.ar}
                      </p>
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 align-middle">
                      <Badge variant="outline" className="font-normal capitalize">
                        {difficultyLabel(workout.difficulty)}
                      </Badge>
                    </td>
                    <td className="hidden whitespace-nowrap px-5 py-4 align-middle text-zinc-600 sm:table-cell">
                      {workout._count.exercises}
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 align-middle">
                      {workout.isFeatured ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-inset ring-amber-200/80">
                          <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-500" strokeWidth={0} />
                          Featured
                        </span>
                      ) : (
                        <span className="text-xs text-zinc-400">—</span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 align-middle">
                      <Badge variant={workout.status === 'published' ? 'success' : 'warning'} className="capitalize">
                        {workout.status}
                      </Badge>
                    </td>
                    <td className="px-5 py-4 align-middle">
                      <div className="flex flex-wrap items-center justify-end gap-x-1 gap-y-1 text-xs">
                        <Link href={`/admin/workouts/${workout.id}/edit`} className="rounded-md px-2 py-1 font-medium text-blue-600 hover:bg-blue-50">
                          Edit
                        </Link>
                        <span className="text-zinc-200">|</span>
                        <button type="button" onClick={() => handleDuplicate(workout.id)} className="rounded-md px-2 py-1 font-medium text-zinc-600 hover:bg-zinc-100">
                          Duplicate
                        </button>
                        <span className="text-zinc-200">|</span>
                        {workout.status === 'draft' ? (
                          <button type="button" onClick={() => handlePublish(workout.id)} className="rounded-md px-2 py-1 font-medium text-emerald-600 hover:bg-emerald-50">
                            Publish
                          </button>
                        ) : (
                          <button type="button" onClick={() => handleUnpublish(workout.id)} className="rounded-md px-2 py-1 font-medium text-amber-700 hover:bg-amber-50">
                            Unpublish
                          </button>
                        )}
                        <span className="text-zinc-200">|</span>
                        <button type="button" onClick={() => handleDelete(workout.id)} className="rounded-md px-2 py-1 font-medium text-red-600 hover:bg-red-50">
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {pagination && pagination.totalPages > 1 && (
          <footer className="flex flex-col gap-3 border-t border-zinc-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-zinc-500">
              {(pagination.page - 1) * pagination.limit + 1}–{Math.min(pagination.page * pagination.limit, pagination.total)} of{' '}
              {pagination.total}
            </p>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={pagination.page === 1}
                onClick={() => fetchWorkouts(pagination.page - 1)}
              >
                Previous
              </Button>
              <Button
                type="button"
                variant="outline"
                size="sm"
                disabled={pagination.page === pagination.totalPages}
                onClick={() => fetchWorkouts(pagination.page + 1)}
              >
                Next
              </Button>
            </div>
          </footer>
        )}
      </section>
    </div>
  );
}
