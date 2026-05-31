'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/lib/auth/auth-store';
import { Badge } from '@/components/ui';
import { DataTable, PageHeader, type DataTableColumn } from '@/components/common';

interface DoctorWorkTime {
    id: string;
    day: string;
    startTime: string;
    endTime: string;
}

export default function MyWorkTimesPage() {
    const { user } = useAuthStore();
    const [workTimes, setWorkTimes] = useState<DoctorWorkTime[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const fetchMyWorkTimes = async () => {
            try {
                const res = await fetch('/api/admin/doctor-work-time/mine');
                const data = await res.json();
                if (data.success) {
                    setWorkTimes(data.data);
                }
            } catch (error) {
                console.error('Error fetching my work times:', error);
            } finally {
                setLoading(false);
            }
        };

        if (user) {
            fetchMyWorkTimes();
        }
    }, [user]);

    if (!user || loading) {
        return <div className="p-8 text-center text-muted-foreground">Loading...</div>;
    }

    // Sort by day of week
    const daysOrder = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const sortedWorkTimes = [...workTimes].sort((a, b) => {
        const dayDiff = daysOrder.indexOf(a.day) - daysOrder.indexOf(b.day);
        if (dayDiff !== 0) return dayDiff;
        return a.startTime.localeCompare(b.startTime);
    });

    const columns: DataTableColumn<DoctorWorkTime>[] = [
        {
            key: 'day',
            header: 'Day',
            cell: (wt) => <Badge variant="outline">{wt.day}</Badge>,
        },
        {
            key: 'start',
            header: 'Starts At',
            cell: (wt) => <span className="font-medium">{wt.startTime}</span>,
        },
        {
            key: 'end',
            header: 'Ends At',
            cell: (wt) => <span className="font-medium">{wt.endTime}</span>,
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader title="My Work Times" description="View your weekly scheduled hours" />

            <DataTable
                columns={columns}
                data={sortedWorkTimes}
                getRowKey={(workTime) => workTime.id}
                emptyTitle="You have no work times scheduled"
                emptyDescription="Please contact an administrator to set up your schedule."
            />
        </div>
    );
}
