'use client';

import { useState, useEffect } from 'react';
import { Button, Select, Input } from '@/components/ui';
import Link from 'next/link';
import { Booking } from '../page';

interface BookingDetailsModalProps {
    booking: Booking;
    onClose: () => void;
    onUpdate: (updates: Partial<Booking>) => void;
    isDoctorView: boolean;
}

export function BookingDetailsModal({ booking, onClose, onUpdate, isDoctorView }: BookingDetailsModalProps) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // States for editing
    const [status, setStatus] = useState(booking.status);
    const [notes, setNotes] = useState(booking.notes || '');

    // States for Follow Up
    const [showFollowUp, setShowFollowUp] = useState(false);

    interface FollowUpForm {
        id: string;
        date: string;
        slotIndex: string;
        notes: string;
        repeatWeekly: boolean;
        repeatUntil: string;
        availableSlots: { start: string; end: string }[];
        loadingSlots: boolean;
    }

    const [followUpForms, setFollowUpForms] = useState<FollowUpForm[]>([]);

    useEffect(() => {
        if (showFollowUp && followUpForms.length === 0) {
            setFollowUpForms([{
                id: Date.now().toString(),
                date: '',
                slotIndex: '',
                notes: '',
                repeatWeekly: false,
                repeatUntil: '',
                availableSlots: [],
                loadingSlots: false
            }]);
        } else if (!showFollowUp) {
            setFollowUpForms([]);
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [showFollowUp]);

    const handleDateChange = async (formId: string, newDate: string) => {
        setFollowUpForms(prev => prev.map(f => f.id === formId ? { ...f, date: newDate, loadingSlots: true, slotIndex: '' } : f));
        if (!newDate) {
            setFollowUpForms(prev => prev.map(f => f.id === formId ? { ...f, availableSlots: [], loadingSlots: false } : f));
            return;
        }
        try {
            const res = await fetch(`/api/admin/bookings/available-slots/${booking.admin.id}?date=${newDate}`);
            const data = await res.json();
            if (data.success) {
                setFollowUpForms(prev => prev.map(f => f.id === formId ? { ...f, availableSlots: data.data, loadingSlots: false } : f));
            } else {
                setFollowUpForms(prev => prev.map(f => f.id === formId ? { ...f, availableSlots: [], loadingSlots: false } : f));
            }
        } catch {
            setFollowUpForms(prev => prev.map(f => f.id === formId ? { ...f, availableSlots: [], loadingSlots: false } : f));
        }
    };

    const updateForm = (formId: string, field: keyof FollowUpForm, value: any) => {
        setFollowUpForms(prev => prev.map(f => f.id === formId ? { ...f, [field]: value } : f));
    };

    const addForm = () => {
        setFollowUpForms(prev => [...prev, {
            id: Date.now().toString() + Math.random().toString(),
            date: '',
            slotIndex: '',
            notes: '',
            repeatWeekly: false,
            repeatUntil: '',
            availableSlots: [],
            loadingSlots: false
        }]);
    };

    const removeForm = (formId: string) => {
        setFollowUpForms(prev => prev.filter(f => f.id !== formId));
    };

    const handleUpdateStatus = async () => {
        setLoading(true);
        setError(null);
        try {
            const endpoint = isDoctorView
                ? `/api/admin/bookings/${booking.id}/status`
                : `/api/admin/bookings/${booking.id}`;

            const payload = isDoctorView ? { status } : { status };

            const res = await fetch(endpoint, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            const data = await res.json();
            if (!res.ok || !data.success) throw new Error(data.error || data.message || 'Failed to update status');

            onUpdate({ status });
            // If doctor view, maybe show success toast, but for now just wait
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleUpdateNotes = async () => {
        setLoading(true);
        setError(null);
        try {
            const endpoint = isDoctorView
                ? `/api/admin/bookings/${booking.id}/notes`
                : `/api/admin/bookings/${booking.id}`;

            const payload = { notes };

            const res = await fetch(endpoint, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });

            const data = await res.json();
            if (!res.ok || !data.success) throw new Error(data.error || data.message || 'Failed to update notes');

            onUpdate({ notes });
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const handleCreateFollowUp = async () => {
        setLoading(true);
        setError(null);
        try {
            const payloads: { userId: string, startAt: string, endAt: string, notes: string }[] = [];

            for (const form of followUpForms) {
                if (!form.date || form.slotIndex === '') {
                    throw new Error('Please select a date and an available time slot for all sessions.');
                }
                const slot = form.availableSlots[parseInt(form.slotIndex)];
                if (!slot) throw new Error('Invalid slot selected');

                // Generate basic payload
                payloads.push({
                    userId: booking.user.id,
                    startAt: slot.start,
                    endAt: slot.end,
                    notes: form.notes
                });

                if (form.repeatWeekly && form.repeatUntil) {
                    const untilDate = new Date(form.repeatUntil);
                    let iteration = 1;
                    while (true) {
                        const nextStart = new Date(slot.start);
                        nextStart.setDate(nextStart.getDate() + (iteration * 7));
                        const nextEnd = new Date(slot.end);
                        nextEnd.setDate(nextEnd.getDate() + (iteration * 7));

                        if (nextStart > untilDate) break;

                        payloads.push({
                            userId: booking.user.id,
                            startAt: nextStart.toISOString(),
                            endAt: nextEnd.toISOString(),
                            notes: form.notes
                        });
                        iteration++;
                    }
                }
            }

            if (payloads.length === 0) {
                throw new Error('No sessions to create');
            }

            const promises = payloads.map(p => fetch('/api/admin/bookings/follow-up', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(p),
            }).then(async r => {
                const d = await r.json();
                if (!r.ok || !d.success) throw new Error(d.error || d.message || 'Failed to create a follow-up booking');
            }));

            await Promise.all(promises);

            alert(`Successfully created ${payloads.length} follow-up session(s)!`);
            setShowFollowUp(false);
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleString('en-GB', {
            year: 'numeric', month: 'long', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50 backdrop-blur-sm">
            <div className="bg-white rounded-xl shadow-xl w-full max-w-2xl overflow-hidden max-h-[90vh] flex flex-col">
                <div className="px-6 py-4 flex justify-between items-center border-b border-gray-100">
                    <h2 className="text-xl font-bold text-gray-900">Booking Details</h2>
                    <button onClick={onClose} className="text-gray-400 hover:text-gray-600">
                        <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                        </svg>
                    </button>
                </div>

                <div className="p-6 overflow-y-auto flex-1 space-y-6">
                    {error && (
                        <div className="p-3 text-sm text-red-600 bg-red-50 rounded-lg border border-red-100">
                            {error}
                        </div>
                    )}

                    {/* Info Grid */}
                    <div className="grid grid-cols-2 gap-6 bg-gray-50 p-4 rounded-lg">
                        <div>
                            <p className="text-sm text-gray-500 mb-1">Patient</p>
                            <p className="font-medium text-gray-900">{booking.user.name}</p>
                            <p className="text-sm text-gray-600">{booking.user.email}</p>
                        </div>
                        {!isDoctorView && (
                            <div>
                                <p className="text-sm text-gray-500 mb-1">Doctor</p>
                                <p className="font-medium text-gray-900">{booking.admin.name}</p>
                            </div>
                        )}
                        <div>
                            <p className="text-sm text-gray-500 mb-1">Schedule</p>
                            <p className="font-medium text-gray-900">{formatDate(booking.startAt)}</p>
                            <p className="text-sm text-gray-600">To: {formatDate(booking.endAt)}</p>
                        </div>
                        <div>
                            <p className="text-sm text-gray-500 mb-1">Payment</p>
                            <p className="font-medium text-gray-900">{booking.amount} EGP</p>
                            <span className={`inline-flex px-2 py-0.5 text-xs font-medium rounded-full mt-1 ${booking.paymentStatus === 'paid' ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
                                }`}>
                                {booking.paymentStatus?.toUpperCase() || 'UNPAID'}
                            </span>
                        </div>
                        <div>
                            <p className="text-sm text-gray-500 mb-1">Booked By</p>
                            <p className="font-medium text-gray-900 uppercase text-xs tracking-wider bg-gray-100/50 px-2 py-1 rounded inline-block">
                                {booking.madeByType || 'System'}
                            </p>
                        </div>
                    </div>

                    <div className="space-y-4">
                        <h3 className="font-semibold text-gray-900 border-b pb-2">Actions & Updates</h3>

                        <div className="flex items-end gap-3">
                            <div className="flex-1">
                                <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                                <Select
                                    value={status}
                                    onChange={(e) => setStatus(e.target.value)}
                                    options={[
                                        ...(!isDoctorView ? [
                                            { value: 'payment_pending', label: 'Payment Pending' },
                                            { value: 'pending', label: 'Pending' }
                                        ] : []),
                                        { value: 'confirmed', label: 'Confirmed' },
                                        { value: 'completed', label: 'Completed' },
                                        { value: 'canceled', label: 'Cancelled' },
                                    ]}
                                />
                            </div>
                            <Button
                                onClick={handleUpdateStatus}
                                disabled={loading || status === booking.status}
                                variant="outline"
                            >
                                Update Status
                            </Button>
                        </div>

                        <div className="pt-2">
                            <label className="block text-sm font-medium text-gray-700 mb-1">Medical/Admin Notes</label>
                            <textarea
                                className="w-full border-gray-300 rounded-lg shadow-sm focus:border-blue-500 focus:ring-blue-500 sm:text-sm p-3 min-h-[100px] border text-gray-900 font-medium"
                                value={notes}
                                onChange={(e) => setNotes(e.target.value)}
                                placeholder="Add session notes or administrative remarks..."
                            />
                            <div className="flex justify-end mt-2">
                                <Button
                                    onClick={handleUpdateNotes}
                                    disabled={loading || notes === (booking.notes || '')}
                                    variant="outline"
                                >
                                    Save Notes
                                </Button>
                            </div>
                        </div>

                        {isDoctorView && booking.status === 'completed' && (
                            <div className="pt-4 border-t border-gray-100 mt-4 flex flex-col gap-3">
                                <Link
                                    href={`/admin/booking-reports/new?bookingId=${booking.id}`}
                                    className="flex items-center justify-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-bold text-sm"
                                >
                                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
                                    </svg>
                                    Write Medical Report
                                </Link>

                                {!showFollowUp ? (
                                    <Button
                                        onClick={() => setShowFollowUp(true)}
                                        variant="outline"
                                        className="w-full justify-center"
                                    >
                                        + Schedule Follow-up Appointment
                                    </Button>
                                ) : (
                                    <div className="bg-blue-50 p-4 rounded-lg space-y-4 border border-blue-100">
                                        <div className="flex items-center justify-between">
                                            <h4 className="font-semibold text-blue-900">New Follow-up Booking</h4>
                                            <Button type="button" size="sm" onClick={addForm} variant="outline" className="border-blue-200 text-blue-700 bg-white hover:bg-blue-100 h-8 text-xs py-1 px-3">
                                                + Add Another Session
                                            </Button>
                                        </div>

                                        <div className="space-y-4">
                                            {followUpForms.map((form, index) => (
                                                <div key={form.id} className="relative p-4 bg-white rounded-lg border border-blue-100 shadow-sm space-y-4">
                                                    {followUpForms.length > 1 && (
                                                        <button
                                                            onClick={() => removeForm(form.id)}
                                                            className="absolute top-2 right-2 text-red-500 hover:text-red-700 bg-red-50 hover:bg-red-100 w-6 h-6 flex items-center justify-center rounded-md transition-colors"
                                                            title="Remove this session"
                                                        >
                                                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                                            </svg>
                                                        </button>
                                                    )}

                                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                                        <div>
                                                            <label className="block text-sm font-medium text-blue-800 mb-1">Date</label>
                                                            <Input
                                                                type="date"
                                                                value={form.date}
                                                                onChange={(e) => handleDateChange(form.id, e.target.value)}
                                                            />
                                                        </div>

                                                        <div>
                                                            <label className="block text-sm font-medium text-blue-800 mb-1">Available Time Slots</label>
                                                            <Select
                                                                value={form.slotIndex}
                                                                onChange={(e) => updateForm(form.id, 'slotIndex', e.target.value)}
                                                                disabled={!form.date || form.loadingSlots || form.availableSlots.length === 0}
                                                                options={[
                                                                    { value: '', label: !form.date ? 'Select a date first...' : form.loadingSlots ? 'Loading slots...' : form.availableSlots.length === 0 ? 'No slots available' : 'Choose a time...' },
                                                                    ...form.availableSlots.map((slot, i) => ({
                                                                        value: i.toString(),
                                                                        label: `${new Date(slot.start).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true })} - ${new Date(slot.end).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true })}`
                                                                    }))
                                                                ]}
                                                            />
                                                        </div>
                                                    </div>

                                                    <div>
                                                        <label className="block text-sm font-medium text-blue-800 mb-1">Notes</label>
                                                        <Input
                                                            type="text"
                                                            placeholder="Reason for follow-up..."
                                                            value={form.notes}
                                                            onChange={(e) => updateForm(form.id, 'notes', e.target.value)}
                                                        />
                                                    </div>

                                                    <div className="bg-blue-50/50 px-3 py-2 rounded-lg border border-blue-50 flex items-center gap-4">
                                                        <label className="flex items-center gap-2 cursor-pointer text-sm font-medium text-blue-800 select-none">
                                                            <input
                                                                type="checkbox"
                                                                className="rounded border-blue-300 text-blue-600 focus:ring-blue-500 w-4 h-4 cursor-pointer"
                                                                checked={form.repeatWeekly}
                                                                onChange={(e) => updateForm(form.id, 'repeatWeekly', e.target.checked)}
                                                            />
                                                            Repeat Weekly
                                                        </label>

                                                        {form.repeatWeekly && (
                                                            <div className="flex items-center gap-2 border-l border-blue-200 pl-4">
                                                                <span className="text-sm font-medium text-blue-800">Until</span>
                                                                <Input
                                                                    type="date"
                                                                    className="w-40 h-8 text-sm"
                                                                    value={form.repeatUntil}
                                                                    onChange={(e) => updateForm(form.id, 'repeatUntil', e.target.value)}
                                                                />
                                                            </div>
                                                        )}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>

                                        <div className="flex justify-end gap-2 pt-4 border-t border-blue-100">
                                            <Button
                                                variant="outline"
                                                onClick={() => setShowFollowUp(false)}
                                                disabled={loading}
                                            >
                                                Cancel
                                            </Button>
                                            <Button
                                                onClick={handleCreateFollowUp}
                                                disabled={loading}
                                            >
                                                {loading ? 'Scheduling...' : 'Confirm Follow-up(s)'}
                                            </Button>
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>

                <div className="px-6 py-4 bg-gray-50 border-t border-gray-100 flex justify-end">
                    <Button variant="outline" onClick={onClose}>Close</Button>
                </div>
            </div>
        </div>
    );
}
