import { Injectable, NotFoundException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import { CreateCloseTimeInput, UpdateCloseTimeInput } from './close-time.types';

@Injectable()
export class CloseTimeService {
    /**
     * Get all close times (admin global view)
     */
    async getAll() {
        const prisma = await getPrisma();
        return prisma.closeTime.findMany({
            include: {
                admin: {
                    select: { id: true, name: true, email: true },
                },
            },
            orderBy: { fromDate: 'asc' },
        });
    }

    /**
     * Get close times for a specific doctor + global ones (adminId = null)
     */
    async getByAdmin(adminId: string) {
        const prisma = await getPrisma();
        return prisma.closeTime.findMany({
            where: {
                OR: [
                    { adminId },
                    { adminId: null },
                ],
            },
            orderBy: { fromDate: 'asc' },
        });
    }

    /**
     * Create a close time entry
     */
    async create(data: CreateCloseTimeInput) {
        const prisma = await getPrisma();
        return prisma.closeTime.create({
            data: {
                adminId: data.adminId ?? null,
                fromDate: new Date(data.fromDate),
                toDate: new Date(data.toDate),
                fromTime: data.fromTime,
                toTime: data.toTime,
            },
        });
    }

    /**
     * Update a close time entry
     */
    async update(id: string, data: UpdateCloseTimeInput) {
        const prisma = await getPrisma();

        const existing = await prisma.closeTime.findUnique({ where: { id } });
        if (!existing) throw new NotFoundException('Close time not found');

        return prisma.closeTime.update({
            where: { id },
            data: {
                adminId: data.adminId !== undefined ? (data.adminId ?? null) : undefined,
                fromDate: data.fromDate ? new Date(data.fromDate) : undefined,
                toDate: data.toDate ? new Date(data.toDate) : undefined,
                fromTime: data.fromTime,
                toTime: data.toTime,
            },
        });
    }

    /**
     * Delete a close time entry
     */
    async delete(id: string) {
        const prisma = await getPrisma();
        const existing = await prisma.closeTime.findUnique({ where: { id } });
        if (!existing) throw new NotFoundException('Close time not found');
        return prisma.closeTime.delete({ where: { id } });
    }

    /**
     * Helper: Check whether a specific date + time block is within any close time
     * used by the Booking service when validating slots.
     */
    async isTimeBlocked(adminId: string, date: Date, slotStart: string, slotEnd: string): Promise<boolean> {
        const prisma = await getPrisma();

        const closeTimes = await prisma.closeTime.findMany({
            where: {
                OR: [{ adminId }, { adminId: null }],
                fromDate: { lte: date },
                toDate: { gte: date },
            },
        });

        const slotStartMin = this.toMinutes(slotStart);
        const slotEndMin = this.toMinutes(slotEnd);

        return closeTimes.some((ct) => {
            const ctStartMin = this.toMinutes(ct.fromTime);
            const ctEndMin = this.toMinutes(ct.toTime);
            return slotStartMin < ctEndMin && slotEndMin > ctStartMin;
        });
    }

    private toMinutes(time: string): number {
        const [h, m] = time.split(':').map(Number);
        return h * 60 + m;
    }
}
