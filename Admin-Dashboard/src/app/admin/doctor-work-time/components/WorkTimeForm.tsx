'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Input, Select, Button, Card } from '@/components/ui';

interface Admin {
    id: string;
    name: string;
    email: string;
}

interface DoctorWorkTime {
    id: string;
    day: string;
    startTime: string;
    endTime: string;
}

interface WorkTimeFormProps {
    initialData?: DoctorWorkTime[]; // Array of times for the selected admin
    adminId?: string; // Preselected admin ID from URL
    isEditing?: boolean;
}

const DAYS_OF_WEEK = [
    'Saturday',
    'Sunday',
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
];

interface TimeSlot {
    startTime: string;
    endTime: string;
}

type WeeklySchedule = Record<string, TimeSlot[]>;

export function WorkTimeForm({ initialData = [], adminId: propAdminId, isEditing = false }: WorkTimeFormProps) {
    const router = useRouter();
    const [loading, setLoading] = useState(false);
    const [doctors, setDoctors] = useState<Admin[]>([]);
    const [error, setError] = useState<string | null>(null);

    const [adminId, setAdminId] = useState<string>(propAdminId || '');

    // Initialize schedule state
    const [schedule, setSchedule] = useState<WeeklySchedule>(() => {
        const initialSchedule: WeeklySchedule = {};
        DAYS_OF_WEEK.forEach(day => {
            initialSchedule[day] = [];
        });

        // Populate with existing data if editing
        if (initialData.length > 0) {
            initialData.forEach(wt => {
                if (initialSchedule[wt.day]) {
                    initialSchedule[wt.day].push({
                        startTime: wt.startTime,
                        endTime: wt.endTime
                    });
                } else {
                    initialSchedule[wt.day] = [{
                        startTime: wt.startTime,
                        endTime: wt.endTime
                    }];
                }
            });
            // If adminId isn't set but we have initial data, use its adminId
            if (!propAdminId && (initialData[0] as any).admin?.id) {
                setAdminId((initialData[0] as any).admin.id);
            } else if (!propAdminId && (initialData[0] as any).adminId) {
                setAdminId((initialData[0] as any).adminId);
            }
        }

        return initialSchedule;
    });

    useEffect(() => {
        const fetchDoctors = async () => {
            try {
                const res = await fetch('/api/admins?isDoctor=true&limit=100');
                const data = await res.json();
                if (data.success) {
                    setDoctors(data.data);
                    // Auto-select first doctor if creating and no adminId
                    if (!adminId && data.data.length > 0 && !isEditing) {
                        setAdminId(data.data[0].id);
                    }
                }
            } catch (err) {
                console.error('Failed to fetch doctors', err);
            }
        };
        fetchDoctors();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const addTimeSlot = (day: string) => {
        setSchedule(prev => ({
            ...prev,
            [day]: [...prev[day], { startTime: '09:00', endTime: '17:00' }]
        }));
    };

    const updateTimeSlot = (day: string, index: number, field: keyof TimeSlot, value: string) => {
        setSchedule(prev => {
            const newDaySlots = [...prev[day]];
            newDaySlots[index] = { ...newDaySlots[index], [field]: value };
            return { ...prev, [day]: newDaySlots };
        });
        setError(null);
    };

    const removeTimeSlot = (day: string, index: number) => {
        setSchedule(prev => {
            const newDaySlots = prev[day].filter((_, i) => i !== index);
            return { ...prev, [day]: newDaySlots };
        });
    };

    const validateSchedule = (): boolean => {
        for (const day of DAYS_OF_WEEK) {
            const slots = schedule[day];
            for (let i = 0; i < slots.length; i++) {
                const s = slots[i];
                if (s.startTime >= s.endTime) {
                    setError(`${day}: End time must be after start time for all slots.`);
                    return false;
                }
            }
            // Optional: validate overlapping time within the same day
            slots.sort((a, b) => a.startTime.localeCompare(b.startTime));
            for (let i = 0; i < slots.length - 1; i++) {
                if (slots[i].endTime > slots[i + 1].startTime) {
                    setError(`${day}: Time slots cannot overlap.`);
                    return false;
                }
            }
        }
        return true;
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        if (!adminId) {
            setError('Please select a doctor');
            setLoading(false);
            return;
        }

        if (!validateSchedule()) {
            setLoading(false);
            return;
        }

        // Flatten schedule into array
        const workTimes = Object.entries(schedule).flatMap(([day, slots]) =>
            slots.map(slot => ({
                day,
                startTime: slot.startTime,
                endTime: slot.endTime
            }))
        );

        try {
            const res = await fetch('/api/admin/doctor-work-time/bulk', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ adminId, workTimes }),
            });

            const data = await res.json();

            if (!res.ok || !data.success) {
                throw new Error(data.message || data.error || 'Failed to save schedule');
            }

            router.push('/admin/doctor-work-time');
            router.refresh();
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <Card className="max-w-4xl p-6">
            <form onSubmit={handleSubmit} className="space-y-8">
                {error && (
                    <div className="p-4 bg-red-50 text-red-600 rounded-lg text-sm sticky top-4 z-10 shadow-sm border border-red-100">
                        {error}
                    </div>
                )}

                <div className="space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">
                            Doctor
                        </label>
                        <Select
                            value={adminId}
                            onChange={(e) => setAdminId(e.target.value)}
                            disabled={isEditing || doctors.length === 0}
                            options={[
                                { value: '', label: 'Select a doctor' },
                                ...doctors.map(d => ({ value: d.id, label: `${d.name} (${d.email})` }))
                            ]}
                            required
                        />
                        {doctors.length === 0 && !isEditing && (
                            <p className="text-sm text-gray-500 mt-1">Loading doctors...</p>
                        )}
                    </div>
                </div>

                <div className="space-y-6">
                    <h3 className="text-lg font-medium text-gray-900 border-b pb-2">Weekly Schedule</h3>

                    {DAYS_OF_WEEK.map(day => (
                        <div key={day} className="bg-gray-50 p-4 rounded-lg border border-gray-200 shadow-sm">
                            <div className="flex items-center justify-between mb-4">
                                <h4 className="font-semibold text-gray-800">{day}</h4>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => addTimeSlot(day)}
                                    className="text-xs"
                                >
                                    + Add Shift
                                </Button>
                            </div>

                            {schedule[day].length === 0 ? (
                                <p className="text-sm text-gray-500 italic">No shifts scheduled</p>
                            ) : (
                                <div className="space-y-3">
                                    {schedule[day].map((slot, index) => (
                                        <div key={index} className="flex items-center gap-4 bg-white p-3 rounded border border-gray-100 shadow-sm transition-all hover:border-gray-300">
                                            <div className="flex-1">
                                                <label className="block text-xs text-gray-500 mb-1">Start Time</label>
                                                <Input
                                                    type="time"
                                                    value={slot.startTime}
                                                    onChange={(e) => updateTimeSlot(day, index, 'startTime', e.target.value)}
                                                    required
                                                />
                                            </div>
                                            <div className="flex-1">
                                                <label className="block text-xs text-gray-500 mb-1">End Time</label>
                                                <Input
                                                    type="time"
                                                    value={slot.endTime}
                                                    onChange={(e) => updateTimeSlot(day, index, 'endTime', e.target.value)}
                                                    required
                                                />
                                            </div>
                                            <div className="pt-5">
                                                <button
                                                    type="button"
                                                    onClick={() => removeTimeSlot(day, index)}
                                                    className="p-2 text-red-500 hover:text-red-700 hover:bg-red-50 rounded transition-colors"
                                                    title="Remove Shift"
                                                >
                                                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                                    </svg>
                                                </button>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    ))}
                </div>

                <div className="flex justify-end gap-3 pt-6 border-t mt-8">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => router.back()}
                        disabled={loading}
                    >
                        Cancel
                    </Button>
                    <Button type="submit" disabled={loading || !adminId}>
                        {loading ? 'Saving Schedule...' : 'Save Schedule'}
                    </Button>
                </div>
            </form>
        </Card>
    );
}
