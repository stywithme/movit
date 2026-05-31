'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { Badge, Button } from '@/components/ui';
import { DataTable, PageHeader, type DataTableColumn } from '@/components/common';

interface DoctorWorkTime {
    id: string;
    day: string;
    startTime: string;
    endTime: string;
    admin: {
        id: string;
        name: string;
        email: string;
    };
}

// Grouped by doctor
interface GroupedWorkTime {
    admin: {
        id: string;
        name: string;
        email: string;
    };
    workTimes: DoctorWorkTime[];
}

export default function DoctorWorkTimePage() {
    const router = useRouter();
    const { user } = useAuthStore();
    const { can, isSuperAdmin } = usePermissions();

    const [groupedWorkTimes, setGroupedWorkTimes] = useState<GroupedWorkTime[]>([]);
    const [loading, setLoading] = useState(true);
    const [expandedDoctorId, setExpandedDoctorId] = useState<string | null>(null);

    useEffect(() => {
        // If user is a Doctor (and not SuperAdmin), redirect them to their personal page.
        if (user && user.isDoctor && !isSuperAdmin) {
            router.replace('/admin/doctor-work-time/mine');
            return;
        }

        const fetchWorkTimes = async () => {
            try {
                // Fetch doctors first
                const docsRes = await fetch('/api/admins?isDoctor=true&limit=1000');
                const docsData = await docsRes.json();

                // Fetch work times
                const res = await fetch('/api/admin/doctor-work-time');
                const data = await res.json();

                if (data.success && docsData.success) {
                    const rawWorkTimes: DoctorWorkTime[] = data.data;
                    const doctors: any[] = docsData.data;

                    const grouped: GroupedWorkTime[] = doctors.map(doc => {
                        return {
                            admin: doc,
                            workTimes: rawWorkTimes.filter(wt => wt.admin.id === doc.id)
                        };
                    });

                    setGroupedWorkTimes(grouped);
                }
            } catch (error) {
                console.error('Error fetching work times:', error);
            } finally {
                setLoading(false);
            }
        };

        if (user) {
            fetchWorkTimes();
        }
    }, [user, isSuperAdmin, router]);

    const toggleExpand = (adminId: string) => {
        if (expandedDoctorId === adminId) {
            setExpandedDoctorId(null);
        } else {
            setExpandedDoctorId(adminId);
        }
    };

    if (!user || loading) {
        return <div className="p-8 text-center text-muted-foreground">Loading...</div>;
    }

    // Double check if redirecting
    if (user.isDoctor && !isSuperAdmin) {
        return null;
    }

    const columns: DataTableColumn<GroupedWorkTime>[] = [
        {
            key: 'doctor',
            header: 'Doctor',
            cell: (group) => (
                <div>
                    <p className="font-medium">{group.admin.name}</p>
                    <p className="text-sm text-muted-foreground">{group.admin.email}</p>
                </div>
            ),
        },
        {
            key: 'shifts',
            header: 'Shifts',
            cell: (group) => (
                <div className="space-y-3">
                    <Badge variant={group.workTimes.length > 0 ? 'primary' : 'secondary'}>
                        {group.workTimes.length} Shifts
                    </Badge>
                    {expandedDoctorId === group.admin.id && (
                        <div className="grid gap-2 rounded-lg border bg-muted/30 p-3 sm:grid-cols-2">
                            {group.workTimes.map((wt) => (
                                <div key={wt.id} className="flex items-center justify-between rounded-md bg-background px-3 py-2 text-sm">
                                    <Badge variant="outline">{wt.day}</Badge>
                                    <span className="text-muted-foreground">
                                        {wt.startTime} - {wt.endTime}
                                    </span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            ),
        },
        {
            key: 'actions',
            header: 'Actions',
            headerClassName: 'text-right',
            className: 'text-right',
            cell: (group) => (
                <div className="flex justify-end gap-2">
                    <Button
                        type="button"
                        variant="ghost"
                        size="sm"
                        onClick={() => toggleExpand(group.admin.id)}
                        disabled={group.workTimes.length === 0}
                    >
                        {expandedDoctorId === group.admin.id ? 'Hide' : 'View'}
                    </Button>
                    {(can('update', 'DoctorWorkTime') || isSuperAdmin) && (
                        <Button asChild variant="ghost" size="sm">
                            <Link href={`/admin/doctor-work-time/${group.admin.id}/edit`}>Edit Schedule</Link>
                        </Button>
                    )}
                </div>
            ),
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader title="Doctor Work Times" description="Manage global working hours for doctors" />

            <DataTable
                columns={columns}
                data={groupedWorkTimes}
                getRowKey={(group) => group.admin.id}
                emptyTitle="No work times found"
                emptyDescription="Create schedules for doctors before opening booking slots."
            />
        </div>
    );
}
