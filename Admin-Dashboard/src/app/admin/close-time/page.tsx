'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';

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

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this close time?')) return;
        try {
            const res = await fetch(`/api/admin/close-time/${id}`, { method: 'DELETE' });
            if (res.ok) {
                setCloseTimes(prev => prev.filter(c => c.id !== id));
            }
        } catch (error) {
            console.error('Error deleting close time:', error);
        }
    };

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleDateString('en-GB');
    };

    if (!user || loading) {
        return <div className="p-8 text-center text-gray-500">Loading...</div>;
    }

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Close Times</h1>
                    <p className="text-gray-600 mt-1">Manage vacations and clinic closures</p>
                </div>
                {can('create', 'CloseTime') && (
                    <Link
                        href="/admin/close-time/new"
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Add Close Time
                    </Link>
                )}
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {closeTimes.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">
                        <p>No close times found.</p>
                        {can('create', 'CloseTime') && (
                            <Link href="/admin/close-time/new" className="text-blue-600 hover:underline mt-2 inline-block">
                                Create a closure period
                            </Link>
                        )}
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Target
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        From
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        To
                                    </th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {closeTimes.map((ct) => (
                                    <tr key={ct.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 text-center">
                                            {ct.adminId ? (
                                                <div className="flex flex-col items-center">
                                                    <p className="font-medium text-gray-900">{ct.admin?.name || 'Unknown Doctor'}</p>
                                                    <span className="inline-flex px-2 py-0.5 mt-1 text-xs font-medium rounded-full bg-blue-50 text-blue-700 border border-blue-100">
                                                        Doctor Specific
                                                    </span>
                                                </div>
                                            ) : (
                                                <span className="inline-flex px-2 py-1 text-xs font-medium rounded bg-red-50 text-red-700">
                                                    Global Closure
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                            <div className="font-medium">{formatDate(ct.fromDate)}</div>
                                            <div className="text-xs text-gray-400 mt-1">{ct.fromTime}</div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                            <div className="font-medium">{formatDate(ct.toDate)}</div>
                                            <div className="text-xs text-gray-400 mt-1">{ct.toTime}</div>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end gap-3">
                                                {can('update', 'CloseTime') && (
                                                    <Link
                                                        href={`/admin/close-time/${ct.id}/edit`}
                                                        className="text-blue-600 hover:text-blue-800 text-sm"
                                                    >
                                                        Edit
                                                    </Link>
                                                )}
                                                {can('delete', 'CloseTime') && (
                                                    <button
                                                        onClick={() => handleDelete(ct.id)}
                                                        className="text-red-600 hover:text-red-800 text-sm"
                                                    >
                                                        Delete
                                                    </button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
