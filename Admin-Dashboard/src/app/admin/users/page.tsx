'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui';
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

interface User {
  id: string;
  name: string;
  email: string;
  avatarUrl: string | null;
  isActive: boolean;
  isPro: boolean;
  subscriptionExpiry: string | null;
  totalMinutes: number;
  totalWorkouts: number;
  createdAt: string;
}

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [pagination, setPagination] = useState<PaginationMeta | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [deleteUserId, setDeleteUserId] = useState<string | null>(null);

  const fetchUsers = async (page = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page.toString());
      if (statusFilter) params.set('status', statusFilter);
      if (searchQuery) params.set('search', searchQuery);

      const res = await fetch(`/api/users?${params}`);
      const data = await res.json();

      if (data.success) {
        setUsers(data.data);
        setPagination(data.pagination);
      }
    } catch (error) {
      console.error('Error fetching users:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUsers();
  }, [statusFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchUsers();
  };

  const handleToggleActive = async (id: string, isActive: boolean) => {
    try {
      const res = await fetch(`/api/users/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: !isActive }),
      });
      if (res.ok) fetchUsers(pagination?.page || 1);
    } catch (error) {
      console.error('Error updating status:', error);
    }
  };

  const handleDelete = async () => {
    if (!deleteUserId) return;

    try {
      const res = await fetch(`/api/users/${deleteUserId}`, { method: 'DELETE' });
      if (res.ok) fetchUsers(pagination?.page || 1);
      setDeleteUserId(null);
    } catch (error) {
      console.error('Error deleting user:', error);
    }
  };

  const formatDate = (value: string | null) => {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '-';
    return date.toLocaleDateString();
  };

  const columns: DataTableColumn<User>[] = [
    {
      key: 'user',
      header: 'User',
      cell: (user) => (
        <div className="flex min-w-[220px] items-center gap-3">
          {user.avatarUrl ? (
            // Keep a plain img here because avatar URLs are user-provided.
            <img src={user.avatarUrl} alt="" className="size-10 rounded-full object-cover" />
          ) : (
            <div className="flex size-10 items-center justify-center rounded-full bg-muted">
              <span className="text-sm font-medium text-muted-foreground">{user.name?.[0]?.toUpperCase() || '?'}</span>
            </div>
          )}
          <div className="min-w-0">
            <p className="truncate font-medium">{user.name}</p>
            <p className="truncate text-sm text-muted-foreground">{user.email}</p>
          </div>
        </div>
      ),
    },
    {
      key: 'subscriptionExpiry',
      header: 'Subscription Expiry',
      cell: (user) => <span className="text-sm text-muted-foreground">{formatDate(user.subscriptionExpiry)}</span>,
    },
    {
      key: 'minutes',
      header: 'Total Minutes',
      cell: (user) => <span className="text-sm text-muted-foreground">{user.totalMinutes}</span>,
    },
    {
      key: 'workouts',
      header: 'Total Workouts',
      cell: (user) => <span className="text-sm text-muted-foreground">{user.totalWorkouts}</span>,
    },
    {
      key: 'status',
      header: 'Status',
      cell: (user) => <StatusBadge status={user.isActive ? 'active' : 'inactive'} />,
    },
    {
      key: 'actions',
      header: 'Actions',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (user) => (
        <div className="flex justify-end gap-2">
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => handleToggleActive(user.id, user.isActive)}
          >
            {user.isActive ? 'Deactivate' : 'Activate'}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setDeleteUserId(user.id)}>
            Delete
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Users"
        description="Manage mobile app users"
        actions={
          <Button asChild>
            <Link href="/admin/users/new">
              <Plus className="size-4" />
              New User
            </Link>
          </Button>
        }
      />

      <form onSubmit={handleSearch}>
        <FilterBar
          searchValue={searchQuery}
          searchPlaceholder="Search by name or email..."
          onSearchChange={setSearchQuery}
          selects={[
            {
              id: 'status',
              value: statusFilter,
              placeholder: 'Status',
              onChange: setStatusFilter,
              options: [
                { value: '', label: 'All' },
                { value: 'active', label: 'Active' },
                { value: 'inactive', label: 'Inactive' },
              ],
            },
          ]}
          onReset={() => {
            setSearchQuery('');
            setStatusFilter('');
          }}
        >
          <Button type="submit" variant="outline">
            Search
          </Button>
        </FilterBar>
      </form>

      <DataTable
        columns={columns}
        data={users}
        getRowKey={(user) => user.id}
        loading={loading}
        emptyTitle="No users found"
        emptyDescription="Try adjusting the filters or create a new mobile user."
        footer={<Pagination pagination={pagination} onPageChange={fetchUsers} disabled={loading} />}
      />

      <ConfirmDialog
        open={Boolean(deleteUserId)}
        onOpenChange={(open) => !open && setDeleteUserId(null)}
        title="Delete user?"
        description="This user will be removed from the dashboard."
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </div>
  );
}
