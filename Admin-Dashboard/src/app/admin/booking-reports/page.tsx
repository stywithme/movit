'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { Button } from '@/components/ui';
import { ConfirmDialog, DataTable, PageHeader, type DataTableColumn } from '@/components/common';
import { Plus } from 'lucide-react';

interface BookingReport {
    id: string;
    booking: {
        user: { name: string; email: string };
        admin: { name: string; email: string };
        startAt: string;
    };
    content: {
        diagnosis: string;
        prescription?: string;
    };
    createdAt: string;
}

export default function BookingReportsPage() {
    const { user } = useAuthStore();
    const { can, isSuperAdmin } = usePermissions();

    const [reports, setReports] = useState<BookingReport[]>([]);
    const [loading, setLoading] = useState(true);
    const [deleteReportId, setDeleteReportId] = useState<string | null>(null);

    const fetchReports = async () => {
        setLoading(true);
        try {
            const isDoctorOnly = user?.isDoctor && !isSuperAdmin;
            const endpoint = isDoctorOnly ? '/api/admin/booking-reports/mine' : '/api/admin/booking-reports';

            const res = await fetch(endpoint);
            const data = await res.json();

            if (data.success) {
                setReports(data.data);
            }
        } catch (error) {
            console.error('Error fetching medical reports:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (user) {
            fetchReports();
        }
    }, [user, isSuperAdmin]);

    const handleDelete = async () => {
        if (!deleteReportId) return;

        try {
            const res = await fetch(`/api/admin/booking-reports/${deleteReportId}`, { method: 'DELETE' });
            if (res.ok) {
                setReports(prev => prev.filter(r => r.id !== deleteReportId));
            }
            setDeleteReportId(null);
        } catch (error) {
            console.error('Error deleting report:', error);
        }
    };

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleDateString('en-GB', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    };

    if (!user || loading) {
        return <div className="p-8 text-center text-muted-foreground">Loading reports...</div>;
    }

    const columns: DataTableColumn<BookingReport>[] = [
        {
            key: 'date',
            header: 'Date',
            cell: (report) => <span className="text-sm font-medium">{formatDate(report.createdAt)}</span>,
        },
        {
            key: 'patient',
            header: 'Patient',
            cell: (report) => (
                <div>
                    <div className="font-medium">{report.booking?.user?.name || '---'}</div>
                    <div className="text-xs text-muted-foreground">{report.booking?.user?.email || '---'}</div>
                </div>
            ),
        },
        ...((!user?.isDoctor || isSuperAdmin)
            ? [
                {
                    key: 'doctor',
                    header: 'Doctor',
                    cell: (report: BookingReport) => <span className="text-sm font-medium">{report.booking?.admin?.name || '---'}</span>,
                } satisfies DataTableColumn<BookingReport>,
            ]
            : []),
        {
            key: 'summary',
            header: 'Summary',
            cell: (report) => <span className="line-clamp-2 text-sm text-muted-foreground">{report.content.diagnosis}</span>,
        },
        {
            key: 'actions',
            header: 'Actions',
            headerClassName: 'text-right',
            className: 'text-right',
            cell: (report) => (
                <div className="flex justify-end gap-2">
                    <Button asChild variant="ghost" size="sm">
                        <Link href={`/admin/booking-reports/${report.id}`}>View</Link>
                    </Button>
                    {can('update', 'BookingReport') && (
                        <Button asChild variant="ghost" size="sm">
                            <Link href={`/admin/booking-reports/${report.id}/edit`}>Edit</Link>
                        </Button>
                    )}
                    {can('delete', 'BookingReport') && (
                        <Button type="button" variant="ghost" size="sm" onClick={() => setDeleteReportId(report.id)}>
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
                title="Medical Reports"
                description="Post-session patient diagnoses and prescriptions"
                actions={
                    can('create', 'BookingReport') && (
                    <Button asChild>
                        <Link href="/admin/booking-reports/new">
                            <Plus className="size-4" />
                        Write Report
                        </Link>
                    </Button>
                    )
                }
            />

            <DataTable
                columns={columns}
                data={reports}
                getRowKey={(report) => report.id}
                emptyTitle="No medical reports found"
                emptyDescription="Reports written after completed appointments will appear here."
            />

            <ConfirmDialog
                open={Boolean(deleteReportId)}
                onOpenChange={(open) => !open && setDeleteReportId(null)}
                title="Delete medical report?"
                description="This medical report will be permanently removed."
                confirmLabel="Delete"
                destructive
                onConfirm={handleDelete}
            />
        </div>
    );
}
