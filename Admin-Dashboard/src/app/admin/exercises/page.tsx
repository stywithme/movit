'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import Image from 'next/image';
import Link from 'next/link';
import { Plus } from 'lucide-react';
import { toast } from 'sonner';
import { Checkbox } from '@/components/ui/Checkbox';
import { Button } from '@/components/ui/Button';
import { Badge } from '@/components/ui/Badge';
import {
  ConfirmDialog,
  DataTable,
  FilterBar,
  PageHeader,
  Pagination,
  StatusBadge,
  type DataTableColumn,
  type PaginationMeta,
} from '@/components/common';
import { LocalizedText } from '@/lib/types/localized';
import { ApiError } from '@/lib/api/client';
import { exercisesService } from '@/modules/exercises/exercises.service';

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

type ConfirmAction =
  | { type: 'delete'; id: string; label: string }
  | { type: 'bulk-delete' }
  | { type: 'bulk-unpublish' }
  | null;

export default function ExercisesListPage() {
  const [exercises, setExercises] = useState<Exercise[]>([]);
  const [pagination, setPagination] = useState<PaginationMeta | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkLoading, setBulkLoading] = useState(false);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction>(null);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedSearch(searchQuery.trim()), 300);
    return () => window.clearTimeout(timer);
  }, [searchQuery]);

  const fetchExercises = useCallback(
    async (page = 1) => {
      setLoading(true);
      setPageError(null);
      try {
        const result = await exercisesService.list({
          page,
          status: statusFilter,
          search: debouncedSearch,
        });

        setExercises(result.data as Exercise[]);
        setPagination(result.pagination || null);
      } catch (error) {
        const message = error instanceof ApiError ? error.message : 'Unable to load exercises';
        setPageError(message);
        toast.error(message);
      } finally {
        setLoading(false);
      }
    },
    [statusFilter, debouncedSearch],
  );

  useEffect(() => {
    fetchExercises();
  }, [fetchExercises]);

  const pageIds = useMemo(() => exercises.map((exercise) => exercise.id), [exercises]);
  const allOnPageSelected = pageIds.length > 0 && pageIds.every((id) => selectedIds.has(id));

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const toggleSelectAllOnPage = () => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allOnPageSelected) {
        pageIds.forEach((id) => next.delete(id));
      } else {
        pageIds.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  const refreshCurrentPage = () => fetchExercises(pagination?.page || 1);

  const handlePublish = async (exercise: Exercise) => {
    try {
      await exercisesService.publish(exercise.id);
      toast.success('Exercise published');
      refreshCurrentPage();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Unable to publish exercise');
    }
  };

  const handleUnpublish = async (exercise: Exercise) => {
    try {
      await exercisesService.unpublish(exercise.id);
      toast.success('Exercise unpublished');
      refreshCurrentPage();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Unable to unpublish exercise');
    }
  };

  const handleConfirm = async () => {
    if (!confirmAction) return;

    setBulkLoading(true);
    try {
      if (confirmAction.type === 'delete') {
        await exercisesService.delete(confirmAction.id);
        toast.success('Exercise deleted');
      }

      if (confirmAction.type === 'bulk-delete') {
        await exercisesService.bulkDelete(Array.from(selectedIds));
        setSelectedIds(new Set());
        toast.success('Selected exercises deleted');
      }

      if (confirmAction.type === 'bulk-unpublish') {
        await exercisesService.bulkUnpublish(Array.from(selectedIds));
        setSelectedIds(new Set());
        toast.success('Selected exercises unpublished');
      }

      setConfirmAction(null);
      refreshCurrentPage();
    } catch (error) {
      toast.error(error instanceof ApiError ? error.message : 'Action failed');
    } finally {
      setBulkLoading(false);
    }
  };

  const columns: DataTableColumn<Exercise>[] = [
    {
      key: 'select',
      header: (
        <Checkbox
          checked={allOnPageSelected}
          onCheckedChange={toggleSelectAllOnPage}
          aria-label="Select all on this page"
        />
      ),
      cell: (exercise) => (
        <Checkbox
          checked={selectedIds.has(exercise.id)}
          onCheckedChange={() => toggleSelect(exercise.id)}
          aria-label={`Select ${exercise.name.en}`}
        />
      ),
      className: 'w-10',
      headerClassName: 'w-10',
    },
    {
      key: 'exercise',
      header: 'Exercise',
      cell: (exercise) => (
        <div className="flex min-w-[260px] items-center gap-3">
          {exercise.media[0]?.url ? (
            <Image
              src={exercise.media[0].url}
              alt=""
              width={40}
              height={40}
              className="size-10 rounded-md object-cover"
            />
          ) : (
            <div className="flex size-10 items-center justify-center rounded-md bg-muted text-xs text-muted-foreground">
              IMG
            </div>
          )}
          <div className="min-w-0">
            <p className="truncate font-medium">{exercise.name.en}</p>
            <p className="truncate text-sm text-muted-foreground" dir="rtl">
              {exercise.name.ar}
            </p>
          </div>
        </div>
      ),
    },
    {
      key: 'category',
      header: 'Category',
      cell: (exercise) => <span className="text-muted-foreground">{exercise.category?.name?.en || '-'}</span>,
    },
    {
      key: 'method',
      header: 'Method',
      cell: (exercise) => (
        <Badge variant="outline" className="capitalize">
          {exercise.countingMethod.code.replace(/_/g, ' ')}
        </Badge>
      ),
    },
    {
      key: 'variants',
      header: 'Variants',
      cell: (exercise) => <span className="text-muted-foreground">{exercise._count.poseVariants}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (exercise) => <StatusBadge status={exercise.status} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (exercise) => (
        <div className="flex flex-wrap items-center justify-end gap-1">
          <Button asChild variant="ghost" size="sm">
            <Link href={`/admin/exercises/${exercise.id}/edit`}>Edit</Link>
          </Button>
          {exercise.status === 'draft' ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => handlePublish(exercise)}>
              Publish
            </Button>
          ) : (
            <Button type="button" variant="ghost" size="sm" onClick={() => handleUnpublish(exercise)}>
              Unpublish
            </Button>
          )}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive hover:text-destructive"
            onClick={() => setConfirmAction({ type: 'delete', id: exercise.id, label: exercise.name.en })}
          >
            Delete
          </Button>
        </div>
      ),
    },
  ];

  const confirmCopy = {
    title:
      confirmAction?.type === 'delete'
        ? 'Delete exercise?'
        : confirmAction?.type === 'bulk-delete'
          ? 'Delete selected exercises?'
          : 'Unpublish selected exercises?',
    description:
      confirmAction?.type === 'delete'
        ? `This will permanently delete "${confirmAction.label}".`
        : confirmAction?.type === 'bulk-delete'
          ? `This will delete ${selectedIds.size} selected exercise(s).`
          : `This will return ${selectedIds.size} selected exercise(s) to draft status.`,
    confirmLabel: confirmAction?.type?.includes('delete') ? 'Delete' : 'Unpublish',
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Exercises"
        description="Manage training exercises, publishing state, and pose variants."
        actions={
          <Button asChild>
            <Link href="/admin/exercises/new">
              <Plus className="size-4" />
              New Exercise
            </Link>
          </Button>
        }
      />

      <FilterBar
        searchValue={searchQuery}
        searchPlaceholder="Name, slug, category, muscles..."
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
        ]}
        onReset={() => {
          setSearchQuery('');
          setStatusFilter('');
        }}
      />

      {selectedIds.size > 0 && (
        <div className="flex flex-wrap items-center gap-3 rounded-xl border bg-card p-4 shadow-sm">
          <span className="text-sm font-medium">{selectedIds.size} exercise(s) selected</span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={bulkLoading}
            onClick={() => setConfirmAction({ type: 'bulk-unpublish' })}
          >
            Unpublish
          </Button>
          <Button
            type="button"
            variant="danger"
            size="sm"
            disabled={bulkLoading}
            onClick={() => setConfirmAction({ type: 'bulk-delete' })}
          >
            Delete
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="ml-auto"
            disabled={bulkLoading}
            onClick={() => setSelectedIds(new Set())}
          >
            Clear selection
          </Button>
        </div>
      )}

      <DataTable
        columns={columns}
        data={exercises}
        getRowKey={(exercise) => exercise.id}
        loading={loading}
        error={pageError}
        emptyTitle="No exercises found"
        emptyDescription="Create your first exercise or adjust the current filters."
        footer={<Pagination pagination={pagination} onPageChange={fetchExercises} disabled={loading} />}
      />

      <ConfirmDialog
        open={!!confirmAction}
        onOpenChange={(open) => !open && setConfirmAction(null)}
        title={confirmCopy.title}
        description={confirmCopy.description}
        confirmLabel={confirmCopy.confirmLabel}
        destructive={confirmAction?.type?.includes('delete')}
        loading={bulkLoading}
        onConfirm={handleConfirm}
      />
    </div>
  );
}
