'use client';

import { useEffect, useState } from 'react';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { Button, Input, Select } from '@/components/ui';
import { BookingDetailsModal } from './components/BookingDetailsModal';
import { CreateBookingModal } from './components/CreateBookingModal';
import { Plus } from 'lucide-react';

export interface Booking {
    id: string;
    user: {
        id: string;
        name: string;
        email: string;
    };
    admin: {
        id: string;
        name: string;
        email: string;
    };
    startAt: string;
    endAt: string;
    amount: number;
    paymentStatus: string;
    status: string;
    paymentGateway: string;
    notes: string | null;
    madeByType: 'doctor' | 'admin' | 'user' | null;
    madeById: string | null;
}

export default function BookingsPage() {
    const { user } = useAuthStore();
    const { isSuperAdmin, can } = usePermissions();

    const [bookings, setBookings] = useState<Booking[]>([]);
    const [loading, setLoading] = useState(true);

    // Filters
    const [statusFilter, setStatusFilter] = useState<string>('');
    const [dateFilter, setDateFilter] = useState<string>('');

    // Modal State
    const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);

    const fetchBookings = async () => {
        setLoading(true);
        try {
            const isDoctorOnly = user?.isDoctor && !isSuperAdmin;
            const endpoint = isDoctorOnly ? '/api/admin/bookings/mine' : '/api/admin/bookings';

            const params = new URLSearchParams();
            if (statusFilter) params.set('status', statusFilter);
            if (dateFilter) params.set('date', dateFilter);

            const res = await fetch(`${endpoint}?${params}`);
            const data = await res.json();

            if (data.success) {
                setBookings(data.data);
            }
        } catch (error) {
            console.error('Error fetching bookings:', error);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (user) {
            fetchBookings();
        }
    }, [user, isSuperAdmin, statusFilter, dateFilter]);

    const updateBookingState = (id: string, updates: Partial<Booking>) => {
        setBookings(prev => prev.map(b => b.id === id ? { ...b, ...updates } : b));
        if (selectedBooking?.id === id) {
            setSelectedBooking(prev => prev ? { ...prev, ...updates } : null);
        }
    };

    const formatDate = (isoStr: string) => {
        return new Date(isoStr).toLocaleString('en-GB', {
            year: 'numeric',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'payment_pending': return 'bg-orange-50 text-orange-700 border border-orange-100';
            case 'pending': return 'bg-yellow-50 text-yellow-700 border border-yellow-100';
            case 'confirmed': return 'bg-blue-50 text-blue-700 border border-blue-100';
            case 'completed': return 'bg-green-50 text-green-700 border border-green-100';
            case 'canceled': return 'bg-red-50 text-red-700 border border-red-100';
            case 'rescheduled': return 'bg-purple-50 text-purple-700 border border-purple-100';
            default: return 'bg-gray-50 text-gray-700 border border-gray-100';
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900">Bookings</h1>
                    <p className="text-gray-600 mt-1">Manage patient appointments and sessions</p>
                </div>
                {(!user?.isDoctor || isSuperAdmin) && (
                    <Button onClick={() => setIsCreateModalOpen(true)}>
                        <Plus className="h-4 w-4 mr-2" />
                        New Booking
                    </Button>
                )}
            </div>

            <div className="flex flex-wrap gap-4 items-end bg-white p-4 rounded-lg shadow-sm border border-gray-200">
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Status</label>
                    <Select
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        options={[
                            { value: '', label: 'All Statuses' },
                            { value: 'payment_pending', label: 'Payment Pending' },
                            { value: 'pending', label: 'Pending' },
                            { value: 'confirmed', label: 'Confirmed' },
                            { value: 'completed', label: 'Completed' },
                            { value: 'canceled', label: 'Cancelled' },
                        ]}
                    />
                </div>
                <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
                    <Input
                        type="date"
                        value={dateFilter}
                        onChange={(e) => setDateFilter(e.target.value)}
                    />
                </div>
                <div className="ml-auto">
                    <Button variant="outline" onClick={() => { setStatusFilter(''); setDateFilter(''); }}>
                        Clear Filters
                    </Button>
                </div>
            </div>

            <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {loading ? (
                    <div className="p-8 text-center text-gray-500">Loading bookings...</div>
                ) : bookings.length === 0 ? (
                    <div className="p-8 text-center text-gray-500">No bookings found for the selected filters.</div>
                ) : (
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 border-b border-gray-200">
                                <tr>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Patient</th>
                                    {(!user?.isDoctor || isSuperAdmin) && (
                                        <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Doctor</th>
                                    )}
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Schedule</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Status</th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase">Created By</th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200">
                                {bookings.map((booking) => (
                                    <tr key={booking.id} className="hover:bg-gray-50">
                                        <td className="px-6 py-4 text-center">
                                            <div className="font-medium text-gray-900">{booking.user.name}</div>
                                            <div className="text-sm text-gray-500">{booking.user.email}</div>
                                        </td>
                                        {(!user?.isDoctor || isSuperAdmin) && (
                                            <td className="px-6 py-4 text-center">
                                                <div className="text-sm text-gray-900">{booking.admin.name}</div>
                                            </td>
                                        )}
                                        <td className="px-6 py-4 text-sm text-gray-600 text-center">
                                            <div>{formatDate(booking.startAt)}</div>
                                            <div className="text-xs text-gray-400 mt-0.5">Duration:
                                                {Math.round((new Date(booking.endAt).getTime() - new Date(booking.startAt).getTime()) / 60000)} mins
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <span className={`inline-flex px-2 py-1 text-[10px] font-bold rounded-full uppercase tracking-tight ${getStatusColor(booking.status)}`}>
                                                {booking.status.replace('_', ' ')}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 text-center">
                                            <div className="text-xs font-semibold text-gray-500 uppercase bg-gray-50 inline-block px-2 py-0.5 rounded border border-gray-100 italic">
                                                {booking.madeByType || 'system'}
                                            </div>
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <button
                                                onClick={() => setSelectedBooking(booking)}
                                                className="text-blue-600 hover:text-blue-800 text-sm font-medium"
                                            >
                                                View Details
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            {selectedBooking && (
                <BookingDetailsModal
                    booking={selectedBooking}
                    onClose={() => setSelectedBooking(null)}
                    onUpdate={(updates) => updateBookingState(selectedBooking.id, updates)}
                    isDoctorView={!!(user?.isDoctor && !isSuperAdmin)}
                />
            )}
            {isCreateModalOpen && (
                <CreateBookingModal
                    onClose={() => setIsCreateModalOpen(false)}
                    onSuccess={() => {
                        setIsCreateModalOpen(false);
                        fetchBookings();
                    }}
                    initialDoctorId={user?.isDoctor && !isSuperAdmin ? user.id : undefined}
                />
            )}
        </div>
    );
}
