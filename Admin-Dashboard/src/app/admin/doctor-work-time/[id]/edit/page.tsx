'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui';
import { PageHeader } from '@/components/common';
import { WorkTimeForm } from '../../components/WorkTimeForm';

export default function EditWorkTimePage({ params }: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const resolvedParams = use(params);
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchWorkTime = async () => {
            try {
                // Fetch all schedules for this adminId
                const res = await fetch(`/api/admin/doctor-work-time/${resolvedParams.id}`);
                const result = await res.json();

                if (result.success) {
                    setData(result.data);
                } else {
                    setError(result.error || 'Work time not found');
                }
            } catch (err) {
                setError('Failed to fetch work time');
            } finally {
                setLoading(false);
            }
        };

        fetchWorkTime();
    }, [resolvedParams.id]);

    if (loading) return <div className="p-8 text-center text-muted-foreground">Loading...</div>;

    if (error || data === null) {
        return (
            <div className="p-8 text-center">
                <p className="mb-4 text-destructive">{error || 'Work time not found'}</p>
                <Button type="button" variant="outline" onClick={() => router.back()}>
                    Go back
                </Button>
            </div>
        );
    }

    const adminName = data.length > 0 && data[0].admin ? data[0].admin.name : 'Doctor';

    return (
        <div className="space-y-6">
            <PageHeader
                title="Edit Work Time"
                description={`Update schedule for ${adminName}`}
                breadcrumbs={[
                    { label: 'Doctor Work Times', href: '/admin/doctor-work-time' },
                    { label: 'Edit Work Time' },
                ]}
            />

            <WorkTimeForm initialData={data} adminId={resolvedParams.id} isEditing />
        </div>
    );
}
