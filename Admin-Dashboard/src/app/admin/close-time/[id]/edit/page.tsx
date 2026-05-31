'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/auth/auth-store';
import { Button } from '@/components/ui';
import { PageHeader } from '@/components/common';
import { CloseTimeForm } from '../../components/CloseTimeForm';

export default function EditCloseTimePage({ params }: { params: Promise<{ id: string }> }) {
    const { user, initialized } = useAuthStore();
    const router = useRouter();
    const resolvedParams = use(params);
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        if (initialized && user?.isDoctor) {
            router.replace('/admin/close-time');
        }
    }, [user, initialized, router]);

    if (!initialized || user?.isDoctor) {
        return null;
    }

    useEffect(() => {
        const fetchCloseTime = async () => {
            try {
                const res = await fetch(`/api/admin/close-time`);
                const result = await res.json();

                // The API backend doesn't seem to have a specific GET /admin/close-time/:id in the controller
                // So we will fetch all and filter client-side just to be safe, or assumes we added it?
                // Wait, CloseTimeController didn't have a getById. It only has GET /admin/close-time.
                // Let's just find it locally.
                if (result.success) {
                    const item = result.data.find((c: any) => c.id === resolvedParams.id);
                    if (item) {
                        setData(item);
                    } else {
                        setError('Close time not found');
                    }
                } else {
                    setError(result.error || 'Close time not found');
                }
            } catch (err) {
                setError('Failed to fetch close time');
            } finally {
                setLoading(false);
            }
        };

        fetchCloseTime();
    }, [resolvedParams.id]);

    if (loading) return <div className="p-8 text-center text-muted-foreground">Loading...</div>;

    if (error || !data) {
        return (
            <div className="p-8 text-center">
                <p className="mb-4 text-destructive">{error || 'Close time not found'}</p>
                <Button type="button" variant="outline" onClick={() => router.back()}>
                    Go back
                </Button>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <PageHeader
                title="Edit Close Time"
                description="Update vacation or closure period"
                breadcrumbs={[
                    { label: 'Close Times', href: '/admin/close-time' },
                    { label: 'Edit Close Time' },
                ]}
            />

            <CloseTimeForm initialData={data} isEditing />
        </div>
    );
}
