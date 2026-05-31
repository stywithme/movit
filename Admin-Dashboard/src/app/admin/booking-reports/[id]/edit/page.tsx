'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/components/ui';
import { PageHeader } from '@/components/common';
import { BookingReportForm } from '../../components/BookingReportForm';

export default function EditBookingReportPage({ params }: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const resolvedParams = use(params);
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchReport = async () => {
            try {
                const res = await fetch(`/api/admin/booking-reports/${resolvedParams.id}`);
                const result = await res.json();

                if (result.success) {
                    setData(result.data);
                } else {
                    setError(result.error || 'Medical Report not found');
                }
            } catch (err) {
                setError('Failed to fetch medical report');
            } finally {
                setLoading(false);
            }
        };

        fetchReport();
    }, [resolvedParams.id]);

    if (loading) return <div className="p-8 text-center text-muted-foreground">Loading Report...</div>;

    if (error || !data) {
        return (
            <div className="p-8 text-center">
                <p className="mb-4 text-destructive">{error || 'Medical Report not found'}</p>
                <Button type="button" variant="outline" onClick={() => router.back()}>
                    Go back
                </Button>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <PageHeader
                title="Edit Medical Report"
                description="Update diagnosis or prescription"
                breadcrumbs={[
                    { label: 'Medical Reports', href: '/admin/booking-reports' },
                    { label: 'Edit Medical Report' },
                ]}
            />

            <BookingReportForm initialData={data} isEditing />
        </div>
    );
}
