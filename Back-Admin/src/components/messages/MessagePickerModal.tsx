'use client';

/**
 * MessagePickerModal - Browse and select messages from the library
 * ================================================================
 * 
 * Used inside exercise wizard to assign messages to:
 * - Joint state messages (perfect, warning, danger, etc.)
 * - Feedback messages (motivational, tips)
 * - Position check error messages
 * 
 * Features:
 * - Search by text, code, or tags
 * - Filter by category and context
 * - Preview message details (both languages + audio)
 * - Quick-create new message without leaving the modal
 * - Single or multi-select mode
 */

import { useEffect, useMemo, useRef, useState, useCallback } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogBody,
  DialogFooter,
  Button,
  Input,
  Select,
  Badge,
} from '@/components/ui';
import { MessageFormModal, type MessageFormData } from './MessageFormModal';
import { Plus, Search, Check, Volume2 } from 'lucide-react';
import type { LocalizedTextWithAudio } from '@/lib/types/localized';

export interface MessageOption {
  id: string;
  code: string;
  category: string;
  context: string | null;
  content: LocalizedTextWithAudio;
  tags: string[];
  isSystem: boolean;
  isActive: boolean;
}

interface MessagePickerModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Currently selected message IDs */
  selected?: string[];
  /** Callback when selection confirmed */
  onSelect: (messages: MessageOption[]) => void;
  /** Allow selecting multiple messages */
  multiple?: boolean;
  /** Pre-filter by category */
  categoryFilter?: string;
  /** Pre-filter by context */
  contextFilter?: string;
  /** Title override */
  title?: string;
  /** Description override */
  description?: string;
  /** Defaults for quick-create */
  createDefaults?: Partial<MessageFormData>;
}

const CATEGORY_OPTIONS = [
  { value: '', label: 'All Categories' },
  { value: 'state', label: 'State' },
  { value: 'position', label: 'Position' },
  { value: 'motivational', label: 'Motivational' },
  { value: 'tip', label: 'Tip' },
  { value: 'system', label: 'System' },
];

const CONTEXT_OPTIONS = [
  { value: '', label: 'All Contexts' },
  { value: 'perfect', label: 'Perfect' },
  { value: 'normal', label: 'Normal' },
  { value: 'pad', label: 'Pad' },
  { value: 'warning', label: 'Warning' },
  { value: 'danger', label: 'Danger' },
  { value: 'general', label: 'General' },
  { value: 'motivational', label: 'Motivational' },
  { value: 'tip', label: 'Tip' },
  { value: 'error', label: 'Error' },
];

const CONTEXT_COLORS: Record<string, string> = {
  perfect: 'bg-green-100 text-green-800',
  normal: 'bg-blue-100 text-blue-800',
  pad: 'bg-orange-100 text-orange-800',
  warning: 'bg-amber-100 text-amber-800',
  danger: 'bg-red-100 text-red-800',
  general: 'bg-gray-100 text-gray-700',
  motivational: 'bg-purple-100 text-purple-800',
  tip: 'bg-cyan-100 text-cyan-800',
  error: 'bg-red-100 text-red-800',
};

export function MessagePickerModal({
  open,
  onOpenChange,
  selected = [],
  onSelect,
  multiple = false,
  categoryFilter: initialCategoryFilter,
  contextFilter: initialContextFilter,
  title,
  description,
  createDefaults,
}: MessagePickerModalProps) {
  const [messages, setMessages] = useState<MessageOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [categoryFilter, setCategoryFilter] = useState(initialCategoryFilter || '');
  const [contextFilter, setContextFilter] = useState(initialContextFilter || '');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set(selected));
  const [expandedId, setExpandedId] = useState<string | null>(null);

  // Quick-create modal
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Track previous open state to only run effect on open transition
  const prevOpenRef = useRef(false);

  // Fetch messages
  const fetchMessages = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/messages');
      const data = await res.json();
      if (data.success) {
        setMessages(data.data);
      }
    } catch (error) {
      console.error('Error fetching messages:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  // Reset state only when modal opens (not on every prop change)
  useEffect(() => {
    if (open && !prevOpenRef.current) {
      fetchMessages();
      setSelectedIds(new Set(selected));
      setSearchQuery('');
      setCategoryFilter(initialCategoryFilter || '');
      setContextFilter(initialContextFilter || '');
    }
    prevOpenRef.current = open;
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  // Filtered messages
  const filteredMessages = useMemo(() => {
    return messages.filter((msg) => {
      if (!msg.isActive) return false;
      if (categoryFilter && msg.category !== categoryFilter) return false;
      if (contextFilter && msg.context !== contextFilter) return false;
      if (searchQuery) {
        const query = searchQuery.toLowerCase();
        const contentText = `${msg.content.en} ${msg.content.ar}`.toLowerCase();
        const tagsText = msg.tags.join(' ').toLowerCase();
        if (
          !msg.code.toLowerCase().includes(query) &&
          !contentText.includes(query) &&
          !tagsText.includes(query)
        ) {
          return false;
        }
      }
      return true;
    });
  }, [messages, categoryFilter, contextFilter, searchQuery]);

  const toggleSelection = (id: string) => {
    if (multiple) {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        if (next.has(id)) {
          next.delete(id);
        } else {
          next.add(id);
        }
        return next;
      });
    } else {
      setSelectedIds(new Set([id]));
    }
  };

  const handleConfirm = () => {
    const selectedMessages = messages.filter((m) => selectedIds.has(m.id));
    onSelect(selectedMessages);
    onOpenChange(false);
  };

  const handleCreated = (newMessage: MessageFormData) => {
    // Refresh list and auto-select
    fetchMessages().then(() => {
      if (newMessage.id) {
        setSelectedIds((prev) => {
          const next = new Set(prev);
          next.add(newMessage.id!);
          return next;
        });
      }
    });
  };

  return (
    <>
      <Dialog open={open} onOpenChange={onOpenChange}>
        <DialogContent size="xl">
          <DialogHeader>
            <DialogTitle>{title || 'Select Message'}</DialogTitle>
            <DialogDescription>
              {description || 'Browse and select messages from the library.'}
            </DialogDescription>
          </DialogHeader>

          <DialogBody className="!p-0">
            {/* Filters Bar */}
            <div className="sticky top-0 bg-white border-b border-gray-100 px-6 py-3 flex flex-wrap gap-3 items-center z-10">
              <div className="flex-1 min-w-[200px] relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                <Input
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="Search by code, text, or tags..."
                  className="pl-10 !py-2"
                />
              </div>
              <div className="w-[160px]">
                <Select
                  value={categoryFilter}
                  onChange={(e) => setCategoryFilter(e.target.value)}
                  options={CATEGORY_OPTIONS}
                  className="!py-2"
                />
              </div>
              <div className="w-[160px]">
                <Select
                  value={contextFilter}
                  onChange={(e) => setContextFilter(e.target.value)}
                  options={CONTEXT_OPTIONS}
                  className="!py-2"
                />
              </div>
              <Button
                variant="ghost"
                size="sm"
                icon={<Plus className="h-4 w-4" />}
                onClick={() => setShowCreateModal(true)}
              >
                New
              </Button>
            </div>

            {/* Messages List */}
            <div className="divide-y divide-gray-100">
              {loading ? (
                <div className="p-8 text-center text-gray-500">Loading messages...</div>
              ) : filteredMessages.length === 0 ? (
                <div className="p-8 text-center text-gray-500">
                  <p>No messages found.</p>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="mt-2"
                    icon={<Plus className="h-4 w-4" />}
                    onClick={() => setShowCreateModal(true)}
                  >
                    Create New Message
                  </Button>
                </div>
              ) : (
                filteredMessages.map((msg) => {
                  const isSelected = selectedIds.has(msg.id);
                  const isExpanded = expandedId === msg.id;
                  return (
                    <div
                      key={msg.id}
                      className={`px-6 py-3 cursor-pointer transition-colors ${
                        isSelected
                          ? 'bg-blue-50 border-l-4 border-l-blue-500'
                          : 'hover:bg-gray-50 border-l-4 border-l-transparent'
                      }`}
                      onClick={() => toggleSelection(msg.id)}
                    >
                      <div className="flex items-start gap-3">
                        {/* Selection indicator */}
                        <div
                          className={`mt-1 flex-shrink-0 h-5 w-5 rounded border-2 flex items-center justify-center transition-colors ${
                            isSelected
                              ? 'bg-blue-600 border-blue-600'
                              : 'border-gray-300 bg-white'
                          }`}
                        >
                          {isSelected && <Check className="h-3 w-3 text-white" strokeWidth={3} />}
                        </div>

                        {/* Message content */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <span className="text-sm font-mono font-medium text-gray-800">
                              {msg.code}
                            </span>
                            <span
                              className={`inline-flex px-1.5 py-0.5 text-[10px] font-semibold rounded-full uppercase ${
                                CONTEXT_COLORS[msg.context || ''] || 'bg-gray-100 text-gray-600'
                              }`}
                            >
                              {msg.context || msg.category}
                            </span>
                            {msg.content.audioAr && (
                              <span title="Has Arabic audio">
                                <Volume2 className="h-3 w-3 text-green-500" />
                              </span>
                            )}
                            {msg.content.audioEn && (
                              <span title="Has English audio">
                                <Volume2 className="h-3 w-3 text-blue-500" />
                              </span>
                            )}
                          </div>

                          <p className="text-sm text-gray-900 mt-1 truncate">{msg.content.en}</p>
                          <p className="text-sm text-gray-600 mt-0.5 truncate" dir="rtl">
                            {msg.content.ar}
                          </p>

                          {/* Expanded details */}
                          {isExpanded && (
                            <div className="mt-2 pt-2 border-t border-gray-100">
                              {msg.tags.length > 0 && (
                                <div className="flex flex-wrap gap-1 mt-1">
                                  {msg.tags.map((tag) => (
                                    <Badge key={tag} variant="default" className="text-[10px] px-1.5 py-0.5">
                                      {tag}
                                    </Badge>
                                  ))}
                                </div>
                              )}
                            </div>
                          )}
                        </div>

                        {/* Expand toggle */}
                        <button
                          type="button"
                          className="text-xs text-gray-400 hover:text-gray-600 mt-1"
                          onClick={(e) => {
                            e.stopPropagation();
                            setExpandedId(isExpanded ? null : msg.id);
                          }}
                        >
                          {isExpanded ? 'Less' : 'More'}
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </DialogBody>

          <DialogFooter>
            <div className="flex-1 text-sm text-gray-500">
              {selectedIds.size > 0 && `${selectedIds.size} selected`}
            </div>
            <Button variant="ghost" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button onClick={handleConfirm} disabled={selectedIds.size === 0}>
              {multiple
                ? `Select ${selectedIds.size > 0 ? `(${selectedIds.size})` : ''}`
                : 'Select'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Quick-create modal */}
      <MessageFormModal
        open={showCreateModal}
        onOpenChange={setShowCreateModal}
        onSaved={handleCreated}
        defaults={createDefaults}
      />
    </>
  );
}
