import { z } from 'zod';

const closeTimeBaseSchema = z.object({
    adminId: z.string().uuid().nullable().optional(),
    fromDate: z.string().datetime({ offset: true }),
    toDate: z.string().datetime({ offset: true }),
    fromTime: z.string().regex(/^([01]\d|2[0-3]):([0-5]\d)$/, 'Time must be in HH:mm format'),
    toTime: z.string().regex(/^([01]\d|2[0-3]):([0-5]\d)$/, 'Time must be in HH:mm format'),
});

export const createCloseTimeSchema = closeTimeBaseSchema.refine((data) => {
    return new Date(data.fromDate) <= new Date(data.toDate);
}, {
    message: 'fromDate must be before or equal to toDate',
    path: ['fromDate'],
}).refine((data) => {
    const [fH, fM] = data.fromTime.split(':').map(Number);
    const [tH, tM] = data.toTime.split(':').map(Number);
    return tH * 60 + tM > fH * 60 + fM;
}, {
    message: 'toTime must be after fromTime',
    path: ['toTime'],
});

export const updateCloseTimeSchema = closeTimeBaseSchema
    .partial()
    .refine((data) => {
        // Only validate dates if both are provided
        if (data.fromDate && data.toDate) {
            return new Date(data.fromDate) <= new Date(data.toDate);
        }
        return true;
    }, {
        message: 'fromDate must be before or equal to toDate',
        path: ['fromDate'],
    })
    .refine((data) => {
        // Only validate times if both are provided
        if (data.fromTime && data.toTime) {
            const [fH, fM] = data.fromTime.split(':').map(Number);
            const [tH, tM] = data.toTime.split(':').map(Number);
            return tH * 60 + tM > fH * 60 + fM;
        }
        return true;
    }, {
        message: 'toTime must be after fromTime',
        path: ['toTime'],
    });

export type CreateCloseTimeInput = z.infer<typeof createCloseTimeSchema>;
export type UpdateCloseTimeInput = z.infer<typeof updateCloseTimeSchema>;
