'use client';

import { useEffect, useState } from 'react';
import { usePermissions } from '@/hooks/usePermissions';
import { Button, Input, Select } from '@/components/ui';
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

    const handleDelete = async (id: string) => {
        if (!confirm('Are you sure you want to deactivate this plan?')) return;
        try {
            const res = await fetch(`/api/admin/plans/${id}`, { method: 'DELETE' });
            if (res.ok) {
                fetchPlans();
            }
        } catch (error) {
            console.error('Error deleting plan:', error);
        }
    };

    const getLocalizedName = (nameObj: Record<string, string>) => {
        if (!nameObj) return '';
        // try finding 'en', or 'ar', or just first key
        return nameObj.en || nameObj.ar || Object.values(nameObj)[0] || '';
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Plans</h1>
                    <p className="text-gray-600 mt-1">Manage subscription plans and features</p>
                </div>
                {(can('create', 'Plan') || isSuperAdmin) && (
                    <Button onClick={() => { setSelectedPlan(null); setIsFormOpen(true); }}>
                        <Plus className="h-4 w-4 mr-2" />
                        Create Plan
                    </Button>
                )}
            </div>

            <div className="bg-white p-4 rounded-lg shadow-sm border border-gray-200">
                <div className="flex flex-wrap gap-4 items-end">
                    <form onSubmit={handleSearch} className="flex-1 min-w-[200px]">
                        <label className="block text-sm font-medium text-gray-700 mb-1">Search</label>
                        <div className="flex gap-2">
                            <Input
                                type="text"
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                placeholder="Search by plan name..."
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
                                { value: 'inactive', label: 'Inactive' },
                            ]}
                        />
                    </div>
                </div>
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {loading ? (
                    <div className="p-8 text-center text-gray-500">Loading plans...</div>
                ) : plans.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">No plans found.</div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Plan Name</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Prices (Month/Year)</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Limits</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Status</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {plans.map((plan) => (
                                    <tr key={plan.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 text-center">
                                            <div className="font-medium text-gray-900">{getLocalizedName(plan.name)}</div>
                                            {plan.discount > 0 && (
                                                <div className="text-xs text-green-600 font-medium">Discount: {plan.discount}%</div>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                            <div>{plan.monthlyPrice} / {plan.yearlyPrice} {plan.currency || 'SAR'}</div>
                                            {(plan.monthlyGooglePlayProductId || plan.yearlyGooglePlayProductId) && (
                                                <div className="text-xs text-gray-500 mt-1">Google Play mapped</div>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                            <div>Workouts: {plan.maxWorkoutsLimit}</div>
                                            <div>Exercises: {plan.maxExercisesLimit}</div>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <span className={`inline-flex px-2 py-1 text-xs font-medium rounded-full ${plan.isActive ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}`}>
                                                {plan.isActive ? 'Active' : 'Inactive'}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <div className="flex justify-center gap-3 text-sm">
                                                {(can('update', 'Plan') || isSuperAdmin) && (
                                                    <button
                                                        onClick={() => { setSelectedPlan(plan); setIsFormOpen(true); }}
                                                        className="text-blue-600 hover:text-blue-800 font-medium"
                                                    >
                                                        Edit
                                                    </button>
                                                )}
                                                {(can('delete', 'Plan') || isSuperAdmin) && plan.isActive && (
                                                    <button
                                                        onClick={() => handleDelete(plan.id)}
                                                        className="text-red-600 hover:text-red-800 font-medium"
                                                    >
                                                        Deactivate
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
        </div>
    );
}
