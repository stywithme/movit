import { z } from 'zod';

// ─── Create Booking ──────────────────────────────────────────────────────────
export const createBookingSchema = z.object({
    userId: z.string().uuid(),
    adminId: z.string().uuid(),
    startAt: z.string().datetime({ offset: true }),
    endAt: z.string().datetime({ offset: true }),
    amount: z.number().min(0).optional().default(0),
    paymentStatus: z.enum(['paid', 'unpaid', 'refunded']).optional(),
    paymentId: z.string().optional(),
    paymentGateway: z.string().optional(),
    sessionUrl: z.string().url().optional(),
    notes: z.string().optional(),
}).refine((d) => new Date(d.endAt) > new Date(d.startAt), {
    message: 'endAt must be after startAt',
    path: ['endAt'],
});

// ─── Update Booking ──────────────────────────────────────────────────────────
export const updateBookingSchema = z.object({
    status: z.enum(['payment_pending', 'pending', 'confirmed', 'completed', 'canceled']).optional(),
    amount: z.number().min(0).optional(),
    paymentStatus: z.enum(['paid', 'unpaid', 'refunded']).optional(),
    paymentId: z.string().optional(),
    paymentGateway: z.string().optional(),
    sessionUrl: z.string().url().optional(),
    sessionMeetingId: z.string().optional(),
    sessionPlatform: z.enum(['zoom', 'google_meet']).optional(),
    notes: z.string().optional(),
    cancelledBy: z.string().optional(),
});

// ─── Doctor: Update Status ────────────────────────────────────────────────────
export const updateBookingStatusSchema = z.object({
    status: z.enum(['confirmed', 'completed', 'canceled']),
});

// ─── Doctor: Update Notes ─────────────────────────────────────────────────────
export const updateBookingNotesSchema = z.object({
    notes: z.string(),
});

// ─── Doctor: Follow-up ───────────────────────────────────────────────────────
export const createFollowUpSchema = z.object({
    userId: z.string().uuid(),
    startAt: z.string().datetime({ offset: true }),
    endAt: z.string().datetime({ offset: true }),
    notes: z.string().optional(),
}).refine((d) => new Date(d.endAt) > new Date(d.startAt), {
    message: 'endAt must be after startAt',
    path: ['endAt'],
});

// ─── Reschedule ───────────────────────────────────────────────────────────────
export const rescheduleBookingSchema = z.object({
    startAt: z.string().datetime({ offset: true }),
    endAt: z.string().datetime({ offset: true }),
}).refine((d) => new Date(d.endAt) > new Date(d.startAt), {
    message: 'endAt must be after startAt',
    path: ['endAt'],
});

// ─── Cancel ───────────────────────────────────────────────────────────────────
export const cancelBookingSchema = z.object({
    reason: z.string().optional(),
});

// ─── Filters ─────────────────────────────────────────────────────────────────
export const bookingFiltersSchema = z.object({
    status: z.enum(['payment_pending', 'pending', 'confirmed', 'completed', 'canceled']).optional(),
    adminId: z.string().uuid().optional(),
    userId: z.string().uuid().optional(),
    dateFrom: z.string().datetime({ offset: true }).optional(),
    dateTo: z.string().datetime({ offset: true }).optional(),
});

// ─── Types ────────────────────────────────────────────────────────────────────
export type CreateBookingInput = z.infer<typeof createBookingSchema>;
export type UpdateBookingInput = z.infer<typeof updateBookingSchema>;
export type UpdateBookingStatusInput = z.infer<typeof updateBookingStatusSchema>;
export type UpdateBookingNotesInput = z.infer<typeof updateBookingNotesSchema>;
export type CreateFollowUpInput = z.infer<typeof createFollowUpSchema>;
export type RescheduleBookingInput = z.infer<typeof rescheduleBookingSchema>;
export type BookingFiltersInput = z.infer<typeof bookingFiltersSchema>;
