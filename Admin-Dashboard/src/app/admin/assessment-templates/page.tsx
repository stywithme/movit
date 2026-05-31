'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Plus } from 'lucide-react';
import type { LocalizedText } from '@/lib/types/localized';
import { Badge, Button } from '@/components/ui';
import {
  ConfirmDialog,
  DataTable,
  FilterBar,
  PageHeader,
  StatusBadge,
  type DataTableColumn,
} from '@/components/common';
import {
  ASSESSMENT_TEMPLATE_TYPE_FILTER_OPTIONS,
  ASSESSMENT_TEMPLATE_TYPE_BADGE_VARIANT,
  ASSESSMENT_TEMPLATE_TYPE_SHORT_LABEL,
} from './assessment-template-types';

interface AssessmentTemplate {
  id: string;
  name: LocalizedText;
  type: string;
  targetLevel?: { id: string; name: LocalizedText; levelNumber: number } | null;
  isDefault: boolean;
  domainWeights: {
    mobility: number;
    control: number;
    symmetry: number;
    safety: number;
  } | null;
  status: 'draft' | 'published';
  _count?: { exercises: number };
  createdAt: string;
}

const STATUS_OPTIONS = [
  { value: '', label: 'All' },
  { value: 'draft', label: 'Draft' },
  { value: 'published', label: 'Published' },
];

function DomainWeightsBar({ weights }: { weights: { mobility: number; control: number; symmetry: number; safety: number } }) {
  const total = weights.mobility + weights.control + weights.symmetry + weights.safety;
  const items = [
    { key: 'mobility', label: 'M', value: weights.mobility, color: 'bg-primary' },
    { key: 'control', label: 'C', value: weights.control, color: 'bg-success' },
    { key: 'symmetry', label: 'S', value: weights.symmetry, color: 'bg-purple-500' },
    { key: 'safety', label: 'Sa', value: weights.safety, color: 'bg-orange-500' },
  ];

  return (
    <div
      title={`Mobility: ${weights.mobility}, Control: ${weights.control}, Symmetry: ${weights.symmetry}, Safety: ${weights.safety}`}
    >
      <div className="flex h-2.5 w-28 overflow-hidden rounded-full bg-muted">
        {items.map((item) =>
          total > 0 ? (
            <div
              key={item.key}
              className={`${item.color} transition-all`}
              style={{ width: `${(item.value / total) * 100}%` }}
            />
          ) : null
        )}
      </div>
      <div className="flex gap-1.5 mt-1">
        {items.map((item) => (
          <span key={item.key} className="text-[10px] text-muted-foreground">
            {item.label}:{item.value}
          </span>
        ))}
      </div>
    </div>
  );
}

export default function AssessmentTemplatesPage() {
  const [templates, setTemplates] = useState<AssessmentTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [typeFilter, setTypeFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [deletingTemplate, setDeletingTemplate] = useState<AssessmentTemplate | null>(null);

  const fetchTemplates = async () => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (typeFilter) params.set('type', typeFilter);
      if (statusFilter) params.set('status', statusFilter);

      const res = await fetch(`/api/admin/assessment-templates?${params}`);
      const data = await res.json();
      if (data.success) {
        setTemplates(data.data);
      }
    } catch (error) {
      console.error('Error fetching templates:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTemplates();
  }, [typeFilter, statusFilter]);

  const handlePublish = async (id: string) => {
    try {
      const res = await fetch(`/api/admin/assessment-templates/${id}/publish`, { method: 'POST' });
      if (res.ok) fetchTemplates();
    } catch (error) {
      console.error('Error publishing:', error);
    }
  };

  const handleUnpublish = async (id: string) => {
    try {
      const res = await fetch(`/api/admin/assessment-templates/${id}/publish`, { method: 'DELETE' });
      if (res.ok) fetchTemplates();
    } catch (error) {
      console.error('Error unpublishing:', error);
    }
  };

  const handleDelete = async () => {
    if (!deletingTemplate) return;
    try {
      const res = await fetch(`/api/admin/assessment-templates/${deletingTemplate.id}`, { method: 'DELETE' });
      if (res.ok) {
        setDeletingTemplate(null);
        fetchTemplates();
      }
    } catch (error) {
      console.error('Error deleting:', error);
    }
  };

  const columns: DataTableColumn<AssessmentTemplate>[] = [
    {
      key: 'name',
      header: 'Name',
      cell: (template) => (
        <div className="min-w-[240px]">
          <p className="font-medium">{template.name.en}</p>
          <p className="text-sm text-muted-foreground" dir="rtl">
            {template.name.ar}
          </p>
          {template.isDefault && (
            <Badge variant="primary" size="sm" className="mt-1">
              Default
            </Badge>
          )}
        </div>
      ),
    },
    {
      key: 'type',
      header: 'Type',
      cell: (template) => (
        <Badge variant={ASSESSMENT_TEMPLATE_TYPE_BADGE_VARIANT[template.type] || 'secondary'}>
          {ASSESSMENT_TEMPLATE_TYPE_SHORT_LABEL[template.type] || template.type}
        </Badge>
      ),
    },
    {
      key: 'targetLevel',
      header: 'Target Level',
      cell: (template) => (
        <span className="text-sm text-muted-foreground">
          {template.targetLevel ? `L${template.targetLevel.levelNumber} - ${template.targetLevel.name.en}` : '-'}
        </span>
      ),
    },
    {
      key: 'exercises',
      header: 'Exercises',
      cell: (template) => <span className="font-medium text-muted-foreground">{template._count?.exercises ?? 0}</span>,
    },
    {
      key: 'weights',
      header: 'Domain Weights',
      cell: (template) => (
        <DomainWeightsBar weights={template.domainWeights || { mobility: 0, control: 0, symmetry: 0, safety: 0 }} />
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (template) => <StatusBadge status={template.status} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (template) => (
        <div className="flex justify-end gap-1">
          <Button asChild variant="ghost" size="sm">
            <Link href={`/admin/assessment-templates/${template.id}/edit`}>Edit</Link>
          </Button>
          {template.status === 'published' ? (
            <Button type="button" variant="ghost" size="sm" onClick={() => handleUnpublish(template.id)}>
              Unpublish
            </Button>
          ) : (
            <Button type="button" variant="ghost" size="sm" onClick={() => handlePublish(template.id)}>
              Publish
            </Button>
          )}
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive hover:text-destructive"
            onClick={() => setDeletingTemplate(template)}
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
        title="Assessment Templates"
        description="Manage assessment templates for evaluations."
        actions={
          <Button asChild>
            <Link href="/admin/assessment-templates/new">
              <Plus className="size-4" />
              New Template
            </Link>
          </Button>
        }
      />

      <FilterBar
        selects={[
          {
            id: 'type',
            value: typeFilter,
            onChange: setTypeFilter,
            options: ASSESSMENT_TEMPLATE_TYPE_FILTER_OPTIONS,
            className: 'lg:w-56',
          },
          {
            id: 'status',
            value: statusFilter,
            onChange: setStatusFilter,
            options: STATUS_OPTIONS,
          },
        ]}
        onReset={() => {
          setTypeFilter('');
          setStatusFilter('');
        }}
      />

      <DataTable
        columns={columns}
        data={templates}
        getRowKey={(template) => template.id}
        loading={loading}
        emptyTitle="No assessment templates found"
        emptyDescription="Create your first template or adjust the current filters."
      />

      <ConfirmDialog
        open={!!deletingTemplate}
        onOpenChange={(open) => !open && setDeletingTemplate(null)}
        title="Delete assessment template?"
        description={
          deletingTemplate
            ? `This will permanently delete "${deletingTemplate.name.en}".`
            : 'This action cannot be undone.'
        }
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </div>
  );
}
