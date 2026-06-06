'use client';

import { useEffect, useState } from 'react';
import { usePermissions } from '@/hooks/usePermissions';
import { Button } from '@/components/ui';
import {
    ConfirmDialog,
    DataTable,
    FilterBar,
    PageHeader,
    StatusBadge,
    type DataTableColumn,
} from '@/components/common';
import { Plus } from 'lucide-react';
import type { Plan } from '@/lib/types/plan';
import { PlanFormModal } from './components/PlanFormModal';

export default function PlansPage() {
    const { can, isSuperAdmin } = usePermissions();

    const [plans, setPlans] = useState<Plan[]>([]);
    const [loading, setLoading] = useState(true);

    // Filters
    const [statusFilter, setStatusFilter] = useState<string>('');
    const [searchQuery, setSearchQuery] = useState<string>('');

    // Modal State
    const [selectedPlan, setSelectedPlan] = useState<Plan | null>(null);
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [deactivatePlanId, setDeactivatePlanId] = useState<string | null>(null);

    const fetchPlans = async () => {
        setLoading(true);
        try {
            const params = new URLSearchParams();
            if (statusFilter !== '') params.set('isActive', statusFilter === 'active' ? 'true' : 'false');
            if (searchQuery) params.set('search', searchQuery);

            const res = await fetch(`/api/admin/plans?${params}`);
            const data = await res.json();

            if (data.success) {
                setPlans(data.data);
            }
        } catch (error) {
            console.error('Error fetching plans:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchPlans();
    }, [statusFilter]);

    const handleSearch = (e: React.FormEvent) => {
        e.preventDefault();
        fetchPlans();
    };

    const handleDelete = async () => {
        if (!deactivatePlanId) return;

        try {
            const res = await fetch(`/api/admin/plans/${deactivatePlanId}`, { method: 'DELETE' });
            if (res.ok) {
                fetchPlans();
            }
            setDeactivatePlanId(null);
        } catch (error) {
            console.error('Error deleting plan:', error);
        }
    };

    const getLocalizedName = (nameObj: Record<string, string>) => {
        if (!nameObj) return '';
        // try finding 'en', or 'ar', or just first key
        return nameObj.en || nameObj.ar || Object.values(nameObj)[0] || '';
    };

    const columns: DataTableColumn<Plan>[] = [
        {
            key: 'name',
            header: 'Plan Name',
            cell: (plan) => (
                <div>
                    <div className="font-medium">{getLocalizedName(plan.name)}</div>
                    {plan.discount > 0 && <div className="text-xs font-medium text-success">Discount: {plan.discount}%</div>}
                </div>
            ),
        },
        {
            key: 'prices',
            header: 'Prices (Month/Year)',
            cell: (plan) => (
                <div className="text-sm text-muted-foreground">
                    <div>
                        {plan.monthlyPrice} / {plan.yearlyPrice} {plan.currency || 'SAR'}
                    </div>
                    {(plan.monthlyGooglePlayProductId || plan.yearlyGooglePlayProductId) && (
                        <div className="mt-1 text-xs">Google Play mapped</div>
                    )}
                </div>
            ),
        },
        {
            key: 'limits',
            header: 'Limits',
            cell: (plan) => (
                <div className="text-sm text-muted-foreground">
                    <div>Workout Templates: {plan.maxWorkoutTemplatesLimit}</div>
                    <div>Exercises: {plan.maxExercisesLimit}</div>
                </div>
            ),
        },
        {
            key: 'status',
            header: 'Status',
            cell: (plan) => <StatusBadge status={plan.isActive ? 'active' : 'inactive'} />,
        },
        {
            key: 'actions',
            header: 'Actions',
            headerClassName: 'text-right',
            className: 'text-right',
            cell: (plan) => (
                <div className="flex justify-end gap-2">
                    {(can('update', 'Plan') || isSuperAdmin) && (
                        <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => {
                                setSelectedPlan(plan);
                                setIsFormOpen(true);
                            }}
                        >
                            Edit
                        </Button>
                    )}
                    {(can('delete', 'Plan') || isSuperAdmin) && plan.isActive && (
                        <Button type="button" variant="ghost" size="sm" onClick={() => setDeactivatePlanId(plan.id)}>
                            Deactivate
                        </Button>
                    )}
                </div>
            ),
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader
                title="Plans"
                description="Manage subscription plans and features"
                actions={
                    (can('create', 'Plan') || isSuperAdmin) && (
                        <Button
                            onClick={() => {
                                setSelectedPlan(null);
                                setIsFormOpen(true);
                            }}
                        >
                            <Plus className="h-4 w-4" />
                            Create Plan
                        </Button>
                    )
                }
            />

            <form onSubmit={handleSearch}>
                <FilterBar
                    searchValue={searchQuery}
                    searchPlaceholder="Search by plan name..."
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
                                { value: 'inactive', label: 'Inactive' },
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
                data={plans}
                getRowKey={(plan) => plan.id}
                loading={loading}
                emptyTitle="No plans found"
                emptyDescription="Create a subscription plan to make it available for users."
            />

            {isFormOpen && (
                <PlanFormModal
                    plan={selectedPlan}
                    onClose={() => setIsFormOpen(false)}
                    onSuccess={() => {
                        setIsFormOpen(false);
                        fetchPlans();
                    }}
                />
            )}

            <ConfirmDialog
                open={Boolean(deactivatePlanId)}
                onOpenChange={(open) => !open && setDeactivatePlanId(null)}
                title="Deactivate plan?"
                description="This plan will no longer be active for new subscriptions."
                confirmLabel="Deactivate"
                destructive
                onConfirm={handleDelete}
            />
        </div>
    );
}
