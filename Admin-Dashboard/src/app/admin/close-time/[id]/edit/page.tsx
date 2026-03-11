'use client';

import { useEffect, useState, use } from 'react';
import { useRouter } from 'next/navigation';
import { CloseTimeForm } from '../../components/CloseTimeForm';

export default function EditCloseTimePage({ params }: { params: Promise<{ id: string }> }) {
    const router = useRouter();
    const resolvedParams = use(params);
    const [data, setData] = useState<any>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

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

    if (loading) return <div className="p-8 text-center text-gray-500">Loading...</div>;

    if (error || !data) {
        return (
            <div className="p-8 text-center">
                <p className="text-red-500 mb-4">{error || 'Close time not found'}</p>
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
                <h1 className="text-2xl font-bold text-gray-900">Edit Close Time</h1>
                <p className="text-gray-600 mt-1">Update vacation or closure period</p>
            </div>

            <CloseTimeForm initialData={data} isEditing />
        </div>
    );
}
