'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
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

    if (loading) return <div className="p-8 text-center text-gray-500">Loading...</div>;

    if (error || data === null) {
        return (
            <div className="p-8 text-center">
                <p className="text-red-500 mb-4">{error || 'Work time not found'}</p>
                <button
                    onClick={() => router.back()}
                    className="text-blue-600 hover:underline"
                >
                    Go back
                </button>
            </div>
        );
    }

    const adminName = data.length > 0 && data[0].admin ? data[0].admin.name : 'Doctor';

    return (
        <div className="space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-gray-900">Edit Work Time</h1>
                <p className="text-gray-600 mt-1">Update schedule for {adminName}</p>
            </div>

            <WorkTimeForm initialData={data} adminId={resolvedParams.id} isEditing />
        </div>
    );
}
