'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Plus } from 'lucide-react';
import { Badge, Button } from '@/components/ui';
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

interface Admin {
  id: string;
  name: string;
  email: string;
  role: { id: string; name: string } | null;
  isActive: boolean;
  createdAt: string;
}

export default function AdminsPage() {
  const [admins, setAdmins] = useState<Admin[]>([]);
  const [pagination, setPagination] = useState<PaginationMeta | null>(null);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [deleteAdminId, setDeleteAdminId] = useState<string | null>(null);

  const fetchAdmins = async (page = 1) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      params.set('page', page.toString());
      if (statusFilter) params.set('status', statusFilter);
      if (searchQuery) params.set('search', searchQuery);

      const res = await fetch(`/api/admins?${params}`);
      const data = await res.json();

      if (data.success) {
        setAdmins(data.data);
        setPagination(data.pagination);
      }
    } catch (error) {
      console.error('Error fetching admins:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAdmins();
  }, [statusFilter]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    fetchAdmins();
  };

  const handleToggleActive = async (id: string, isActive: boolean) => {
    try {
      const res = await fetch(`/api/admins/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isActive: !isActive }),
      });
      if (res.ok) fetchAdmins(pagination?.page || 1);
    } catch (error) {
      console.error('Error updating status:', error);
    }
  };

  const handleDelete = async () => {
    if (!deleteAdminId) return;

    try {
      const res = await fetch(`/api/admins/${deleteAdminId}`, { method: 'DELETE' });
      if (res.ok) fetchAdmins(pagination?.page || 1);
      setDeleteAdminId(null);
    } catch (error) {
      console.error('Error deleting admin:', error);
    }
  };

  const columns: DataTableColumn<Admin>[] = [
    {
      key: 'admin',
      header: 'Admin',
      cell: (admin) => (
        <div className="min-w-[220px]">
          <p className="font-medium">{admin.name}</p>
          <p className="text-sm text-muted-foreground">{admin.email}</p>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      cell: (admin) =>
        admin.role ? (
          <Badge variant="purple">{admin.role.name}</Badge>
        ) : (
          <span className="text-sm text-muted-foreground">No role</span>
        ),
    },
    {
      key: 'status',
      header: 'Status',
      cell: (admin) => <StatusBadge status={admin.isActive ? 'active' : 'inactive'} />,
    },
    {
      key: 'actions',
      header: 'Actions',
      headerClassName: 'text-right',
      className: 'text-right',
      cell: (admin) => (
        <div className="flex justify-end gap-2">
          <Button asChild variant="ghost" size="sm">
            <Link href={`/admin/admins/${admin.id}/edit`}>Edit</Link>
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => handleToggleActive(admin.id, admin.isActive)}
          >
            {admin.isActive ? 'Deactivate' : 'Activate'}
          </Button>
          <Button type="button" variant="ghost" size="sm" onClick={() => setDeleteAdminId(admin.id)}>
            Delete
          </Button>
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <PageHeader
        title="Admins"
        description="Manage dashboard admins"
        actions={
          <Button asChild>
            <Link href="/admin/admins/new">
              <Plus className="size-4" />
              New Admin
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
        data={admins}
        getRowKey={(admin) => admin.id}
        loading={loading}
        emptyTitle="No admins found"
        emptyDescription="Create an admin to assign dashboard access."
        footer={<Pagination pagination={pagination} onPageChange={fetchAdmins} disabled={loading} />}
      />

      <ConfirmDialog
        open={Boolean(deleteAdminId)}
        onOpenChange={(open) => !open && setDeleteAdminId(null)}
        title="Delete admin?"
        description="This admin account will be removed from the dashboard."
        confirmLabel="Delete"
        destructive
        onConfirm={handleDelete}
      />
    </div>
  );
}
