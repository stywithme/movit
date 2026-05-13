'use client';

import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter, DialogBody } from '@/components/ui/Dialog';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Label } from '@/components/ui/Label';
import { Select } from '@/components/ui/Select';
import { Textarea } from '@/components/ui/Textarea';
import { toast } from 'react-hot-toast';
import { Loader2 } from 'lucide-react';
import { format } from 'date-fns';

interface User {
    id: string;
    name: string;
    email: string;
}

interface Doctor {
    id: string;
    name: string;
}

interface Slot {
    startAt: string;
    endAt: string;
    durationMinutes?: number;
}

interface CreateBookingModalProps {
    onClose: () => void;
    onSuccess: () => void;
    initialDoctorId?: string;
    initialUserId?: string;
}

export function CreateBookingModal({ onClose, onSuccess, initialDoctorId, initialUserId }: CreateBookingModalProps) {
    const [users, setUsers] = useState<User[]>([]);
    const [doctors, setDoctors] = useState<Doctor[]>([]);
    const [slots, setSlots] = useState<Slot[]>([]);

    const [selectedUserId, setSelectedUserId] = useState(initialUserId || '');
    const [selectedDoctorId, setSelectedDoctorId] = useState(initialDoctorId || '');
    const [selectedDate, setSelectedDate] = useState('');
    const [selectedSlot, setSelectedSlot] = useState<string>('');
    const [notes, setNotes] = useState('');

    const [loadingUsers, setLoadingUsers] = useState(false);
    const [loadingDoctors, setLoadingDoctors] = useState(false);
    const [loadingSlots, setLoadingSlots] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        const today = format(new Date(), 'yyyy-MM-dd');
        setSelectedDate(today);
        fetchUsers();
        fetchDoctors(today);
    }, []);

    useEffect(() => {
        if (selectedDoctorId && selectedDate) {
            fetchSlots(selectedDoctorId, selectedDate);
        } else {
            setSlots([]);
        }
    }, [selectedDoctorId, selectedDate]);

    const fetchUsers = async () => {
        setLoadingUsers(true);
        try {
            const res = await fetch('/api/users?limit=100');
            const data = await res.json();
            if (data.success) setUsers(data.data);
        } catch (error) {
            console.error('Error fetching users:', error);
        } finally {
            setLoadingUsers(false);
        }
    };

    const fetchDoctors = async (date: string) => {
        if (!date) return;
        setLoadingDoctors(true);
        try {
            const res = await fetch(`/api/admin/bookings/available-doctors?date=${date}`);
            const data = await res.json();
            if (data.success) {
                let list = data.data || [];
                // If initialDoctorId is provided but not in the available list, we should still show it
                if (initialDoctorId && !list.find((d: any) => d.id === initialDoctorId)) {
                    list.push({ id: initialDoctorId, name: 'Current Doctor (Self)' });
                }
                setDoctors(list);
                // Reset selected doctor if not in the new list and not the initial one
                if (selectedDoctorId && !list.find((d: any) => d.id === selectedDoctorId) && selectedDoctorId !== initialDoctorId) {
                    setSelectedDoctorId('');
                }
            }
        } catch (error) {
            console.error('Error fetching doctors:', error);
        } finally {
            setLoadingDoctors(false);
        }
    };

    const fetchSlots = async (doctorId: string, date: string) => {
        setLoadingSlots(true);
        try {
            const res = await fetch(`/api/admin/bookings/available-slots/${doctorId}?date=${date}`);
            const data = await res.json();
            if (data.success) {
                setSlots(data.data);
                setSelectedSlot(''); // Reset slot when fetching new ones
            }
        } catch (error) {
            console.error('Error fetching slots:', error);
        } finally {
            setLoadingSlots(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedUserId || !selectedDoctorId || !selectedSlot) {
            toast.error('Please fill all required fields');
            return;
        }

        const slot = JSON.parse(selectedSlot) as Slot;
        setSubmitting(true);
        try {
            const res = await fetch('/api/admin/bookings', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    userId: selectedUserId,
                    adminId: selectedDoctorId,
                    startAt: slot.startAt,
                    endAt: slot.endAt,
                    notes,
                }),
            });
            const data = await res.json();
            if (data.success) {
                toast.success('Booking created successfully');
                onSuccess();
            } else {
                toast.error(data.error || 'Failed to create booking');
            }
        } catch (error) {
            toast.error('Failed to create booking');
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Dialog open onOpenChange={onClose}>
            <DialogContent className="sm:max-w-[800px]">
                <DialogHeader>
                    <DialogTitle className="text-xl font-bold text-black border-b pb-4">Create New Booking</DialogTitle>
                </DialogHeader>

                <form onSubmit={handleSubmit} className="flex flex-col h-full max-h-[85vh]">
                    <DialogBody className="space-y-8 py-8">
                        {/* Patient Selection */}
                        <div className="space-y-3">
                            <Label className="text-black font-bold text-sm tracking-tight">Patient / Client</Label>
                            <Select
                                value={selectedUserId}
                                onChange={(e) => setSelectedUserId(e.target.value)}
                                options={[
                                    { value: '', label: loadingUsers ? 'Loading patients...' : 'Select a patient...' },
                                    ...users?.map(u => ({ value: u.id, label: `${u.name} (${u.email})` })) || []
                                ]}
                                disabled={loadingUsers || !!initialUserId}
                                placeholder="Select a patient..."
                                className="mt-1"
                            />
                        </div>

                        {/* Date & Doctor Grid */}
                        <div className="grid grid-cols-2 gap-8 pt-2">
                            <div className="space-y-3">
                                <Label className="text-black font-bold text-sm tracking-tight">Preferred Date</Label>
                                <Input
                                    type="date"
                                    value={selectedDate}
                                    onChange={(e) => {
                                        setSelectedDate(e.target.value);
                                        fetchDoctors(e.target.value);
                                    }}
                                    className="w-full mt-1"
                                />
                            </div>
                            <div className="space-y-3">
                                <Label className="text-black font-bold text-sm tracking-tight">Doctor</Label>
                                <Select
                                    value={selectedDoctorId}
                                    onChange={(e) => setSelectedDoctorId(e.target.value)}
                                    options={[
                                        { value: '', label: loadingDoctors ? 'Loading doctors...' : (doctors.length > 0 ? 'Select a doctor...' : 'No doctors available for this date') },
                                        ...doctors.map(d => ({ value: d.id, label: d.name }))
                                    ]}
                                    disabled={loadingDoctors || !!initialDoctorId}
                                    placeholder="Select a doctor..."
                                    className="mt-1"
                                />
                                {!loadingDoctors && selectedDate && doctors.length === 0 && (
                                    <p className="text-xs text-red-500 font-medium">No doctors found working on this date.</p>
                                )}
                            </div>
                        </div>

                        {/* Slots */}
                        <div className="space-y-3 pt-2">
                            <Label className="text-black font-bold text-sm tracking-tight">Available Time Slots</Label>
                            <Select
                                value={selectedSlot}
                                onChange={(e) => setSelectedSlot(e.target.value)}
                                options={[
                                    { value: '', label: loadingSlots ? 'Loading slots...' : (slots.length > 0 ? 'Select a slot...' : (selectedDoctorId ? 'No slots available' : 'Select a doctor first')) },
                                    ...slots.map(s => ({
                                        value: JSON.stringify(s),
                                        label: `${format(new Date(s.startAt), 'HH:mm')} - ${format(new Date(s.endAt), 'HH:mm')}`
                                    }))
                                ]}
                                disabled={loadingSlots || !selectedDoctorId}
                                placeholder="Select a time slot..."
                                className="mt-1"
                            />
                            {!loadingSlots && selectedDoctorId && slots.length === 0 && (
                                <p className="text-xs text-orange-600 font-medium">No work hours or slots found for this doctor on the selected date.</p>
                            )}
                        </div>

                        {/* Notes */}
                        <div className="space-y-3 pt-2">
                            <Label className="text-black font-bold text-sm tracking-tight">Booking Notes (Optional)</Label>
                            <Textarea
                                placeholder="Add specific session requirements, clinical notes, or patient instructions..."
                                value={notes}
                                onChange={(e) => setNotes(e.target.value)}
                                className="text-black min-h-[120px] mt-1"
                            />
                        </div>
                    </DialogBody>

                    <DialogFooter className="py-6 bg-gray-50/50">
                        <Button type="button" variant="outline" onClick={onClose} disabled={submitting} className="px-10 h-11">
                            Cancel
                        </Button>
                        <Button
                            type="submit"
                            disabled={submitting || !selectedSlot}
                            className="px-10 h-11 bg-blue-600 hover:bg-blue-700 font-bold"
                        >
                            {submitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                            Create Booking Instance
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    );
}

