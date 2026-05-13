'use client';

import { useState, useEffect } from 'react';
import { Button, Input, Select, Textarea, Dialog } from '@/components/ui';
import type { Plan, CreatePlanDto, UpdatePlanDto } from '@/lib/types/plan';
import { X, Plus, Trash2 } from 'lucide-react';

interface Props {
    plan: Plan | null;
    onClose: () => void;
    onSuccess: () => void;
}

export function PlanFormModal({ plan, onClose, onSuccess }: Props) {
    const [loading, setLoading] = useState(false);
    const [nameEn, setNameEn] = useState(plan?.name?.en || '');
    const [nameAr, setNameAr] = useState(plan?.name?.ar || '');
    const [descriptionEn, setDescriptionEn] = useState(plan?.description?.en || '');
    const [descriptionAr, setDescriptionAr] = useState(plan?.description?.ar || '');
    const [monthlyPrice, setMonthlyPrice] = useState(plan?.monthlyPrice?.toString() || '0');
    const [yearlyPrice, setYearlyPrice] = useState(plan?.yearlyPrice?.toString() || '0');
    const [currency, setCurrency] = useState(plan?.currency || 'SAR');
    const [discount, setDiscount] = useState(plan?.discount?.toString() || '0');
    const [maxWorkouts, setMaxWorkouts] = useState(plan?.maxWorkoutsLimit?.toString() || '0');
    const [maxExercises, setMaxExercises] = useState(plan?.maxExercisesLimit?.toString() || '0');
    const [freeSessions, setFreeSessions] = useState(plan?.freeDoctorSessionsLimit?.toString() || '0');
    const [monthlyGooglePlayProductId, setMonthlyGooglePlayProductId] = useState(plan?.monthlyGooglePlayProductId || '');
    const [yearlyGooglePlayProductId, setYearlyGooglePlayProductId] = useState(plan?.yearlyGooglePlayProductId || '');
    const [isActive, setIsActive] = useState(plan ? plan.isActive : true);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);

        try {
            const payload: CreatePlanDto | UpdatePlanDto = {
                name: { en: nameEn, ar: nameAr },
                description: { en: descriptionEn, ar: descriptionAr },
                monthlyPrice: parseFloat(monthlyPrice),
                yearlyPrice: parseFloat(yearlyPrice),
                currency: currency.trim() || 'SAR',
                discount: parseFloat(discount),
                maxWorkoutsLimit: parseInt(maxWorkouts, 10),
                maxExercisesLimit: parseInt(maxExercises, 10),
                freeDoctorSessionsLimit: parseInt(freeSessions, 10),
                monthlyGooglePlayProductId: monthlyGooglePlayProductId.trim() || null,
                yearlyGooglePlayProductId: yearlyGooglePlayProductId.trim() || null,
                isActive,
            };

            const url = plan ? `/api/admin/plans/${plan.id}` : '/api/admin/plans';
            const method = plan ? 'PATCH' : 'POST';

            const res = await fetch(url, {
                method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(payload),
            });

            if (!res.ok) {
                throw new Error('Failed to save plan');
            }

            onSuccess();
        } catch (error) {
            console.error('Error saving plan:', error);
            alert('Failed to save plan. Please check the console for more details.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 overflow-y-auto w-full">
            <div className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[90vh] flex flex-col my-8">
                <div className="flex items-center justify-between p-6 border-b border-gray-200 sticky top-0 bg-white z-10">
                    <h2 className="text-xl font-bold text-gray-900">
                        {plan ? 'Edit Plan' : 'Create New Plan'}
                    </h2>
                    <button
                        onClick={onClose}
                        className="text-gray-400 hover:text-gray-500 transition-colors"
                    >
                        <X className="h-6 w-6" />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="p-6 space-y-6 overflow-y-auto">
                    {/* Names */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Name (English)</label>
                            <Input value={nameEn} onChange={(e) => setNameEn(e.target.value)} required />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Name (Arabic)</label>
                            <Input value={nameAr} onChange={(e) => setNameAr(e.target.value)} required />
                        </div>
                    </div>

                    {/* Descriptions */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Description (English)</label>
                            <Textarea value={descriptionEn} onChange={(e) => setDescriptionEn(e.target.value)} rows={3} />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Description (Arabic)</label>
                            <Textarea value={descriptionAr} onChange={(e) => setDescriptionAr(e.target.value)} rows={3} />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Monthly Price</label>
                            <Input type="number" step="0.01" value={monthlyPrice} onChange={(e) => setMonthlyPrice(e.target.value)} required />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Yearly Price</label>
                            <Input type="number" step="0.01" value={yearlyPrice} onChange={(e) => setYearlyPrice(e.target.value)} required />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Discount (%)</label>
                            <Input type="number" step="0.01" value={discount} onChange={(e) => setDiscount(e.target.value)} />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Currency</label>
                            <Input value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} placeholder="SAR" />
                            <p className="mt-1 text-xs text-gray-500">Use SAR for Saudi MyFatoorah accounts.</p>
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Google Play Monthly Product ID</label>
                            <Input
                                value={monthlyGooglePlayProductId}
                                onChange={(e) => setMonthlyGooglePlayProductId(e.target.value)}
                                placeholder="pose_pro_monthly"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Google Play Yearly Product ID</label>
                            <Input
                                value={yearlyGooglePlayProductId}
                                onChange={(e) => setYearlyGooglePlayProductId(e.target.value)}
                                placeholder="pose_pro_yearly"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 border-t pt-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Max Workouts Limit</label>
                            <Input type="number" value={maxWorkouts} onChange={(e) => setMaxWorkouts(e.target.value)} placeholder="0 for unlimited" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Max Exercises Limit</label>
                            <Input type="number" value={maxExercises} onChange={(e) => setMaxExercises(e.target.value)} placeholder="0 for unlimited" />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">Free Dr. Sessions</label>
                            <Input type="number" value={freeSessions} onChange={(e) => setFreeSessions(e.target.value)} placeholder="0" />
                        </div>
                    </div>

                    <div className="flex items-center pt-2">
                        <input
                            type="checkbox"
                            id="plan_active"
                            checked={isActive}
                            onChange={(e) => setIsActive(e.target.checked)}
                            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
                        />
                        <label htmlFor="plan_active" className="ml-2 block text-sm text-gray-900 cursor-pointer">
                            Plan is currently active
                        </label>
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-gray-200 mt-6 sticky bottom-0 bg-white">
                        <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" disabled={loading}>
                            {loading ? 'Saving...' : plan ? 'Update Plan' : 'Create Plan'}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
