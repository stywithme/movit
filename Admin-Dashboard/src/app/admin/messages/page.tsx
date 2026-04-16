'use client';

import { useCallback, useEffect, useState } from 'react';
import { Input, Select } from '@/components/ui';
import {
  MessageFormModal,
  MessageBulkAudioModal,
  TtsDefaultsModal,
  type MessageFormData,
} from '@/components/messages';
import { Settings } from 'lucide-react';
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

function AudioStatusCell({ content }: { content: LocalizedTextWithAudio }) {
  const enText = !!content.en?.trim();
  const arText = !!content.ar?.trim();
  const enOk = !enText || !!content.audioEn;
  const arOk = !arText || !!content.audioAr;
  return (
    <div className="flex flex-col gap-0.5 text-xs">
      <span className={enOk ? 'text-green-700' : 'text-amber-700'} title="English TTS">
        EN {enOk ? '✓' : '—'}
      </span>
      <span className={arOk ? 'text-green-700' : 'text-amber-700'} title="Arabic TTS">
        AR {arOk ? '✓' : '—'}
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
        }
      } catch (error) {
        console.error('Error fetching messages:', error);
        setMessages([]);
        setPagination(null);
        setPageError('Failed to load messages');
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
      window.alert('System messages cannot be deactivated.');
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
        fetchMessages(pagination?.page || 1);
      } else {
        window.alert(data.error || 'Failed to update message status');
      }
    } catch (error) {
      console.error('Error toggling status:', error);
      window.alert('Failed to update message status');
    }
  };

  const handleDelete = async (id: string, isSystem: boolean) => {
    if (isSystem) {
      window.alert('System messages cannot be deleted.');
      return;
    }
    if (!confirm('Delete this message permanently? If it is used in exercises, you should deactivate it instead.')) return;
    try {
      const res = await fetch(`/api/messages/${id}`, { method: 'DELETE' });
      const data = await res.json().catch(() => ({}));
      if (res.ok && data.success) fetchMessages(pagination?.page || 1);
      else if (data.error) window.alert(data.error);
    } catch (error) {
      console.error('Error deleting message:', error);
      window.alert('Failed to delete message');
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Messages Library</h1>
          <p className="text-gray-600 mt-1">Manage reusable feedback messages</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setTtsDefaultsOpen(true)}
            className="p-2.5 bg-white border border-gray-300 text-gray-800 rounded-lg hover:bg-gray-50 transition-colors"
            title="Default TTS settings"
            aria-label="Default TTS settings"
          >
            <Settings className="w-5 h-5" />
          </button>
          <button
            type="button"
            onClick={() => setBulkAudioOpen(true)}
            className="px-4 py-2 bg-white border border-gray-300 text-gray-800 rounded-lg hover:bg-gray-50 transition-colors flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z"
              />
            </svg>
            Bulk audio (AI)
          </button>
          <button
            onClick={handleCreate}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
          >
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Message
          </button>
        </div>
      </div>

      {pageError && (
        <div className="p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-700">
          {pageError}
        </div>
      )}

      <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
        <div className="flex flex-wrap gap-4 items-end">
          <div className="flex-1 min-w-[200px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
            <Input
              type="text"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Search by code, text, or tags..."
              className="flex-1"
            />
          </div>

          <div className="min-w-[200px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Category</label>
            <Select
              value={categoryFilter}
              onChange={(e) => setCategoryFilter(e.target.value)}
              options={CATEGORY_OPTIONS}
            />
          </div>

          <div className="min-w-[200px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={STATUS_OPTIONS}
            />
          </div>

          <div className="min-w-[220px]">
            <label className="block text-sm font-medium text-gray-700 mb-1">Audio</label>
            <Select
              value={audioFilter}
              onChange={(e) => setAudioFilter(e.target.value)}
              options={AUDIO_FILTER_OPTIONS}
            />
          </div>
        </div>
      </div>

      <div className="bg-white rounded-lg shadow-sm border border-gray-200">
        {loading ? (
          <div className="p-6 text-center text-gray-500">Loading messages...</div>
        ) : messages.length === 0 ? (
          <div className="p-6 text-center text-gray-500">No messages found.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Code</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Category</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Context</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Content</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">
                    Audio
                  </th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Tags</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-200">
                {messages.map((message) => (
                  <tr key={message.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3 text-sm font-mono text-gray-700">
                      <span className="inline-flex items-center gap-1">
                        {message.isSystem && (
                          <span title="System message" className="text-xs text-slate-500 font-sans">
                            [sys]
                          </span>
                        )}
                        {message.code}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-900">{message.category}</td>
                    <td className="px-4 py-3 text-sm text-gray-700">{message.context || '-'}</td>
                    <td className="px-4 py-3 text-sm text-gray-900 max-w-xs">
                      <div className="space-y-1">
                        <div className="text-gray-900 truncate">{message.content.en}</div>
                        <div className="text-gray-600 truncate" dir="rtl">{message.content.ar}</div>
                      </div>
                    </td>
                    <td className="px-4 py-3 align-top">
                      <AudioStatusCell content={message.content} />
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-700">
                      {message.tags.length > 0 ? message.tags.join(', ') : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <span
                        className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${
                          message.isActive
                            ? 'bg-green-100 text-green-800'
                            : 'bg-gray-100 text-gray-600'
                        }`}
                      >
                        {message.isActive ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex items-center justify-end gap-3">
                        <button
                          type="button"
                          onClick={() => handleToggleActive(message.id, message.isActive, message.isSystem)}
                          disabled={message.isSystem}
                          className={`text-sm ${
                            message.isActive ? 'text-yellow-600 hover:text-yellow-900' : 'text-green-600 hover:text-green-900'
                          } disabled:opacity-40 disabled:cursor-not-allowed`}
                        >
                          {message.isActive ? 'Deactivate' : 'Activate'}
                        </button>
                        <button
                          type="button"
                          onClick={() => handleEdit(message)}
                          className="text-blue-600 hover:text-blue-900 text-sm"
                        >
                          Edit
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(message.id, message.isSystem)}
                          disabled={message.isSystem}
                          className="text-red-600 hover:text-red-900 text-sm disabled:opacity-40 disabled:cursor-not-allowed"
                        >
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

        {pagination && pagination.total > 0 && (
          <div className="px-6 py-4 border-t border-gray-200 flex flex-wrap justify-between items-center gap-3">
            <p className="text-sm text-gray-600">
              Showing {(pagination.page - 1) * pagination.limit + 1} to{' '}
              {Math.min(pagination.page * pagination.limit, pagination.total)} of {pagination.total}
            </p>
            {pagination.totalPages > 1 && (
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => fetchMessages(pagination.page - 1)}
                  disabled={pagination.page === 1 || loading}
                  className="px-3 py-1.5 border border-gray-300 bg-white text-gray-800 rounded text-sm hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400 disabled:border-gray-200 disabled:cursor-not-allowed"
                >
                  Previous
                </button>
                <button
                  type="button"
                  onClick={() => fetchMessages(pagination.page + 1)}
                  disabled={pagination.page === pagination.totalPages || loading}
                  className="px-3 py-1.5 border border-gray-300 bg-white text-gray-800 rounded text-sm hover:bg-gray-50 disabled:bg-gray-100 disabled:text-gray-400 disabled:border-gray-200 disabled:cursor-not-allowed"
                >
                  Next
                </button>
              </div>
            )}
          </div>
        )}
      </div>

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
    </div>
  );
}
