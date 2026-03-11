'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Input, Select, Button, Card } from '@/components/ui';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';

interface Admin {
    id: string;
    name: string;
    email: string;
}

interface CloseTimeFormData {
    adminId: string | null;
    fromDate: string;
    toDate: string;
    fromTime: string;
    toTime: string;
}

interface CloseTimeFormProps {
    initialData?: CloseTimeFormData & { id: string, admin?: Admin | null };
    isEditing?: boolean;
}

export function CloseTimeForm({ initialData, isEditing = false }: CloseTimeFormProps) {
    const router = useRouter();
    const { user } = useAuthStore();
    const { isSuperAdmin } = usePermissions();

    const [loading, setLoading] = useState(false);
    const [doctors, setDoctors] = useState<Admin[]>([]);
    const [error, setError] = useState<string | null>(null);

    // If doctor, auto-assign their own ID. If admin, default to global (null)
    const defaultAdminId = (user?.isDoctor && !isSuperAdmin) ? user.id : '';

    const [formData, setFormData] = useState<CloseTimeFormData>({
        adminId: initialData?.adminId || defaultAdminId,
        fromDate: initialData?.fromDate ? new Date(initialData.fromDate).toISOString().split('T')[0] : new Date().toISOString().split('T')[0],
        toDate: initialData?.toDate ? new Date(initialData.toDate).toISOString().split('T')[0] : new Date().toISOString().split('T')[0],
        fromTime: initialData?.fromTime || '00:00',
        toTime: initialData?.toTime || '23:59',
    });

    useEffect(() => {
        // Only fetch doctors if SuperAdmin / Admin
        if (!user?.isDoctor || isSuperAdmin) {
            const fetchDoctors = async () => {
                try {
                    const res = await fetch('/api/admins?isDoctor=true&limit=100');
                    const data = await res.json();
                    if (data.success) {
                        setDoctors(data.data);
                    }
                } catch (err) {
                    console.error('Failed to fetch doctors', err);
                }
            };
            fetchDoctors();
        }
    }, [user, isSuperAdmin]);

    const handleChange = (field: keyof CloseTimeFormData, value: string | null) => {
        setFormData((prev) => ({ ...prev, [field]: value }));
        setError(null);
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        if (formData.fromDate > formData.toDate) {
            setError('To Date must be after or equal to From Date');
            setLoading(false);
            return;
        }

        if (formData.fromDate === formData.toDate && formData.fromTime >= formData.toTime) {
            setError('To Time must be after From Time on the same day');
            setLoading(false);
            return;
        }

        try {
            const url = isEditing ? `/api/admin/close-time/${initialData?.id}` : '/api/admin/close-time';
            const method = isEditing ? 'PUT' : 'POST';

            // Ensure dates are parsed as ISODateStrings for the backend 
            // e.g. "2026-03-20T00:00:00Z"
            const payload = {
                ...formData,
                adminId: formData.adminId || null,
                fromDate: new Date(formData.fromDate).toISOString(),
                toDate: new Date(formData.toDate).toISOString(),
            };

            const res = await fetch(url, {
                method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(payload),
            });

            const data = await res.json();

            if (!res.ok || !data.success) {
                throw new Error(data.message || data.error || 'Failed to save close time');
            }

            router.push('/admin/close-time');
            router.refresh();
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card className="max-w-2xl p-6 bg-white shadow-sm border border-gray-100">
            <form onSubmit={handleSubmit} className="space-y-6">
                {error && (
                    <div className="p-4 bg-red-50 text-red-600 rounded-lg text-sm border border-red-100">
                        {error}
                    </div>
                )}

                <div className="space-y-5">
                    {(!user?.isDoctor || isSuperAdmin) && (
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                Targeted Doctor (Optional)
                            </label>
                            <Select
                                value={formData.adminId || ''}
                                onChange={(e) => handleChange('adminId', e.target.value || null)}
                                disabled={isEditing && !!initialData?.adminId}
                                options={[
                                    { value: '', label: 'Global (All Doctors)' },
                                    ...doctors.map(d => ({ value: d.id, label: `${d.name} (${d.email})` }))
                                ]}
                            />
                            <p className="text-xs text-gray-500 mt-1">Leave as Global to close the clinic entirely for this period.</p>
                        </div>
                    )}

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                From Date
                            </label>
                            <Input
                                type="date"
                                value={formData.fromDate}
                                onChange={(e) => handleChange('fromDate', e.target.value)}
                                required
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                To Date
                            </label>
                            <Input
                                type="date"
                                value={formData.toDate}
                                onChange={(e) => handleChange('toDate', e.target.value)}
                                required
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                From Time
                            </label>
                            <Input
                                type="time"
                                value={formData.fromTime}
                                onChange={(e) => handleChange('fromTime', e.target.value)}
                                required
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-medium text-gray-700 mb-1">
                                To Time
                            </label>
                            <Input
                                type="time"
                                value={formData.toTime}
                                onChange={(e) => handleChange('toTime', e.target.value)}
                                required
                            />
                        </div>
                    </div>
                </div>

                <div className="flex justify-end gap-3 pt-6 border-t border-gray-100">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => router.back()}
                        disabled={loading}
                    >
                        Cancel
                    </Button>
                    <Button type="submit" disabled={loading}>
                        {loading ? 'Saving...' : isEditing ? 'Update Close Time' : 'Create Close Time'}
                    </Button>
                </div>
            </form>
        </Card>
    );
}
