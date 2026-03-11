'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/lib/auth/auth-store';

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
        return <div className="p-8 text-center text-gray-500">Loading...</div>;
    }

    // Sort by day of week
    const daysOrder = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
    const sortedWorkTimes = [...workTimes].sort((a, b) => {
        const dayDiff = daysOrder.indexOf(a.day) - daysOrder.indexOf(b.day);
        if (dayDiff !== 0) return dayDiff;
        return a.startTime.localeCompare(b.startTime);
    });

    return (
        <div className="space-y-6">
            <div>
                <h1 className="text-2xl font-bold text-gray-900">My Work Times</h1>
                <p className="text-gray-600 mt-1">View your weekly scheduled hours</p>
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {sortedWorkTimes.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">
                        <p>You have no work times scheduled.</p>
                        <p className="text-sm mt-1">Please contact an administrator to set up your schedule.</p>
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Day
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Starts At
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">
                                        Ends At
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {sortedWorkTimes.map((wt) => (
                                    <tr key={wt.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 text-center">
                                            <span className="inline-flex px-3 py-1 text-sm font-medium rounded-full bg-blue-50 text-blue-700">
                                                {wt.day}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-gray-900 font-medium text-center">
                                            {wt.startTime}
                                        </td>
                                        <td className="px-6 py-4 text-gray-900 font-medium text-center">
                                            {wt.endTime}
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
