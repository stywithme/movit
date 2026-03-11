'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { Button } from '@/components/ui';

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

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to delete this medical report?')) return;
        try {
            const res = await fetch(`/api/admin/booking-reports/${id}`, { method: 'DELETE' });
            if (res.ok) {
                setReports(prev => prev.filter(r => r.id !== id));
            }
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
        return <div className="p-8 text-center text-gray-500">Loading reports...</div>;
    }

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Medical Reports</h1>
                    <p className="text-gray-600 mt-1">Post-session patient diagnoses and prescriptions</p>
                </div>
                {can('create', 'BookingReport') && (
                    <Link
                        href="/admin/booking-reports/new"
                        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors flex items-center gap-2"
                    >
                        <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                        </svg>
                        Write Report
                    </Link>
                )}
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {reports.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">
                        <p>No medical reports found.</p>
                        {can('create', 'BookingReport') && (
                            <Link href="/admin/booking-reports/new" className="text-blue-600 hover:underline mt-2 inline-block">
                                Write a new report
                            </Link>
                        )}
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Date</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Patient</th>
                                    {(!user?.isDoctor || isSuperAdmin) && (
                                        <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Doctor</th>
                                    )}
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Summary</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {reports.map((report) => (
                                    <tr key={report.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 text-sm text-gray-900 text-center">
                                            {formatDate(report.createdAt)}
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <div className="font-medium text-gray-900">{report.booking?.user?.name || '---'}</div>
                                            <div className="text-xs text-gray-500">{report.booking?.user?.email || '---'}</div>
                                        </td>
                                        {(!user?.isDoctor || isSuperAdmin) && (
                                            <td className="px-6 py-4 text-sm text-gray-900 text-center">
                                                {report.booking?.admin?.name || '---'}
                                            </td>
                                        )}
                                        <td className="px-6 py-4 text-sm text-gray-600 max-w-xs truncate text-center">
                                            {report.content.diagnosis}
                                        </td>
                                        <td className="px-6 py-4 text-right space-x-3">
                                            <Link
                                                href={`/admin/booking-reports/${report.id}`}
                                                className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                                            >
                                                View
                                            </Link>
                                            {can('update', 'BookingReport') && (
                                                <Link
                                                    href={`/admin/booking-reports/${report.id}/edit`}
                                                    className="text-gray-600 hover:text-gray-800 text-sm font-medium"
                                                >
                                                    Edit
                                                </Link>
                                            )}
                                            {can('delete', 'BookingReport') && (
                                                <button
                                                    onClick={() => handleDelete(report.id)}
                                                    className="text-red-600 hover:text-red-800 text-sm font-medium ml-3"
                                                >
                                                    Delete
                                                </button>
                                            )}
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
