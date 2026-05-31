'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import { LocalizedText } from '@/lib/types/localized';
import { Button, Badge } from '@/components/ui';
import { ConfirmDialog, FilterBar, PageHeader, Pagination, StatusBadge, type PaginationMeta } from '@/components/common';
import { Plus, Star } from 'lucide-react';

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

export default function WorkoutsListPage() {
  const [workouts, setWorkouts] = useState<Workout[]>([]);
  const [pagination, setPagination] = useState<PaginationMeta | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [featuredFilter, setFeaturedFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [deleteWorkoutId, setDeleteWorkoutId] = useState<string | null>(null);

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
    const timer = window.setTimeout(() => {
      fetchWorkouts(1);
    }, 300);

    return () => window.clearTimeout(timer);
  }, [statusFilter, featuredFilter, searchQuery]);

  const handlePublish = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}/publish`, { method: 'POST' });
      const data = await res.json();
      if (data.success) {
        toast.success('Workout published');
        fetchWorkouts(pagination?.page || 1);
      } else {
        toast.error(data.errors?.join('\n') || data.error || 'Failed to publish');
      }
    } catch (error) {
      console.error('Error publishing:', error);
    }
  };

  const handleUnpublish = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}/publish`, { method: 'DELETE' });
      if (res.ok) {
        toast.success('Workout unpublished');
        fetchWorkouts(pagination?.page || 1);
      }
    } catch (error) {
      console.error('Error unpublishing:', error);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}`, { method: 'DELETE' });
      if (res.ok) {
        toast.success('Workout deleted');
        setDeleteWorkoutId(null);
        fetchWorkouts(pagination?.page || 1);
      }
    } catch (error) {
      console.error('Error deleting:', error);
    }
  };

  const handleDuplicate = async (id: string) => {
    try {
      const res = await fetch(`/api/workouts/${id}/duplicate`, { method: 'POST' });
      if (res.ok) {
        toast.success('Workout duplicated');
        fetchWorkouts(pagination?.page || 1);
      }
    } catch (error) {
      console.error('Error duplicating:', error);
    }
  };

  const difficultyLabel = (d: string) =>
    d === 'beginner' ? 'Beginner' : d === 'intermediate' ? 'Intermediate' : 'Advanced';

  return (
    <div className="space-y-6">
      <PageHeader
        title="Workouts"
        description="Create and manage workout templates for programs and Explore."
        actions={
          <Button asChild>
            <Link href="/admin/workouts/new">
              <Plus className="size-4" aria-hidden />
              New workout
            </Link>
          </Button>
        }
      />

      <FilterBar
        searchValue={searchQuery}
        searchPlaceholder="Name (EN or AR)..."
        onSearchChange={setSearchQuery}
        selects={[
          {
            id: 'status',
            value: statusFilter,
            onChange: setStatusFilter,
            options: [
              { value: '', label: 'All statuses' },
              { value: 'draft', label: 'Draft' },
              { value: 'published', label: 'Published' },
            ],
          },
          {
            id: 'featured',
            value: featuredFilter,
            onChange: setFeaturedFilter,
            options: [
              { value: '', label: 'All' },
              { value: 'true', label: 'Featured only' },
              { value: 'false', label: 'Not featured' },
            ],
          },
        ]}
        onReset={() => {
          setSearchQuery('');
          setStatusFilter('');
          setFeaturedFilter('');
        }}
      />

      <section className="overflow-hidden rounded-xl border bg-card shadow-sm">
        {loading ? (
          <div className="py-20 text-center text-sm text-muted-foreground">Loading workouts...</div>
        ) : workouts.length === 0 ? (
          <div className="py-16 text-center">
            <p className="text-sm text-muted-foreground">No workouts match your filters.</p>
            <Link href="/admin/workouts/new" className="mt-3 inline-block text-sm font-medium text-primary hover:underline">
              Create a workout
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-5 py-3 font-medium text-muted-foreground">Workout</th>
                  <th className="px-5 py-3 font-medium text-muted-foreground">Difficulty</th>
                  <th className="hidden px-5 py-3 font-medium text-muted-foreground sm:table-cell">Exercises</th>
                  <th className="px-5 py-3 font-medium text-muted-foreground">Featured</th>
                  <th className="px-5 py-3 font-medium text-muted-foreground">Status</th>
                  <th className="px-5 py-3 text-right font-medium text-muted-foreground">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {workouts.map((workout) => (
                  <tr key={workout.id} className="transition-colors hover:bg-muted/50">
                    <td className="max-w-[240px] px-5 py-4 align-top">
                      <p className="truncate font-medium">{workout.name.en}</p>
                      <p className="truncate text-muted-foreground" dir="rtl">
                        {workout.name.ar}
                      </p>
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 align-middle">
                      <Badge variant="outline" className="font-normal capitalize">
                        {difficultyLabel(workout.difficulty)}
                      </Badge>
                    </td>
                    <td className="hidden whitespace-nowrap px-5 py-4 align-middle text-muted-foreground sm:table-cell">
                      {workout._count.exercises}
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 align-middle">
                      {workout.isFeatured ? (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-medium text-amber-800 ring-1 ring-inset ring-amber-200/80">
                          <Star className="h-3.5 w-3.5 fill-amber-400 text-amber-500" strokeWidth={0} />
                          Featured
                        </span>
                      ) : (
                        <span className="text-xs text-muted-foreground">-</span>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-5 py-4 align-middle">
                      <StatusBadge status={workout.status} />
                    </td>
                    <td className="px-5 py-4 align-middle">
                      <div className="flex flex-wrap items-center justify-end gap-x-1 gap-y-1 text-xs">
                        <Link href={`/admin/workouts/${workout.id}/edit`} className="rounded-md px-2 py-1 font-medium text-primary hover:bg-accent">
                          Edit
                        </Link>
                        <span className="text-border">|</span>
                        <button type="button" onClick={() => handleDuplicate(workout.id)} className="rounded-md px-2 py-1 font-medium text-muted-foreground hover:bg-accent hover:text-foreground">
                          Duplicate
                        </button>
                        <span className="text-border">|</span>
                        {workout.status === 'draft' ? (
                          <button type="button" onClick={() => handlePublish(workout.id)} className="rounded-md px-2 py-1 font-medium text-success hover:bg-accent">
                            Publish
                          </button>
                        ) : (
                          <button type="button" onClick={() => handleUnpublish(workout.id)} className="rounded-md px-2 py-1 font-medium text-warning hover:bg-accent">
                            Unpublish
                          </button>
                        )}
                        <span className="text-border">|</span>
                        <button type="button" onClick={() => setDeleteWorkoutId(workout.id)} className="rounded-md px-2 py-1 font-medium text-destructive hover:bg-accent">
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

        <Pagination pagination={pagination} onPageChange={fetchWorkouts} disabled={loading} />
      </section>

      <ConfirmDialog
        open={!!deleteWorkoutId}
        onOpenChange={(open) => !open && setDeleteWorkoutId(null)}
        title="Delete workout?"
        description="This action cannot be undone."
        confirmLabel="Delete"
        destructive
        onConfirm={() => {
          if (deleteWorkoutId) return handleDelete(deleteWorkoutId);
        }}
      />
    </div>
  );
}
