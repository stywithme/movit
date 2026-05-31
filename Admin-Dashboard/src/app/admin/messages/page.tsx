'use client';

import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
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
import {
  MessageFormModal,
  MessageBulkAudioModal,
  TtsDefaultsModal,
  type MessageFormData,
} from '@/components/messages';
import { Plus, Settings, Volume2 } from 'lucide-react';
import type { LocalizedTextWithAudio } from '@/lib/types/localized';

interface MessageTemplate {
  id: string;
  code: string;
  category: string;
  context: string | null;
  description?: string | null;
  content: LocalizedTextWithAudio;
  tags: string[];
  isSystem: boolean;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

interface Pagination {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

const CATEGORY_OPTIONS = [
  { value: '', label: 'All Categories' },
  { value: 'state', label: 'State' },
  { value: 'position', label: 'Position' },
  { value: 'motivational', label: 'Motivational' },
  { value: 'tip', label: 'Tip' },
  { value: 'system', label: 'System' },
];

const STATUS_OPTIONS = [
  { value: '', label: 'All Status' },
  { value: 'active', label: 'Active' },
  { value: 'inactive', label: 'Inactive' },
];

const AUDIO_FILTER_OPTIONS = [
  { value: '', label: 'All (audio)' },
  { value: 'any', label: 'Missing audio (any language)' },
  { value: 'ar', label: 'Missing Arabic audio' },
  { value: 'en', label: 'Missing English audio' },
  { value: 'complete', label: 'Fully voiced (where text exists)' },
];

const PAGE_SIZE = 20;

type ConfirmAction = { type: 'delete'; id: string; label: string } | null;

function AudioStatusCell({ content }: { content: LocalizedTextWithAudio }) {
  const enText = !!content.en?.trim();
  const arText = !!content.ar?.trim();
  const enOk = !enText || !!content.audioEn;
  const arOk = !arText || !!content.audioAr;
  return (
    <div className="flex flex-col gap-0.5 text-xs">
      <span className={enOk ? 'text-success' : 'text-warning'} title="English TTS">
        EN {enOk ? 'Ready' : 'Missing'}
      </span>
      <span className={arOk ? 'text-success' : 'text-warning'} title="Arabic TTS">
        AR {arOk ? 'Ready' : 'Missing'}
      </span>
    </div>
  );
}

export default function MessagesListPage() {
  const [messages, setMessages] = useState<MessageTemplate[]>([]);
  const [pagination, setPagination] = useState<Pagination | null>(null);
  const [loading, setLoading] = useState(true);
  const [pageError, setPageError] = useState<string | null>(null);
  const [categoryFilter, setCategoryFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [audioFilter, setAudioFilter] = useState('');

  const [formModalOpen, setFormModalOpen] = useState(false);
  const [editingMessage, setEditingMessage] = useState<MessageFormData | null>(null);
  const [bulkAudioOpen, setBulkAudioOpen] = useState(false);
  const [ttsDefaultsOpen, setTtsDefaultsOpen] = useState(false);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction>(null);
  const [actionLoading, setActionLoading] = useState(false);

  useEffect(() => {
    const t = window.setTimeout(() => setDebouncedSearch(searchQuery), 400);
    return () => window.clearTimeout(t);
  }, [searchQuery]);

  const fetchMessages = useCallback(
    async (page = 1) => {
      setLoading(true);
      setPageError(null);
      try {
        const params = new URLSearchParams({
          includeInactive: 'true',
          page: page.toString(),
          limit: PAGE_SIZE.toString(),
        });
        if (categoryFilter) params.set('category', categoryFilter);
        if (statusFilter) params.set('status', statusFilter);
        if (audioFilter) params.set('audioMissing', audioFilter);
        if (debouncedSearch.trim()) params.set('search', debouncedSearch.trim());

        const res = await fetch(`/api/messages?${params.toString()}`);
        const data = await res.json().catch(() => ({
          success: false,
          error: `Failed to load messages (${res.status})`,
        }));
        if (res.ok && data.success) {
          setMessages(data.data);
          if (data.pagination) {
            setPagination(data.pagination);
          } else {
            setPagination(null);
          }
        } else {
          setMessages([]);
          setPagination(null);
          setPageError(data.error || 'Failed to load messages');
          toast.error(data.error || 'Failed to load messages');
        }
      } catch (error) {
        console.error('Error fetching messages:', error);
        setMessages([]);
        setPagination(null);
        setPageError('Failed to load messages');
        toast.error('Failed to load messages');
      } finally {
        setLoading(false);
      }
    },
    [categoryFilter, statusFilter, audioFilter, debouncedSearch]
  );

  useEffect(() => {
    fetchMessages(1);
  }, [fetchMessages]);

  const handleCreate = () => {
    setEditingMessage(null);
    setFormModalOpen(true);
  };

  const handleEdit = (msg: MessageTemplate) => {
    setEditingMessage({
      id: msg.id,
      code: msg.code,
      category: msg.category,
      context: msg.context,
      description: msg.description ?? null,
      content: msg.content,
      tags: msg.tags,
      isSystem: msg.isSystem,
      isActive: msg.isActive,
    });
    setFormModalOpen(true);
  };

  const handleSaved = () => {
    fetchMessages(pagination?.page || 1);
  };

  const handleToggleActive = async (id: string, currentStatus: boolean, isSystem: boolean) => {
    if (isSystem) {
      toast.warning('System messages cannot be deactivated.');
      return;
    }
    try {
      const res = await fetch(`/api/messages/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: !currentStatus }),
      });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
        toast.success(currentStatus ? 'Message deactivated' : 'Message activated');
        fetchMessages(pagination?.page || 1);
      } else {
        toast.error(data.error || 'Failed to update message status');
      }
    } catch (error) {
      console.error('Error toggling status:', error);
      toast.error('Failed to update message status');
    }
  };

  const requestDelete = (message: MessageTemplate) => {
    if (message.isSystem) {
      toast.warning('System messages cannot be deleted.');
      return;
    }

    setConfirmAction({ type: 'delete', id: message.id, label: message.code });
  };

  const handleConfirmDelete = async () => {
    if (!confirmAction) return;

    setActionLoading(true);
    try {
      const res = await fetch(`/api/messages/${confirmAction.id}`, { method: 'DELETE' });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) {
        toast.success('Message deleted');
        setConfirmAction(null);
        fetchMessages(pagination?.page || 1);
      } else {
        toast.error(data.error || 'Failed to delete message');
      }
    } catch (error) {
      console.error('Error deleting message:', error);
      toast.error('Failed to delete message');
    } finally {
      setActionLoading(false);
    }
  };

  const columns: DataTableColumn<MessageTemplate>[] = [
    {
      key: 'code',
      header: 'Code',
      cell: (message) => (
        <div className="flex min-w-[180px] items-center gap-2">
          {message.isSystem && <Badge variant="secondary">System</Badge>}
          <span className="font-mono text-sm text-muted-foreground">{message.code}</span>
        </div>
      ),
    },
    {
      key: 'category',
      header: 'Category',
      cell: (message) => <Badge variant="outline" className="capitalize">{message.category}</Badge>,
    },
    {
      key: 'context',
      header: 'Context',
      cell: (message) => <span className="text-muted-foreground">{message.context || '-'}</span>,
    },
    {
      key: 'content',
      header: 'Content',
      cell: (message) => (
        <div className="max-w-xs space-y-1">
          <div className="truncate">{message.content.en}</div>
          <div className="truncate text-muted-foreground" dir="rtl">
            {message.content.ar}
          </div>
        </div>
      ),
      className: 'max-w-xs',
    },
    {
      key: 'audio',
      header: 'Audio',
      cell: (message) => <AudioStatusCell content={message.content} />,
    },
    {
      key: 'tags',
      header: 'Tags',
      cell: (message) => (
        <div className="flex max-w-[220px] flex-wrap gap-1">
          {message.tags.length > 0 ? (
            message.tags.slice(0, 3).map((tag) => (
              <Badge key={tag} variant="secondary">
                {tag}
              </Badge>
            ))
          ) : (
            <span className="text-muted-foreground">-</span>
          )}
          {message.tags.length > 3 && <Badge variant="outline">+{message.tags.length - 3}</Badge>}
        </div>
      ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (message) => <StatusBadge status={message.isActive ? 'active' : 'inactive'} />,
    },
    {
      key: 'actions',
      header: <span className="sr-only">Actions</span>,
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (message) => (
        <div className="flex items-center justify-end gap-1">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            disabled={message.isSystem}
            onClick={() => handleToggleActive(message.id, message.isActive, message.isSystem)}
          >
            {message.isActive ? 'Deactivate' : 'Activate'}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => handleEdit(message)}>
            Edit
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="text-destructive hover:text-destructive"
            disabled={message.isSystem}
            onClick={() => requestDelete(message)}
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
        title="Messages Library"
        description="Manage reusable feedback messages and generated audio coverage."
        actions={
          <>
            <Button
              type="button"
              variant="outline"
              size="icon"
              onClick={() => setTtsDefaultsOpen(true)}
              title="Default TTS settings"
              aria-label="Default TTS settings"
            >
              <Settings className="size-4" />
            </Button>
            <Button type="button" variant="outline" onClick={() => setBulkAudioOpen(true)}>
              <Volume2 className="size-4" />
              Bulk audio (AI)
            </Button>
            <Button type="button" onClick={handleCreate}>
              <Plus className="size-4" />
              New Message
            </Button>
          </>
        }
      />

      <FilterBar
        searchValue={searchQuery}
        searchPlaceholder="Search by code, text, or tags..."
        onSearchChange={setSearchQuery}
        selects={[
          {
            id: 'category',
            value: categoryFilter,
            onChange: setCategoryFilter,
            options: CATEGORY_OPTIONS,
            className: 'lg:w-52',
          },
          {
            id: 'status',
            value: statusFilter,
            onChange: setStatusFilter,
            options: STATUS_OPTIONS,
            className: 'lg:w-44',
          },
          {
            id: 'audio',
            value: audioFilter,
            onChange: setAudioFilter,
            options: AUDIO_FILTER_OPTIONS,
            className: 'lg:w-60',
          },
        ]}
        onReset={() => {
          setSearchQuery('');
          setCategoryFilter('');
          setStatusFilter('');
          setAudioFilter('');
        }}
      />

      <DataTable
        columns={columns}
        data={messages}
        getRowKey={(message) => message.id}
        loading={loading}
        error={pageError}
        emptyTitle="No messages found"
        emptyDescription="Create a new message or adjust the current filters."
        footer={<TablePagination pagination={pagination} onPageChange={fetchMessages} disabled={loading} />}
      />

      <MessageFormModal
        open={formModalOpen}
        onOpenChange={setFormModalOpen}
        editMessage={editingMessage}
        onSaved={handleSaved}
      />

      <MessageBulkAudioModal
        open={bulkAudioOpen}
        onOpenChange={setBulkAudioOpen}
        currentFilters={{
          category: categoryFilter,
          status: statusFilter,
          search: debouncedSearch,
          audioMissing: audioFilter,
        }}
        onCompleted={() => fetchMessages(pagination?.page || 1)}
      />

      <TtsDefaultsModal open={ttsDefaultsOpen} onOpenChange={setTtsDefaultsOpen} />

      <ConfirmDialog
        open={!!confirmAction}
        onOpenChange={(open) => !open && setConfirmAction(null)}
        title="Delete message?"
        description={`Delete "${confirmAction?.label || 'this message'}" permanently? If it is used in exercises, deactivation is safer.`}
        confirmLabel="Delete"
        destructive
        loading={actionLoading}
        onConfirm={handleConfirmDelete}
      />
    </div>
  );
}
