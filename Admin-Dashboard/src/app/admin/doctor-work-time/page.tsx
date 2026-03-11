'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';

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
        return <div className="p-8 text-center text-gray-500">Loading...</div>;
    }

    // Double check if redirecting
    if (user.isDoctor && !isSuperAdmin) {
        return null;
    }

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Doctor Work Times</h1>
                    <p className="text-gray-600 mt-1">Manage global working hours for doctors</p>
                </div>
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {groupedWorkTimes.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">
                        <p>No work times found.</p>
                        {can('create', 'DoctorWorkTime') && (
                            <Link href="/admin/doctor-work-time/new" className="text-blue-600 hover:underline mt-2 inline-block">
                                Create the first schedule
                            </Link>
                        )}
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Doctor
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Total Shifts
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {groupedWorkTimes.map((group) => (
                                    <React.Fragment key={group.admin.id}>
                                        <tr className="hover:bg-gray-50">
                                            <td className="px-6 py-4 text-center">
                                                <div>
                                                    <p className="font-medium text-gray-900">{group.admin.name}</p>
                                                    <p className="text-sm text-gray-500">{group.admin.email}</p>
                                                </div>
                                            </td>
                                            <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                                {group.workTimes.length} Shifts
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <button
                                                    onClick={() => toggleExpand(group.admin.id)}
                                                    className="text-blue-600 hover:text-blue-800 text-sm font-medium mr-4"
                                                    disabled={group.workTimes.length === 0}
                                                    style={{ opacity: group.workTimes.length === 0 ? 0.5 : 1 }}
                                                >
                                                    {expandedDoctorId === group.admin.id ? 'Hide' : 'View'}
                                                </button>
                                                {(can('update', 'DoctorWorkTime') || isSuperAdmin) && (
                                                    <Link
                                                        href={`/admin/doctor-work-time/${group.admin.id}/edit`}
                                                        className="text-blue-600 hover:text-blue-800 text-sm"
                                                    >
                                                        Edit Schedule
                                                    </Link>
                                                )}
                                            </td>
                                        </tr>
                                        {expandedDoctorId === group.admin.id && (
                                            <tr>
                                                <td colSpan={3} className="bg-gray-50 px-6 py-4">
                                                    <div className="border border-gray-200 rounded-lg overflow-hidden bg-white">
                                                        <table className="w-full text-sm">
                                                            <thead className="bg-gray-100 border-b border-gray-200 text-gray-600 text-center">
                                                                <tr>
                                                                    <th className="px-4 py-2 font-medium">Day</th>
                                                                    <th className="px-4 py-2 font-medium">Time</th>
                                                                </tr>
                                                            </thead>
                                                            <tbody className="divide-y divide-gray-100">
                                                                {group.workTimes.map(wt => (
                                                                    <tr key={wt.id} className="hover:bg-gray-50">
                                                                        <td className="px-4 py-3 text-center">
                                                                            <span className="inline-flex px-2 py-1 text-xs font-medium rounded bg-blue-50 text-blue-700">
                                                                                {wt.day}
                                                                            </span>
                                                                        </td>
                                                                        <td className="px-4 py-3 text-gray-600 text-center">
                                                                            {wt.startTime} - {wt.endTime}
                                                                        </td>
                                                                    </tr>
                                                                ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                </td>
                                            </tr>
                                        )}
                                    </React.Fragment>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
}
