import { z } from 'zod';

export const createBookingReportSchema = z.object({
    bookingId: z.string().uuid(),
    content: z.record(z.string(), z.unknown()),
    attachments: z.array(z.string()).optional(),
});

export const updateBookingReportSchema = z.object({
    content: z.record(z.string(), z.unknown()).optional(),
    attachments: z.array(z.string()).optional(),
});

export type CreateBookingReportInput = z.infer<typeof createBookingReportSchema>;
export type UpdateBookingReportInput = z.infer<typeof updateBookingReportSchema>;
