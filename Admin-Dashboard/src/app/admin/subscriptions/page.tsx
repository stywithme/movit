'use client';

import { useEffect, useState } from 'react';
import { usePermissions } from '@/hooks/usePermissions';
import { Button, Input, Select } from '@/components/ui';
import type { Subscription } from '@/lib/types/subscription';

export default function SubscriptionsPage() {
    const { can, isSuperAdmin } = usePermissions();

    const [subscriptions, setSubscriptions] = useState<Subscription[]>([]);
    const [loading, setLoading] = useState(true);

    // Filters
    const [statusFilter, setStatusFilter] = useState<string>('');
    const [searchQuery, setSearchQuery] = useState<string>('');

    const fetchSubscriptions = async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (statusFilter) params.set('status', statusFilter);
            if (searchQuery) params.set('search', searchQuery);

            const res = await fetch(`/api/admin/subscriptions?${params}`);
            const data = await res.json();

            if (data.success) {
                setSubscriptions(data.data);
            }
        } catch (error) {
            console.error('Error fetching subscriptions:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchSubscriptions();
    }, [statusFilter]);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchSubscriptions();
    };

    const formatDate = (dateStr: string) => {
        return new Date(dateStr).toLocaleDateString('en-GB', {
            year: 'numeric',
            month: 'short',
            day: 'numeric'
        });
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'active': return 'bg-green-100 text-green-800';
            case 'cancelled': return 'bg-red-100 text-red-800';
            case 'expired': return 'bg-gray-100 text-gray-800';
            default: return 'bg-blue-100 text-blue-800';
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Subscriptions</h1>
                    <p className="text-gray-600 mt-1">Monitor user subscriptions and payments</p>
                </div>
            </div>

            <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
                <div className="flex flex-wrap gap-4 items-end">
                    <form onSubmit={handleSearch} className="flex-1 min-w-[200px]">
                        <label className="block text-sm font-medium text-gray-700 mb-1">Search User</label>
                        <div className="flex gap-2">
                            <Input
                                type="text"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Search by name or email..."
                                className="flex-1"
                            />
                            <button
                                type="submit"
                                className="px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200"
                            >
                                Search
                            </button>
                        </div>
                    </form>

                    <div className="w-40">
                        <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                        <Select
                            value={statusFilter}
                            onChange={(e) => setStatusFilter(e.target.value)}
                            options={[
                                { value: '', label: 'All' },
                                { value: 'active', label: 'Active' },
                                { value: 'cancelled', label: 'Cancelled' },
                                { value: 'expired', label: 'Expired' },
                            ]}
                        />
                    </div>
                </div>
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {loading ? (
                    <div className="p-8 text-center text-gray-500">Loading subscriptions...</div>
                ) : subscriptions.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">No subscriptions found.</div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">User</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Plan</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Amount</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Period</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Status</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {subscriptions.map((sub) => (
                                    <tr key={sub.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 text-center">
                                            <div className="font-medium text-gray-900">{sub.user?.name || 'Unknown'}</div>
                                            <div className="text-xs text-gray-500">{sub.user?.email || '-'}</div>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <div className="text-sm font-medium text-gray-900">
                                                {typeof sub.plan?.name === 'object' ? (sub.plan?.name?.en || sub.plan?.name?.ar) : sub.plan?.name}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-900 text-center">
                                            ${sub.amountPaid}
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                            <div>{formatDate(sub.startDate)}</div>
                                            <div className="text-xs text-gray-400">to {formatDate(sub.endDate)}</div>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <span className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${getStatusColor(sub.status)}`}>
                                                {sub.status}
                                            </span>
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
