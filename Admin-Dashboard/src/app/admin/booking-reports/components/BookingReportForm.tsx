'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Input, Select, Button, Card, Textarea } from '@/components/ui';
import { Loader2 } from 'lucide-react';

interface Booking {
    id: string;
    user: { name: string; email: string };
    startAt: string;
    status: string;
    reports?: { id: string }[];
}

interface ReportFormData {
    bookingId: string;
    content: {
        diagnosis: string;
        prescription: string;
        notes?: string;
    };
    attachments: string[];
}

interface BookingReportFormProps {
    initialData?: ReportFormData & { id: string };
    isEditing?: boolean;
}

export function BookingReportForm({ initialData, isEditing = false }: BookingReportFormProps) {
    const router = useRouter();
    const searchParams = useSearchParams();
    const defaultBookingId = searchParams.get('bookingId') || '';

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // Available completed bookings to report on
    const [availableBookings, setAvailableBookings] = useState<Booking[]>([]);
    const [fetchingBookings, setFetchingBookings] = useState(false);

    const [formData, setFormData] = useState<ReportFormData>({
        bookingId: initialData?.bookingId || defaultBookingId,
        content: {
            diagnosis: initialData?.content?.diagnosis || '',
            prescription: initialData?.content?.prescription || '',
            notes: initialData?.content?.notes || '',
        },
        attachments: initialData?.attachments || [],
    });

    useEffect(() => {
        // Only fetch bookings if we are creating new and need to select one
        if (!isEditing) {
            const fetchBookings = async () => {
                setFetchingBookings(true);
                try {
                    // Fetch completed bookings for this doctor to write reports for
                    const res = await fetch('/api/admin/bookings/mine?status=completed');
                    const data = await res.json();
                    if (data.success) {
                        setAvailableBookings(data.data);
                    }
                } catch (err) {
                    console.error('Failed to fetch bookings', err);
                } finally {
                    setFetchingBookings(false);
                }
            };
            fetchBookings();
        }
    }, [isEditing]);

    const handleContentChange = (field: keyof ReportFormData['content'], value: string) => {
        setFormData(prev => ({
            ...prev,
            content: { ...prev.content, [field]: value }
        }));
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError(null);

        if (!formData.bookingId) {
            setError('You must select a booking.');
            setLoading(false);
            return;
        }

        try {
            const url = isEditing ? `/api/admin/booking-reports/${initialData?.id}` : '/api/admin/booking-reports';
            const method = isEditing ? 'PUT' : 'POST';

            const res = await fetch(url, {
                method,
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(formData),
            });

            const data = await res.json();

            if (!res.ok || !data.success) {
                throw new Error(data.message || data.error || 'Failed to save medical report');
            }

            router.push('/admin/booking-reports');
            router.refresh();
        } catch (err: any) {
            setError(err.message);
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleString('en-GB', {
            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
        });
    };

    return (
        <Card className="max-w-4xl mx-auto p-12 bg-white shadow-md border border-gray-100">
            <form onSubmit={handleSubmit} className="space-y-10">
                <div className="border-b border-gray-100 pb-6">
                    <h2 className="text-2xl font-bold text-black mb-2 tracking-tight">Medical Session Report</h2>
                    <p className="text-gray-500 text-sm">Document the diagnosis and treatment plan for the completed appointment.</p>
                </div>

                {error && (
                    <div className="p-4 bg-red-50 text-red-600 rounded-lg text-sm border border-red-100 font-bold">
                        {error}
                    </div>
                )}

                <div className="space-y-8">
                    {/* Booking Selection */}
                    <div className="space-y-3">
                        <label className="block text-sm font-bold text-black uppercase tracking-wider">
                            Session / Patient <span className="text-red-500">*</span>
                        </label>
                        <Select
                            className="mt-1"
                            value={formData.bookingId}
                            onChange={(e) => setFormData(prev => ({ ...prev, bookingId: e.target.value }))}
                            disabled={isEditing || fetchingBookings}
                            options={[
                                { value: '', label: fetchingBookings ? 'Loading available sessions...' : 'Select a completed appointment...' },
                                ...availableBookings
                                    .filter(b => !b.reports || b.reports.length === 0)
                                    .map(b => ({
                                        value: b.id,
                                        label: `${b.user.name} - ${formatDate(b.startAt)}`
                                    }))
                            ]}
                        />
                        {!isEditing && availableBookings.length === 0 && !fetchingBookings && (
                            <p className="text-sm text-orange-600 font-medium mt-1">You have no completed appointments without reports.</p>
                        )}
                    </div>

                    {/* Medical Content */}
                    <div className="space-y-10 pt-10 border-t border-gray-100">
                        <h3 className="text-xl font-bold text-black tracking-tight">Clinical Documentation</h3>

                        <div className="space-y-3">
                            <label className="block text-sm font-bold text-black uppercase tracking-wider">
                                Clinical Diagnosis & Assessment <span className="text-red-500">*</span>
                            </label>
                            <Textarea
                                className="min-h-[160px] text-black text-lg leading-relaxed bg-gray-50/30 border-gray-300"
                                value={formData.content.diagnosis}
                                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => handleContentChange('diagnosis', e.target.value)}
                                placeholder="Clearly state the clinical findings and the patient's assessment..."
                                required
                            />
                        </div>

                        <div className="space-y-3">
                            <label className="block text-sm font-bold text-black uppercase tracking-wider">
                                Prescription & Treatment Protocol
                            </label>
                            <Textarea
                                className="min-h-[160px] text-black text-lg leading-relaxed bg-gray-50/30 border-gray-300"
                                value={formData.content.prescription}
                                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => handleContentChange('prescription', e.target.value)}
                                placeholder="List recommended medications, specific exercises, and frequency..."
                            />
                        </div>

                        <div className="space-y-3">
                            <label className="block text-sm font-bold text-black uppercase tracking-wider">
                                Follow-up Instructions & Prognosis
                            </label>
                            <Textarea
                                className="min-h-[120px] text-black text-lg leading-relaxed bg-gray-50/30 border-gray-300"
                                value={formData.content.notes}
                                onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => handleContentChange('notes', e.target.value)}
                                placeholder="Add any further observations or specific follow-up appointments..."
                            />
                        </div>
                    </div>
                </div>

                <div className="flex justify-end gap-6 pt-10 border-t border-gray-100">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={() => router.back()}
                        disabled={loading}
                        className="px-10 h-12 font-bold text-gray-600 border-gray-300 hover:bg-gray-50"
                    >
                        Back to List
                    </Button>
                    <Button
                        type="submit"
                        disabled={loading || !formData.bookingId}
                        size="lg"
                        className="px-14 h-12 bg-blue-600 hover:bg-blue-700 text-white font-bold shadow-lg shadow-blue-200 transition-all active:scale-95"
                    >
                        {loading && <Loader2 className="mr-2 h-5 w-4 animate-spin" />}
                        {isEditing ? 'Update Medical Record' : 'Submit Final Report'}
                    </Button>
                </div>
            </form>
        </Card>
    );
}
