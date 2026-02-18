'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import type { LocalizedText } from '@/lib/types/localized';
import { Select, Badge } from '@/components/ui';

interface AssessmentTemplate {
  id: string;
  name: LocalizedText;
  type: 'initial' | 'periodic' | 'post_program' | 'level_specific';
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

const TYPE_OPTIONS = [
  { value: '', label: 'All Types' },
  { value: 'initial', label: 'Initial' },
  { value: 'periodic', label: 'Periodic' },
  { value: 'post_program', label: 'Post Program' },
  { value: 'level_specific', label: 'Level Specific' },
];

const STATUS_OPTIONS = [
  { value: '', label: 'All' },
  { value: 'draft', label: 'Draft' },
  { value: 'published', label: 'Published' },
];

const TYPE_BADGE_VARIANT: Record<string, 'primary' | 'purple' | 'orange' | 'teal'> = {
  initial: 'primary',
  periodic: 'purple',
  post_program: 'orange',
  level_specific: 'teal',
};

const TYPE_LABEL: Record<string, string> = {
  initial: 'Initial',
  periodic: 'Periodic',
  post_program: 'Post Program',
  level_specific: 'Level Specific',
};

function DomainWeightsBar({ weights }: { weights: { mobility: number; control: number; symmetry: number; safety: number } }) {
  const total = weights.mobility + weights.control + weights.symmetry + weights.safety;
  const items = [
    { key: 'mobility', label: 'M', value: weights.mobility, color: 'bg-blue-500' },
    { key: 'control', label: 'C', value: weights.control, color: 'bg-green-500' },
    { key: 'symmetry', label: 'S', value: weights.symmetry, color: 'bg-purple-500' },
    { key: 'safety', label: 'Sa', value: weights.safety, color: 'bg-orange-500' },
  ];

  return (
    <div
      title={`Mobility: ${weights.mobility}, Control: ${weights.control}, Symmetry: ${weights.symmetry}, Safety: ${weights.safety}`}
    >
      <div className="flex w-28 h-2.5 rounded-full overflow-hidden bg-gray-100">
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
          <span key={item.key} className="text-[10px] text-gray-400">
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

  const handleDelete = async (id: string) => {
    if (!confirm('Are you sure you want to delete this assessment template?')) return;
    try {
      const res = await fetch(`/api/admin/assessment-templates/${id}`, { method: 'DELETE' });
      if (res.ok) fetchTemplates();
    } catch (error) {
      console.error('Error deleting:', error);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Assessment Templates</h1>
          <p className="text-gray-600 mt-1">Manage assessment templates for evaluations</p>
        </div>
        <Link
          href="/admin/assessment-templates/new"
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
        >
          <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
          </svg>
          New Template
        </Link>
      </div>

      {/* Filters */}
      <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
        <div className="flex flex-wrap gap-4 items-end">
          <div className="w-48">
            <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
            <Select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              options={TYPE_OPTIONS}
            />
          </div>
          <div className="w-40">
            <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={STATUS_OPTIONS}
            />
          </div>
        </div>
      </div>

      {/* Templates Table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-500">Loading...</div>
        ) : templates.length === 0 ? (
          <div className="p-8 text-center text-gray-500">
            <p>No assessment templates found.</p>
            <Link href="/admin/assessment-templates/new" className="text-blue-600 hover:underline mt-2 inline-block">
              Create your first template
            </Link>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Name</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Target Level</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Exercises</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Domain Weights</th>
                  <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {templates.map((template) => {
                  const weights = template.domainWeights || { mobility: 0, control: 0, symmetry: 0, safety: 0 };
                  return (
                    <tr key={template.id} className="hover:bg-gray-50">
                      <td className="px-6 py-4">
                        <div>
                          <p className="font-medium text-gray-900">{template.name.en}</p>
                          <p className="text-sm text-gray-500">{template.name.ar}</p>
                          {template.isDefault && (
                            <Badge variant="primary" size="sm" className="mt-1">Default</Badge>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <Badge variant={TYPE_BADGE_VARIANT[template.type] || 'default'}>
                          {TYPE_LABEL[template.type] || template.type}
                        </Badge>
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600">
                        {template.targetLevel ? (
                          <span>L{template.targetLevel.levelNumber} &mdash; {template.targetLevel.name.en}</span>
                        ) : (
                          <span className="text-gray-400">&mdash;</span>
                        )}
                      </td>
                      <td className="px-6 py-4 text-sm text-gray-600 font-medium">
                        {template._count?.exercises ?? 0}
                      </td>
                      <td className="px-6 py-4">
                        <DomainWeightsBar weights={weights} />
                      </td>
                      <td className="px-6 py-4">
                        <Badge variant={template.status === 'published' ? 'success' : 'warning'}>
                          {template.status}
                        </Badge>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <div className="flex justify-end gap-2">
                          <Link
                            href={`/admin/assessment-templates/${template.id}/edit`}
                            className="text-blue-600 hover:text-blue-800 text-sm"
                          >
                            Edit
                          </Link>
                          {template.status === 'published' ? (
                            <button
                              onClick={() => handleUnpublish(template.id)}
                              className="text-yellow-600 hover:text-yellow-800 text-sm"
                            >
                              Unpublish
                            </button>
                          ) : (
                            <button
                              onClick={() => handlePublish(template.id)}
                              className="text-green-600 hover:text-green-800 text-sm"
                            >
                              Publish
                            </button>
                          )}
                          <button
                            onClick={() => handleDelete(template.id)}
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
          </div>
        )}
      </div>
    </div>
  );
}
