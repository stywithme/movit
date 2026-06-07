'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Map, Plus } from 'lucide-react';
import { toast } from 'sonner';
import { LocalizedText } from '@/lib/types/localized';
import { Badge, Button } from '@/components/ui';
import {
  ConfirmDialog,
  DataTable,
  FilterBar,
  PageHeader,
  Pagination as TablePagination,
  StatusBadge,
  type DataTableColumn,
} from '@/components/common';
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

type ConfirmAction = { type: 'delete'; id: string; label: string } | null;

export default function ProgramsListPage() {
  const [programs, setPrograms] = useState<Program[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('');
  const [readinessFilter, setReadinessFilter] = useState<string>('');
  const [searchQuery, setSearchQuery] = useState('');
  const [confirmAction, setConfirmAction] = useState<ConfirmAction>(null);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchPrograms = async (page = 1) => {
    setLoading(true);
    setPageError(null);
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
      } else {
        const message = data.error || 'Failed to load programs';
        setPageError(message);
        toast.error(message);
      }
    } catch (error) {
      console.error('Error fetching programs:', error);
      setPageError('Failed to load programs');
      toast.error('Failed to load programs');
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

  const handleConfirmDelete = async () => {
    if (!confirmAction) return;

    setActionLoading(true);
    try {
      const res = await fetch(`/api/programs/${confirmAction.id}`, { method: 'DELETE' });
      const data = await res.json().catch(() => null);
      if (res.ok && data?.success) {
        toast.success('Program deleted');
        setConfirmAction(null);
        fetchPrograms(pagination?.page || 1);
      } else {
        toast.error(data?.error || 'Failed to delete program');
      }
    } catch (error) {
      console.error('Error deleting:', error);
      toast.error('Failed to delete program');
    } finally {
      setActionLoading(false);
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

  const columns: DataTableColumn<Program>[] = [
    {
      key: 'program',
      header: 'Program',
      cell: (program) => (
        <div className="min-w-[220px]">
          <p className="font-medium">{program.name.en}</p>
          <p className="text-sm text-muted-foreground" dir="rtl">
            {program.name.ar}
          </p>
        </div>
      ),
    },
    {
      key: 'levelRange',
      header: 'Level range',
      cell: (program) => <span className="text-muted-foreground">{formatProgramLevelRange(program)}</span>,
    },
    {
      key: 'version',
      header: 'Version',
      cell: (program) => <Badge variant="outline">v{program.version ?? 1}</Badge>,
    },
    {
      key: 'readiness',
      header: 'Readiness',
      cell: (program) => {
        const readiness = program.autoAssignmentReadiness ?? getAutoAssignmentReadiness(program);
        const variant =
          readiness.status === 'ready'
            ? 'success'
            : readiness.status === 'incomplete'
              ? 'warning'
              : 'secondary';

        return (
          <div className="space-y-1">
            <Badge variant={variant} className="capitalize">
              {readiness.status.replace(/_/g, ' ')}
            </Badge>
            {readiness.status === 'incomplete' && readiness.missingFields.length > 0 ? (
              <p className="max-w-[220px] text-xs text-muted-foreground">
                Missing: {readiness.missingFields.join(', ')}
              </p>
            ) : null}
          </div>
        );
      },
    },
    {
      key: 'enrollments',
      header: 'Enrollments',
      cell: (program) =>
        typeof program.activeEnrollmentCount === 'number' && program.activeEnrollmentCount > 0 ? (
          <Badge variant="primary">{program.activeEnrollmentCount} active</Badge>
        ) : (
          <span className="text-xs text-muted-foreground">0</span>
        ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (program) => <StatusBadge status={program.isPublished ? 'published' : 'draft'} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (program) => (
        <div className="flex flex-wrap items-center justify-end gap-1">
          <Button asChild variant="ghost" size="sm">
            <Link href={`/admin/programs/${program.id}/edit`}>Edit</Link>
          </Button>
          <Button asChild variant="ghost" size="sm">
            <Link href={`/admin/programs/map?programId=${program.id}`}>Map</Link>
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => handleDuplicate(program.id)}>
            Duplicate
          </Button>
          {program.isPublished ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => handleUnpublish(program.id)}>
              Unpublish
            </Button>
          ) : (
            <Button type="button" variant="ghost" size="sm" onClick={() => handlePublish(program.id)}>
              Publish
            </Button>
          )}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive hover:text-destructive"
            onClick={() => setConfirmAction({ type: 'delete', id: program.id, label: program.name.en })}
          >
            Delete
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Programs"
        description="Manage training programs, publishing status, and auto-assignment readiness."
        actions={
          <>
            <Button asChild variant="secondary">
              <Link href="/admin/programs/new?programDomain=TRAINING">New Training</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href="/admin/programs/new?programDomain=MOBILITY">New Mobility</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link href="/admin/programs/new?programDomain=THERAPEUTIC">New Therapeutic</Link>
            </Button>
            <Button asChild variant="outline">
              <Link href="/admin/programs/map">
                <Map className="size-4" />
                Open Map
              </Link>
            </Button>
            <Button asChild>
              <Link href="/admin/programs/new">
                <Plus className="size-4" />
                New Program
              </Link>
            </Button>
          </>
        }
      />

      <FilterBar
        searchValue={searchQuery}
        searchPlaceholder="Search programs..."
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
            id: 'readiness',
            value: readinessFilter,
            onChange: setReadinessFilter,
            options: [
              { value: '', label: 'All readiness' },
              { value: 'ready', label: 'Ready' },
              { value: 'incomplete', label: 'Incomplete' },
              { value: 'manual_only', label: 'Manual only' },
            ],
          },
        ]}
        onReset={resetFilters}
      />

      <DataTable
        columns={columns}
        data={programs}
        getRowKey={(program) => program.id}
        loading={loading}
        error={pageError}
        emptyTitle="No programs found"
        emptyDescription="Create your first program or adjust the current filters."
        footer={<TablePagination pagination={pagination} onPageChange={fetchPrograms} disabled={loading} />}
      />

      <ConfirmDialog
        open={!!confirmAction}
        onOpenChange={(open) => !open && setConfirmAction(null)}
        title="Delete program?"
        description={`This will permanently delete "${confirmAction?.label || 'this program'}".`}
        confirmLabel="Delete"
        destructive
        loading={actionLoading}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
