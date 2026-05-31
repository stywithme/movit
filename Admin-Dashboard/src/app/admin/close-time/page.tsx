'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { Badge, Button } from '@/components/ui';
import { ConfirmDialog, DataTable, PageHeader, type DataTableColumn } from '@/components/common';
import { Plus } from 'lucide-react';

interface CloseTime {
    id: string;
    adminId: string | null;
    fromDate: string;
    toDate: string;
    fromTime: string;
    toTime: string;
    admin?: {
        id: string;
        name: string;
        email: string;
    } | null;
}

export default function CloseTimePage() {
    const { user } = useAuthStore();
    const { can, isSuperAdmin } = usePermissions();

    const [closeTimes, setCloseTimes] = useState<CloseTime[]>([]);
    const [loading, setLoading] = useState(true);
    const [deleteCloseTimeId, setDeleteCloseTimeId] = useState<string | null>(null);

    useEffect(() => {
        const fetchCloseTimes = async () => {
            try {
                const isDoctorOnly = user?.isDoctor && !isSuperAdmin;
                const endpoint = isDoctorOnly ? '/api/admin/close-time/mine' : '/api/admin/close-time';

                const res = await fetch(endpoint);
                const data = await res.json();

                if (data.success) {
                    setCloseTimes(data.data);
                }
            } catch (error) {
                console.error('Error fetching close times:', error);
            } finally {
                setLoading(false);
            }
        };

        if (user) {
            fetchCloseTimes();
        }
    }, [user, isSuperAdmin]);

    const handleDelete = async () => {
        if (!deleteCloseTimeId) return;

        try {
            const res = await fetch(`/api/admin/close-time/${deleteCloseTimeId}`, { method: 'DELETE' });
            if (res.ok) {
                setCloseTimes(prev => prev.filter(c => c.id !== deleteCloseTimeId));
            }
            setDeleteCloseTimeId(null);
        } catch (error) {
            console.error('Error deleting close time:', error);
        }
    };

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleDateString('en-GB');
    };

    if (!user || loading) {
        return <div className="p-8 text-center text-muted-foreground">Loading...</div>;
    }

    const columns: DataTableColumn<CloseTime>[] = [
        {
            key: 'target',
            header: 'Target',
            cell: (ct) =>
                ct.adminId ? (
                    <div>
                        <p className="font-medium">{ct.admin?.name || 'Unknown Doctor'}</p>
                        <Badge variant="primary" className="mt-1">Doctor Specific</Badge>
                    </div>
                ) : (
                    <Badge variant="destructive">Global Closure</Badge>
                ),
        },
        {
            key: 'from',
            header: 'From',
            cell: (ct) => (
                <div className="text-sm text-muted-foreground">
                    <div className="font-medium text-foreground">{formatDate(ct.fromDate)}</div>
                    <div className="mt-1 text-xs">{ct.fromTime}</div>
                </div>
            ),
        },
        {
            key: 'to',
            header: 'To',
            cell: (ct) => (
                <div className="text-sm text-muted-foreground">
                    <div className="font-medium text-foreground">{formatDate(ct.toDate)}</div>
                    <div className="mt-1 text-xs">{ct.toTime}</div>
                </div>
            ),
        },
        {
            key: 'actions',
            header: 'Actions',
            headerClassName: 'text-right',
            className: 'text-right',
            cell: (ct) => (
                <div className="flex justify-end gap-2">
                    {can('update', 'CloseTime') && !user.isDoctor && (
                        <Button asChild variant="ghost" size="sm">
                            <Link href={`/admin/close-time/${ct.id}/edit`}>Edit</Link>
                        </Button>
                    )}
                    {can('delete', 'CloseTime') && !user.isDoctor && (
                        <Button type="button" variant="ghost" size="sm" onClick={() => setDeleteCloseTimeId(ct.id)}>
                            Delete
                        </Button>
                    )}
                </div>
            ),
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader
                title="Close Times"
                description="Manage vacations and clinic closures"
                actions={
                    can('create', 'CloseTime') && !user.isDoctor && (
                    <Button asChild>
                        <Link href="/admin/close-time/new">
                            <Plus className="size-4" />
                        Add Close Time
                        </Link>
                    </Button>
                    )
                }
            />

            <DataTable
                columns={columns}
                data={closeTimes}
                getRowKey={(closeTime) => closeTime.id}
                emptyTitle="No close times found"
                emptyDescription="Create a closure period to block availability."
            />

            <ConfirmDialog
                open={Boolean(deleteCloseTimeId)}
                onOpenChange={(open) => !open && setDeleteCloseTimeId(null)}
                title="Delete close time?"
                description="This closure period will be removed from booking availability."
                confirmLabel="Delete"
                destructive
                onConfirm={handleDelete}
            />
        </div>
    );
}
