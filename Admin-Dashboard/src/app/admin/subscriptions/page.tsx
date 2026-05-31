'use client';

import { useEffect, useState } from 'react';
import { Button } from '@/components/ui';
import { DataTable, FilterBar, PageHeader, StatusBadge, type DataTableColumn } from '@/components/common';
import type { Subscription } from '@/lib/types/subscription';

export default function SubscriptionsPage() {
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

    const columns: DataTableColumn<Subscription>[] = [
        {
            key: 'user',
            header: 'User',
            cell: (sub) => (
                <div>
                    <div className="font-medium">{sub.user?.name || 'Unknown'}</div>
                    <div className="text-xs text-muted-foreground">{sub.user?.email || '-'}</div>
                </div>
            ),
        },
        {
            key: 'plan',
            header: 'Plan',
            cell: (sub) => (
                <span className="text-sm font-medium">
                    {typeof sub.plan?.name === 'object' ? sub.plan?.name?.en || sub.plan?.name?.ar : sub.plan?.name}
                </span>
            ),
        },
        {
            key: 'amount',
            header: 'Amount',
            cell: (sub) => <span className="text-sm">${sub.amountPaid}</span>,
        },
        {
            key: 'period',
            header: 'Period',
            cell: (sub) => (
                <div className="text-sm text-muted-foreground">
                    <div>{formatDate(sub.startDate)}</div>
                    <div className="text-xs">to {formatDate(sub.endDate)}</div>
                </div>
            ),
        },
        {
            key: 'status',
            header: 'Status',
            cell: (sub) => <StatusBadge status={sub.status} />,
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader title="Subscriptions" description="Monitor user subscriptions and payments" />

            <form onSubmit={handleSearch}>
                <FilterBar
                    searchValue={searchQuery}
                    searchPlaceholder="Search by name or email..."
                    onSearchChange={setSearchQuery}
                    selects={[
                        {
                            id: 'status',
                            value: statusFilter,
                            placeholder: 'Status',
                            onChange: setStatusFilter,
                            options: [
                                { value: '', label: 'All' },
                                { value: 'active', label: 'Active' },
                                { value: 'cancelled', label: 'Cancelled' },
                                { value: 'expired', label: 'Expired' },
                            ],
                        },
                    ]}
                    onReset={() => {
                        setSearchQuery('');
                        setStatusFilter('');
                    }}
                >
                    <Button type="submit" variant="outline">
                        Search
                    </Button>
                </FilterBar>
            </form>

            <DataTable
                columns={columns}
                data={subscriptions}
                getRowKey={(subscription) => subscription.id}
                loading={loading}
                emptyTitle="No subscriptions found"
                emptyDescription="Subscriptions will appear here after users complete payments."
            />
        </div>
    );
}
