import { z } from 'zod';

const doctorWorkTimeBaseSchema = z.object({
    adminId: z.string().uuid(),
    day: z.enum([
        'Saturday',
        'Sunday',
        'Monday',
        'Tuesday',
        'Wednesday',
        'Thursday',
        'Friday',
    ]),
    startTime: z.string().regex(/^([01]\d|2[0-3]):([0-5]\d)$/, 'Time must be in HH:mm format'),
    endTime: z.string().regex(/^([01]\d|2[0-3]):([0-5]\d)$/, 'Time must be in HH:mm format'),
});

export const createDoctorWorkTimeSchema = doctorWorkTimeBaseSchema.refine((data) => {
    const [startHour, startMin] = data.startTime.split(':').map(Number);
    const [endHour, endMin] = data.endTime.split(':').map(Number);
    const startTotalMinutes = startHour * 60 + startMin;
    const endTotalMinutes = endHour * 60 + endMin;
    return endTotalMinutes > startTotalMinutes;
}, {
    message: 'End time must be after start time',
    path: ['endTime'],
});

export const updateDoctorWorkTimeSchema = doctorWorkTimeBaseSchema
    .partial()
    .omit({ adminId: true })
    .refine((data) => {
        // Only refine if both times are provided in the update
        if (data.startTime && data.endTime) {
            const [startHour, startMin] = data.startTime.split(':').map(Number);
            const [endHour, endMin] = data.endTime.split(':').map(Number);
            const startTotalMinutes = startHour * 60 + startMin;
            const endTotalMinutes = endHour * 60 + endMin;
            return endTotalMinutes > startTotalMinutes;
        }
        return true;
    }, {
        message: 'End time must be after start time',
        path: ['endTime'],
    });

export type CreateDoctorWorkTimeInput = z.infer<typeof createDoctorWorkTimeSchema>;
export type UpdateDoctorWorkTimeInput = z.infer<typeof updateDoctorWorkTimeSchema>;

export const createBulkDoctorWorkTimeSchema = z.object({
    adminId: z.string().uuid(),
    workTimes: z.array(z.object({
        day: z.enum([
            'Saturday',
            'Sunday',
            'Monday',
            'Tuesday',
            'Wednesday',
            'Thursday',
            'Friday',
        ]),
        startTime: z.string().regex(/^([01]\d|2[0-3]):([0-5]\d)$/, 'Time must be in HH:mm format'),
        endTime: z.string().regex(/^([01]\d|2[0-3]):([0-5]\d)$/, 'Time must be in HH:mm format'),
    })).refine(times => {
        // Basic check for start < end for each item
        for (const t of times) {
            const [startHour, startMin] = t.startTime.split(':').map(Number);
            const [endHour, endMin] = t.endTime.split(':').map(Number);
            if ((endHour * 60 + endMin) <= (startHour * 60 + startMin)) return false;
        }
        return true;
    }, 'End time must be after start time for all entries')
});

export type CreateBulkDoctorWorkTimeInput = z.infer<typeof createBulkDoctorWorkTimeSchema>;

export interface DoctorWorkTimePublic {
    id: string;
    adminId: string;
    day: string;
    startTime: string;
    endTime: string;
    admin?: {
        id: string;
        name: string;
        email: string;
    };
}
