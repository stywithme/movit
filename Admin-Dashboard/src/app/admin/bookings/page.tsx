'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { toast } from 'sonner';
import { useAuthStore } from '@/lib/auth/auth-store';
import { usePermissions } from '@/hooks/usePermissions';
import { Badge, Button, Card, Input } from '@/components/ui';
import { DataTable, FilterBar, PageHeader, StatusBadge, type DataTableColumn } from '@/components/common';
import { BookingDetailsModal } from './components/BookingDetailsModal';
import { CreateBookingModal } from './components/CreateBookingModal';
import { Plus } from 'lucide-react';

export interface PaymentInfo {
    checkoutId: string;
    status: string;
    totalAmount: number;
    currency: string;
    paymentUrlPresent: boolean;
    lastError?: string;
    paidAt?: string;
    lastUpdated: string;
}

interface BookingReportSummary {
    id: string;
    createdAt: string;
}

interface BookingAllowedActions {
    canJoin?: boolean;
}

interface GoogleMeetStatus {
    connected: boolean;
    status: 'connected' | 'disconnected' | 'reconnect_required';
    googleEmail?: string;
    lastError?: string;
    connectedAt?: string;
}

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
    sessionUrl?: string | null;
    isRescheduled?: boolean;
    reports?: BookingReportSummary[];
    allowedActions?: BookingAllowedActions;
    paymentInfo?: PaymentInfo;
}

export default function BookingsPage() {
    const searchParams = useSearchParams();
    const { user } = useAuthStore();
    const { isSuperAdmin, can } = usePermissions();
    const isDoctorOnly = !!(user?.isDoctor && !isSuperAdmin);

    const [bookings, setBookings] = useState<Booking[]>([]);
    const [loading, setLoading] = useState(true);
    const [googleMeetStatus, setGoogleMeetStatus] = useState<GoogleMeetStatus | null>(null);
    const [googleMeetLoading, setGoogleMeetLoading] = useState(false);
    const [googleMeetActionLoading, setGoogleMeetActionLoading] = useState(false);

    // Filters
    const [statusFilter, setStatusFilter] = useState<string>('');
    const [dateFilter, setDateFilter] = useState<string>('');

    // Modal State
    const [selectedBooking, setSelectedBooking] = useState<Booking | null>(null);
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const googleMeetResult = searchParams.get('gmeet');
    const googleMeetReason = searchParams.get('reason');

    const fetchBookings = async () => {
        setLoading(true);
        try {
            const endpoint = isDoctorOnly ? '/api/admin/bookings/mine' : '/api/admin/bookings';

            const params = new URLSearchParams();
            if (statusFilter) params.set('status', statusFilter);
            if (dateFilter) {
                // Filter bookings that start within the selected calendar day (local time)
                params.set('dateFrom', `${dateFilter}T00:00:00.000Z`);
                params.set('dateTo', `${dateFilter}T23:59:59.999Z`);
            }

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

    const fetchGoogleMeetStatus = async () => {
        if (!isDoctorOnly) return;

        setGoogleMeetLoading(true);
        try {
            const res = await fetch('/api/admin/google-meet/status');
            const data = await res.json();
            if (data.success) {
                setGoogleMeetStatus(data.data);
            } else {
                setGoogleMeetStatus(null);
            }
        } catch (error) {
            console.error('Error fetching Google Meet status:', error);
            setGoogleMeetStatus(null);
        } finally {
            setGoogleMeetLoading(false);
        }
    };

    useEffect(() => {
        if (user) {
            fetchBookings();
        }
    }, [user, isSuperAdmin, statusFilter, dateFilter]);

    useEffect(() => {
        if (user && isDoctorOnly) {
            fetchGoogleMeetStatus();
        }
    }, [user, isDoctorOnly, googleMeetResult, googleMeetReason]);

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

    const handleGoogleMeetConnect = () => {
        window.location.href = '/api/admin/google-meet/connect?redirectTo=%2Fadmin%2Fbookings';
    };

    const handleGoogleMeetDisconnect = async () => {
        setGoogleMeetActionLoading(true);
        try {
            const res = await fetch('/api/admin/google-meet/disconnect', { method: 'POST' });
            const data = await res.json();
            if (!res.ok || !data.success) {
                throw new Error(data.error || data.message || 'Failed to disconnect Google Meet');
            }
            await fetchGoogleMeetStatus();
        } catch (error) {
            console.error('Error disconnecting Google Meet:', error);
            toast.error(error instanceof Error ? error.message : 'Failed to disconnect Google Meet');
        } finally {
            setGoogleMeetActionLoading(false);
        }
    };

    const getLatestReport = (booking: Booking) =>
        [...(booking.reports || [])].sort(
            (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
        )[0];

    const canJoinBooking = (booking: Booking) =>
        Boolean(booking.allowedActions?.canJoin && booking.sessionUrl);

    const columns: DataTableColumn<Booking>[] = [
        {
            key: 'patient',
            header: 'Patient',
            cell: (booking) => (
                <div className="min-w-[180px]">
                    <div className="font-medium">{booking.user.name}</div>
                    <div className="text-sm text-muted-foreground">{booking.user.email}</div>
                </div>
            ),
        },
        ...((!user?.isDoctor || isSuperAdmin)
            ? [
                {
                    key: 'doctor',
                    header: 'Doctor',
                    cell: (booking: Booking) => <span className="text-sm font-medium">{booking.admin.name}</span>,
                } satisfies DataTableColumn<Booking>,
            ]
            : []),
        {
            key: 'schedule',
            header: 'Schedule',
            cell: (booking) => (
                <div className="text-sm text-muted-foreground">
                    <div>{formatDate(booking.startAt)}</div>
                    <div className="mt-0.5 text-xs">
                        Duration: {Math.round((new Date(booking.endAt).getTime() - new Date(booking.startAt).getTime()) / 60000)} mins
                    </div>
                </div>
            ),
        },
        {
            key: 'status',
            header: 'Status',
            cell: (booking) => (
                <div className="flex flex-wrap gap-1">
                    <StatusBadge status={booking.status} />
                    {booking.isRescheduled && <Badge variant="purple">Rescheduled</Badge>}
                </div>
            ),
        },
        {
            key: 'createdBy',
            header: 'Created By',
            cell: (booking) => <Badge variant="outline">{booking.madeByType || 'system'}</Badge>,
        },
        {
            key: 'actions',
            header: 'Actions',
            headerClassName: 'text-right',
            className: 'text-right',
            cell: (booking) => {
                const latestReport = getLatestReport(booking);
                const canWriteReport = isDoctorOnly && can('create', 'BookingReport') && booking.status === 'completed' && !latestReport;
                const canViewReport = Boolean(latestReport) && can('read', 'BookingReport');
                const canJoin = canJoinBooking(booking);

                return (
                    <div className="flex flex-wrap items-center justify-end gap-2">
                        {canJoin && booking.sessionUrl && (
                            <Button asChild variant="ghost" size="sm">
                                <a href={booking.sessionUrl} target="_blank" rel="noreferrer">
                                    Join
                                </a>
                            </Button>
                        )}
                        {canWriteReport && (
                            <Button asChild variant="ghost" size="sm">
                                <Link href={`/admin/booking-reports/new?bookingId=${booking.id}`}>Write Report</Link>
                            </Button>
                        )}
                        {canViewReport && latestReport && (
                            <Button asChild variant="ghost" size="sm">
                                <Link href={`/admin/booking-reports/${latestReport.id}`}>View Report</Link>
                            </Button>
                        )}
                        <Button type="button" variant="ghost" size="sm" onClick={() => setSelectedBooking(booking)}>
                            View Details
                        </Button>
                    </div>
                );
            },
        },
    ];

    return (
        <div className="space-y-6">
            <PageHeader
                title="Bookings"
                description="Manage patient appointments and sessions"
                actions={
                    (!user?.isDoctor || isSuperAdmin) && (
                    <Button onClick={() => setIsCreateModalOpen(true)}>
                        <Plus className="h-4 w-4" />
                        New Booking
                    </Button>
                    )
                }
            />

            {isDoctorOnly && (
                <Card className="p-4 space-y-4">
                    <div className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
                        <div>
                            <h2 className="text-lg font-semibold">Google Meet Connection</h2>
                            <p className="mt-1 text-sm text-muted-foreground">
                                Connect your Google account so confirmed bookings can generate and join Meet sessions.
                            </p>
                        </div>
                        <div className="flex flex-wrap gap-2">
                            <Button
                                variant={googleMeetStatus?.connected ? 'outline' : 'primary'}
                                onClick={handleGoogleMeetConnect}
                            >
                                {googleMeetStatus?.status === 'reconnect_required'
                                    ? 'Reconnect Google'
                                    : googleMeetStatus?.connected
                                        ? 'Reconnect Google'
                                        : 'Connect Google'}
                            </Button>
                            {googleMeetStatus?.connected && (
                                <Button
                                    variant="ghost"
                                    onClick={handleGoogleMeetDisconnect}
                                    loading={googleMeetActionLoading}
                                >
                                    Disconnect
                                </Button>
                            )}
                        </div>
                    </div>

                    {googleMeetResult === 'connected' && (
                        <div className="rounded-lg border bg-success/10 px-3 py-2 text-sm text-success">
                            Google Meet connected successfully.
                        </div>
                    )}
                    {googleMeetResult === 'error' && (
                        <div className="rounded-lg border bg-destructive/10 px-3 py-2 text-sm text-destructive">
                            Google Meet connection failed{googleMeetReason ? `: ${googleMeetReason}` : '.'}
                        </div>
                    )}

                    {googleMeetLoading ? (
                        <div className="text-sm text-muted-foreground">Checking Google Meet connection...</div>
                    ) : (
                        <div className="flex flex-wrap items-center gap-3 text-sm">
                            <StatusBadge status={googleMeetStatus?.status || 'disconnected'} />
                            {googleMeetStatus?.googleEmail && (
                                <span>
                                    Connected account: <span className="font-medium">{googleMeetStatus.googleEmail}</span>
                                </span>
                            )}
                            {googleMeetStatus?.connectedAt && (
                                <span className="text-muted-foreground">
                                    Connected at: {formatDate(googleMeetStatus.connectedAt)}
                                </span>
                            )}
                        </div>
                    )}

                    {googleMeetStatus?.lastError && (
                        <div className="rounded-lg border bg-warning/10 px-3 py-2 text-sm text-warning">
                            Last Google Meet error: {googleMeetStatus.lastError}
                        </div>
                    )}
                </Card>
            )}

            <FilterBar
                selects={[
                    {
                        id: 'status',
                        value: statusFilter,
                        placeholder: 'Status',
                        onChange: setStatusFilter,
                        options: [
                            { value: '', label: 'All Statuses' },
                            { value: 'payment_pending', label: 'Payment Pending' },
                            { value: 'pending', label: 'Pending' },
                            { value: 'confirmed', label: 'Confirmed' },
                            { value: 'completed', label: 'Completed' },
                            { value: 'canceled', label: 'Cancelled' },
                        ],
                    },
                ]}
                onReset={() => {
                    setStatusFilter('');
                    setDateFilter('');
                }}
            >
                <div className="lg:w-48">
                    <Input
                        type="date"
                        value={dateFilter}
                        onChange={(e) => setDateFilter(e.target.value)}
                    />
                </div>
            </FilterBar>

            <DataTable
                columns={columns}
                data={bookings}
                getRowKey={(booking) => booking.id}
                loading={loading}
                emptyTitle="No bookings found"
                emptyDescription="No bookings match the selected filters."
            />

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
