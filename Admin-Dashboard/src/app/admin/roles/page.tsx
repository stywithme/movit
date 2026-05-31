'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { toast } from 'sonner';
import { Badge, Button } from '@/components/ui';
import { Edit2, Plus, Trash2, UsersIcon } from 'lucide-react';
import { ConfirmDialog, DataTable, FilterBar, PageHeader, type DataTableColumn } from '@/components/common';
import type { Role } from '@/lib/types/roles';

export default function RolesPage() {
    const [roles, setRoles] = useState<Role[]>([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [deleteRoleId, setDeleteRoleId] = useState<string | null>(null);

    const fetchRoles = async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (searchQuery) params.set('search', searchQuery);

            const res = await fetch(`/api/admin/permissions/roles?${params}`);
            const data = await res.json();

            if (data.success) {
                setRoles(data.data);
            }
        } catch (error) {
            console.error('Error fetching roles:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchRoles();
    }, []);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchRoles();
    };

    const handleDelete = async () => {
        if (!deleteRoleId) return;

        try {
            const res = await fetch(`/api/admin/permissions/roles/${deleteRoleId}`, { method: 'DELETE' });
            if (res.ok) fetchRoles();
            else {
                const data = await res.json();
                toast.error(data.error || 'Failed to delete role');
            }
            setDeleteRoleId(null);
        } catch (error) {
            console.error('Error deleting role:', error);
        }
    };

    const columns: DataTableColumn<Role>[] = [
        {
            key: 'role',
            header: 'Role Name',
            cell: (role) => (
                <div className="min-w-[220px]">
                    <div className="font-medium">{role.name}</div>
                    <div className="text-xs text-muted-foreground">{role.displayName.en}</div>
                </div>
            ),
        },
        {
            key: 'permissions',
            header: 'Permissions',
            cell: (role) => <Badge variant="purple">{role._count?.permissions || 0} Actions</Badge>,
        },
        {
            key: 'admins',
            header: 'Assigned Admins',
            cell: (role) => <Badge variant="orange">{role._count?.admins || 0} Members</Badge>,
        },
        {
            key: 'actions',
            header: 'Actions',
            headerClassName: 'text-right',
            className: 'text-right',
            cell: (role) => (
                <div className="flex justify-end gap-2">
                    <Button asChild variant="ghost" size="icon" title="Edit Role">
                        <Link href={`/admin/roles/${role.id}/edit`}>
                            <Edit2 className="size-4" />
                        </Link>
                    </Button>
                    {!role.isSystem && (
                        <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            title="Delete Role"
                            onClick={() => setDeleteRoleId(role.id)}
                        >
                            <Trash2 className="size-4" />
                        </Button>
                    )}
                    <Button type="button" variant="ghost" size="icon" title="View Members">
                        <UsersIcon className="size-4" />
                    </Button>
                </div>
            ),
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader
                title="Roles Management"
                description="Define and manage roles and their permissions"
                actions={
                    <Button asChild>
                        <Link href="/admin/roles/new">
                        <Plus className="w-4 h-4" />
                        Add Role
                        </Link>
                    </Button>
                }
            />

            <form onSubmit={handleSearch}>
                <FilterBar
                    searchValue={searchQuery}
                    searchPlaceholder="Search roles..."
                    onSearchChange={setSearchQuery}
                    onReset={() => setSearchQuery('')}
                >
                    <Button type="submit" variant="outline">
                        Search
                    </Button>
                </FilterBar>
            </form>

            <DataTable
                columns={columns}
                data={roles}
                getRowKey={(role) => role.id}
                loading={loading}
                emptyTitle="No roles found"
                emptyDescription="Get started by creating your first role."
            />

            <ConfirmDialog
                open={Boolean(deleteRoleId)}
                onOpenChange={(open) => !open && setDeleteRoleId(null)}
                title="Delete role?"
                description="This role will be removed. Admins assigned to it may lose access."
                confirmLabel="Delete"
                destructive
                onConfirm={handleDelete}
            />
        </div>
    );
}
