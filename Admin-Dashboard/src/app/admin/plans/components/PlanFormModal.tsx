'use client';

import { useState, useEffect } from 'react';
import { toast } from 'sonner';
import {
    Button,
    Checkbox,
    Dialog,
    DialogBody,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Input,
    Label,
    Textarea,
} from '@/components/ui';
import type { Plan, CreatePlanDto, UpdatePlanDto } from '@/lib/types/plan';

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
    const [maxWorkoutTemplates, setMaxWorkoutTemplates] = useState(plan?.maxWorkoutTemplatesLimit?.toString() || '0');
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
                maxWorkoutTemplatesLimit: parseInt(maxWorkoutTemplates, 10),
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
            toast.error('Failed to save plan. Please check the console for more details.');
        } finally {
            setLoading(false);
        }
    };

    return (
        <Dialog open onOpenChange={(open) => !open && onClose()}>
            <DialogContent size="lg">
                <DialogHeader>
                    <DialogTitle>{plan ? 'Edit Plan' : 'Create New Plan'}</DialogTitle>
                    <DialogDescription>Configure pricing, limits, store mapping, and availability.</DialogDescription>
                </DialogHeader>

                <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col">
                    <DialogBody className="space-y-6">
                    {/* Names */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label>Name (English)</Label>
                            <Input value={nameEn} onChange={(e) => setNameEn(e.target.value)} required />
                        </div>
                        <div className="space-y-2">
                            <Label>Name (Arabic)</Label>
                            <Input value={nameAr} onChange={(e) => setNameAr(e.target.value)} required />
                        </div>
                    </div>

                    {/* Descriptions */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="space-y-2">
                            <Label>Description (English)</Label>
                            <Textarea value={descriptionEn} onChange={(e) => setDescriptionEn(e.target.value)} rows={3} />
                        </div>
                        <div className="space-y-2">
                            <Label>Description (Arabic)</Label>
                            <Textarea value={descriptionAr} onChange={(e) => setDescriptionAr(e.target.value)} rows={3} />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="space-y-2">
                            <Label>Monthly Price</Label>
                            <Input type="number" step="0.01" value={monthlyPrice} onChange={(e) => setMonthlyPrice(e.target.value)} required />
                        </div>
                        <div className="space-y-2">
                            <Label>Yearly Price</Label>
                            <Input type="number" step="0.01" value={yearlyPrice} onChange={(e) => setYearlyPrice(e.target.value)} required />
                        </div>
                        <div className="space-y-2">
                            <Label>Discount (%)</Label>
                            <Input type="number" step="0.01" value={discount} onChange={(e) => setDiscount(e.target.value)} />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                        <div className="space-y-2">
                            <Label>Currency</Label>
                            <Input value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} placeholder="SAR" />
                            <p className="text-xs text-muted-foreground">Use SAR for Saudi MyFatoorah accounts.</p>
                        </div>
                        <div className="space-y-2">
                            <Label>Google Play Monthly Product ID</Label>
                            <Input
                                value={monthlyGooglePlayProductId}
                                onChange={(e) => setMonthlyGooglePlayProductId(e.target.value)}
                                placeholder="pose_pro_monthly"
                            />
                        </div>
                        <div className="space-y-2">
                            <Label>Google Play Yearly Product ID</Label>
                            <Input
                                value={yearlyGooglePlayProductId}
                                onChange={(e) => setYearlyGooglePlayProductId(e.target.value)}
                                placeholder="pose_pro_yearly"
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 gap-4 border-t pt-4 md:grid-cols-3">
                        <div className="space-y-2">
                            <Label>Max Workout Templates Limit</Label>
                            <Input type="number" value={maxWorkoutTemplates} onChange={(e) => setMaxWorkoutTemplates(e.target.value)} placeholder="0 for unlimited" />
                        </div>
                        <div className="space-y-2">
                            <Label>Max Exercises Limit</Label>
                            <Input type="number" value={maxExercises} onChange={(e) => setMaxExercises(e.target.value)} placeholder="0 for unlimited" />
                        </div>
                        <div className="space-y-2">
                            <Label>Free Dr. Sessions</Label>
                            <Input type="number" value={freeSessions} onChange={(e) => setFreeSessions(e.target.value)} placeholder="0" />
                        </div>
                    </div>

                    <div className="flex items-center gap-3 pt-2">
                        <Checkbox
                            checked={isActive}
                            onCheckedChange={(checked) => setIsActive(Boolean(checked))}
                        />
                        <Label>Plan is currently active</Label>
                    </div>
                    </DialogBody>

                    <DialogFooter>
                        <Button type="button" variant="outline" onClick={onClose} disabled={loading}>
                            Cancel
                        </Button>
                        <Button type="submit" loading={loading}>
                            {plan ? 'Update Plan' : 'Create Plan'}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}
