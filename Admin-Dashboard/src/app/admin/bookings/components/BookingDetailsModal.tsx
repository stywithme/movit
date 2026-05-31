'use client';

import { useState, useEffect } from 'react';
import { toast } from 'sonner';
import {
    Badge,
    Button,
    Checkbox,
    Dialog,
    DialogBody,
    DialogContent,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    Input,
    Select,
    Textarea,
} from '@/components/ui';
import { StatusBadge } from '@/components/common';
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
    const [sessionUrl, setSessionUrl] = useState(booking.sessionUrl || '');

    useEffect(() => {
        setStatus(booking.status);
        setNotes(booking.notes || '');
        setSessionUrl(booking.sessionUrl || '');
        setError(null);
    }, [booking.id, booking.status, booking.notes, booking.sessionUrl]);

    const VALID_TRANSITIONS: Record<string, { value: string; label: string }[]> = {
        payment_pending: [
            { value: 'pending', label: 'Pending (Payment Received)' },
            { value: 'canceled', label: 'Cancelled' },
        ],
        pending: [
            { value: 'confirmed', label: 'Confirmed' },
            { value: 'canceled', label: 'Cancelled' },
        ],
        confirmed: [
            { value: 'completed', label: 'Completed' },
            { value: 'canceled', label: 'Cancelled' },
        ],
        completed: [],
        canceled: [],
    };

    const getStatusOptions = () => {
        const currentLabel = booking.status.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
        return [
            { value: booking.status, label: `${currentLabel} (Current)` },
            ...(VALID_TRANSITIONS[booking.status] || []),
        ];
    };

    const isTerminalStatus = booking.status === 'completed' || booking.status === 'canceled';

    // States for Follow Up
    const [showFollowUp, setShowFollowUp] = useState(false);

    interface FollowUpForm {
        id: string;
        date: string;
        slotIndex: string;
        notes: string;
        repeatWeekly: boolean;
        repeatUntil: string;
        availableSlots: { startAt: string; endAt: string; durationMinutes?: number }[];
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

    const handleUpdateSessionUrl = async () => {
        setLoading(true);
        setError(null);
        try {
            const payload: Record<string, unknown> = {};
            if (sessionUrl.trim()) payload.sessionUrl = sessionUrl.trim();
            const res = await fetch(`/api/admin/bookings/${booking.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload),
            });
            const data = await res.json();
            if (!res.ok || !data.success) throw new Error(data.error || data.message || 'Failed to update session URL');
            onUpdate({ sessionUrl: sessionUrl.trim() || null } as any);
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
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
                    startAt: slot.startAt,
                    endAt: slot.endAt,
                    notes: form.notes
                });

                    if (form.repeatWeekly && form.repeatUntil) {
                    const untilDate = new Date(form.repeatUntil);
                    let iteration = 1;
                    while (true) {
                        const nextStart = new Date(slot.startAt);
                        nextStart.setDate(nextStart.getDate() + (iteration * 7));
                        const nextEnd = new Date(slot.endAt);
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

            toast.success(`Successfully created ${payloads.length} follow-up session(s)!`);
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
        <Dialog open onOpenChange={(open) => !open && onClose()}>
            <DialogContent size="lg">
                <DialogHeader>
                    <DialogTitle>Booking Details</DialogTitle>
                </DialogHeader>

                <DialogBody className="space-y-6">
                    {error && (
                        <div className="rounded-lg border bg-destructive/10 p-3 text-sm text-destructive">
                            {error}
                        </div>
                    )}

                    {/* Info Grid */}
                    <div className="grid grid-cols-2 gap-6 rounded-lg bg-muted/50 p-4">
                        <div>
                            <p className="mb-1 text-sm text-muted-foreground">Patient</p>
                            <p className="font-medium">{booking.user.name}</p>
                            <p className="text-sm text-muted-foreground">{booking.user.email}</p>
                        </div>
                        {!isDoctorView && (
                            <div>
                                <p className="mb-1 text-sm text-muted-foreground">Doctor</p>
                                <p className="font-medium">{booking.admin.name}</p>
                            </div>
                        )}
                        <div>
                            <p className="mb-1 text-sm text-muted-foreground">Schedule</p>
                            <p className="font-medium">{formatDate(booking.startAt)}</p>
                            <p className="text-sm text-muted-foreground">To: {formatDate(booking.endAt)}</p>
                        </div>
                        <div>
                            <p className="mb-1 text-sm text-muted-foreground">Payment</p>
                            <p className="font-medium">{booking.amount} SAR</p>
                            <StatusBadge status={booking.paymentStatus === 'paid' ? 'completed' : 'failed'} label={booking.paymentStatus?.toUpperCase() || 'UNPAID'} />
                        </div>
                        <div>
                            <p className="mb-1 text-sm text-muted-foreground">Booked By</p>
                            <Badge variant="outline">{booking.madeByType || 'System'}</Badge>
                        </div>
                        {booking.isRescheduled && (
                            <div>
                                <Badge variant="purple">Rescheduled</Badge>
                            </div>
                        )}
                    </div>

                    {/* Payment summary block (for payment_pending / visibility) */}
                    {booking.status === 'payment_pending' && booking.paymentInfo && (
                        <div className="space-y-2 rounded-lg border bg-warning/10 p-4">
                            <h3 className="text-sm font-semibold">Payment Checkout</h3>
                            <div className="grid grid-cols-2 gap-2 text-sm">
                                <p><span className="text-muted-foreground">Checkout ID:</span> <code className="rounded bg-background px-1 text-xs">{booking.paymentInfo.checkoutId.slice(0, 8)}...</code></p>
                                <p><span className="text-muted-foreground">Status:</span> <span className="font-medium">{booking.paymentInfo.status}</span></p>
                                <p><span className="text-muted-foreground">Total:</span> {booking.paymentInfo.totalAmount} {booking.paymentInfo.currency}</p>
                                <p><span className="text-muted-foreground">Payment URL:</span> {booking.paymentInfo.paymentUrlPresent ? 'Yes' : 'No'}</p>
                                {booking.paymentInfo.lastError && (
                                    <p className="col-span-2 text-destructive">Last error: {booking.paymentInfo.lastError}</p>
                                )}
                                <p className="col-span-2 text-xs text-muted-foreground">Last updated: {new Date(booking.paymentInfo.lastUpdated).toLocaleString()}</p>
                            </div>
                        </div>
                    )}
                    {booking.status === 'payment_pending' && !booking.paymentInfo && (
                        <div className="rounded-lg border bg-muted/50 p-3 text-sm text-muted-foreground">
                            No active checkout. Customer can pay via the mobile app.
                        </div>
                    )}

                    {/* ── Quick Actions ── */}
                    {(() => {
                        const latestReport = [...(booking.reports || [])].sort(
                            (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
                        )[0];
                        const canJoin = Boolean(booking.allowedActions?.canJoin && booking.sessionUrl);
                        const canWriteReport = isDoctorView && booking.status === 'completed' && !latestReport;
                        const canViewReport = Boolean(latestReport);

                        if (!canJoin && !canWriteReport && !canViewReport) return null;

                        return (
                            <div className="flex flex-wrap gap-2 pt-1">
                                {canJoin && booking.sessionUrl && (
                                    <a
                                        href={booking.sessionUrl}
                                        target="_blank"
                                        rel="noreferrer"
                                        className="inline-flex items-center gap-1.5 rounded-lg bg-success px-4 py-2 text-sm font-semibold text-success-foreground transition-colors hover:bg-success/90"
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.276A1 1 0 0121 8.723v6.554a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
                                        </svg>
                                        Join Session
                                    </a>
                                )}
                                {canWriteReport && (
                                    <Link
                                        href={`/admin/booking-reports/new?bookingId=${booking.id}`}
                                        className="inline-flex items-center gap-1.5 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                                        </svg>
                                        Write Report
                                    </Link>
                                )}
                                {canViewReport && latestReport && (
                                    <Link
                                        href={`/admin/booking-reports/${latestReport.id}`}
                                        className="inline-flex items-center gap-1.5 rounded-lg border bg-muted px-4 py-2 text-sm font-semibold transition-colors hover:bg-muted/80"
                                    >
                                        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
                                        </svg>
                                        View Report
                                    </Link>
                                )}
                            </div>
                        );
                    })()}

                    <div className="space-y-4">
                        <h3 className="border-b pb-2 font-semibold">Actions & Updates</h3>

                        {!isTerminalStatus && (
                            <div className="flex items-end gap-3">
                                <div className="flex-1">
                                    <label className="mb-1 block text-sm font-medium">Status</label>
                                    <Select
                                        value={status}
                                        onChange={(e) => setStatus(e.target.value)}
                                        options={getStatusOptions()}
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
                        )}

                        {!isDoctorView && !isTerminalStatus && (
                            <div className="flex items-end gap-3">
                                <div className="flex-1">
                                    <label className="mb-1 block text-sm font-medium">
                                        Session URL (Zoom / Google Meet)
                                        {booking.sessionUrl && <span className="ml-1 text-xs text-success">(Auto-generated)</span>}
                                    </label>
                                    <Input
                                        type="url"
                                        value={sessionUrl}
                                        onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSessionUrl(e.target.value)}
                                        placeholder="https://meet.google.com/... or https://zoom.us/j/..."
                                    />
                                </div>
                                <Button
                                    onClick={handleUpdateSessionUrl}
                                    disabled={loading || sessionUrl === (booking.sessionUrl || '')}
                                    variant="outline"
                                >
                                    Save URL
                                </Button>
                            </div>
                        )}

                        <div className="pt-2">
                            <label className="mb-1 block text-sm font-medium">Medical/Admin Notes</label>
                            <Textarea
                                className="min-h-[100px]"
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
                            <div className="mt-4 flex flex-col gap-3 border-t pt-4">
                                <Link
                                    href={`/admin/booking-reports/new?bookingId=${booking.id}`}
                                    className="flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-sm font-semibold text-primary-foreground transition-colors hover:bg-primary/90"
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
                                    <div className="space-y-4 rounded-lg border bg-muted/50 p-4">
                                        <div className="flex items-center justify-between">
                                            <h4 className="font-semibold">New Follow-up Booking</h4>
                                            <Button type="button" size="sm" onClick={addForm} variant="outline" className="h-8 px-3 py-1 text-xs">
                                                + Add Another Session
                                            </Button>
                                        </div>

                                        <div className="space-y-4">
                                            {followUpForms.map((form, index) => (
                                                <div key={form.id} className="relative space-y-4 rounded-lg border bg-background p-4 shadow-sm">
                                                    {followUpForms.length > 1 && (
                                                        <button
                                                            onClick={() => removeForm(form.id)}
                                                            className="absolute right-2 top-2 flex h-6 w-6 items-center justify-center rounded-md bg-destructive/10 text-destructive transition-colors hover:bg-destructive/20"
                                                            title="Remove this session"
                                                        >
                                                            <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                                            </svg>
                                                        </button>
                                                    )}

                                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                                        <div>
                                                            <label className="mb-1 block text-sm font-medium">Date</label>
                                                            <Input
                                                                type="date"
                                                                value={form.date}
                                                                onChange={(e) => handleDateChange(form.id, e.target.value)}
                                                            />
                                                        </div>

                                                        <div>
                                                            <label className="mb-1 block text-sm font-medium">Available Time Slots</label>
                                                            <Select
                                                                value={form.slotIndex}
                                                                onChange={(e) => updateForm(form.id, 'slotIndex', e.target.value)}
                                                                disabled={!form.date || form.loadingSlots || form.availableSlots.length === 0}
                                                                options={[
                                                                    { value: '', label: !form.date ? 'Select a date first...' : form.loadingSlots ? 'Loading slots...' : form.availableSlots.length === 0 ? 'No slots available' : 'Choose a time...' },
                                                                    ...form.availableSlots.map((slot, i) => ({
                                                                        value: i.toString(),
                                                                        label: `${new Date(slot.startAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true })} - ${new Date(slot.endAt).toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: true })}`
                                                                    }))
                                                                ]}
                                                            />
                                                        </div>
                                                    </div>

                                                    <div>
                                                        <label className="mb-1 block text-sm font-medium">Notes</label>
                                                        <Input
                                                            type="text"
                                                            placeholder="Reason for follow-up..."
                                                            value={form.notes}
                                                            onChange={(e) => updateForm(form.id, 'notes', e.target.value)}
                                                        />
                                                    </div>

                                                    <div className="flex items-center gap-4 rounded-lg border bg-muted/40 px-3 py-2">
                                                        <label className="flex cursor-pointer select-none items-center gap-2 text-sm font-medium">
                                                            <Checkbox
                                                                checked={form.repeatWeekly}
                                                                onCheckedChange={(checked) => updateForm(form.id, 'repeatWeekly', Boolean(checked))}
                                                            />
                                                            Repeat Weekly
                                                        </label>

                                                        {form.repeatWeekly && (
                                                            <div className="flex items-center gap-2 border-l pl-4">
                                                                <span className="text-sm font-medium">Until</span>
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

                                        <div className="flex justify-end gap-2 border-t pt-4">
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
                </DialogBody>

                <DialogFooter>
                    <Button variant="outline" onClick={onClose}>Close</Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
