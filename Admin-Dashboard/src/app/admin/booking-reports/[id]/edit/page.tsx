'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
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

    if (loading) return <div className="p-8 text-center text-gray-500">Loading Report...</div>;

    if (error || !data) {
        return (
            <div className="p-8 text-center">
                <p className="text-red-500 mb-4">{error || 'Medical Report not found'}</p>
                <button
                    onClick={() => router.back()}
                    className="text-blue-600 hover:underline"
                >
                    Go back
                </button>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-gray-900">Edit Medical Report</h1>
                <p className="text-gray-600 mt-1">Update diagnosis or prescription</p>
            </div>

            <BookingReportForm initialData={data} isEditing />
        </div>
    );
}
