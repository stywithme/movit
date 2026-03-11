import { Injectable, ConflictException, NotFoundException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import { CreateDoctorWorkTimeInput, UpdateDoctorWorkTimeInput } from './doctor-work-time.types';

@Injectable()
export class DoctorWorkTimeService {
    /**
     * Get all work times
     */
    async getAll() {
        const prisma = await getPrisma();
        return prisma.doctorWorkTime.findMany({
            include: {
                admin: {
                    select: {
                        id: true,
                        name: true,
                        email: true,
                    },
                },
            },
            orderBy: [
                { day: 'asc' },
                { startTime: 'asc' },
            ],
        });
    }

    /**
     * Get work times for a specific doctor
     */
    async getByAdmin(adminId: string) {
        const prisma = await getPrisma();
        return prisma.doctorWorkTime.findMany({
            where: { adminId },
            orderBy: { startTime: 'asc' },
        });
    }

    /**
     * Set all work times for a specific doctor (bulk replace)
     */
    async setDoctorWorkTimes(data: import('./doctor-work-time.types').CreateBulkDoctorWorkTimeInput) {
        const prisma = await getPrisma();

        // Check for overlaps within the provided data
        for (const wt of data.workTimes) {
            const othersInSameDay = data.workTimes.filter(o => o.day === wt.day && o !== wt);
            if (this.hasOverlap(wt.startTime, wt.endTime, othersInSameDay)) {
                throw new ConflictException(`Overlapping times provided for ${wt.day}`);
            }
        }

        return prisma.$transaction(async (tx) => {
            // Delete existing
            await tx.doctorWorkTime.deleteMany({
                where: { adminId: data.adminId },
            });

            // If empty array, just return empty
            if (data.workTimes.length === 0) {
                return [];
            }

            // Create new
            await tx.doctorWorkTime.createMany({
                data: data.workTimes.map(wt => ({
                    adminId: data.adminId,
                    day: wt.day,
                    startTime: wt.startTime,
                    endTime: wt.endTime,
                })),
            });

            // Return the newly created records
            return tx.doctorWorkTime.findMany({
                where: { adminId: data.adminId },
                orderBy: [
                    { day: 'asc' },
                    { startTime: 'asc' },
                ],
            });
        });
    }

    /**
     * Create a new work time
     */
    async create(data: CreateDoctorWorkTimeInput) {
        const prisma = await getPrisma();

        // Check for overlaps in the same day for this doctor
        const existing = await prisma.doctorWorkTime.findMany({
            where: {
                adminId: data.adminId,
                day: data.day,
            },
        });

        if (this.hasOverlap(data.startTime, data.endTime, existing)) {
            throw new ConflictException('Work time overlaps with an existing one on the same day');
        }

        return prisma.doctorWorkTime.create({
            data,
        });
    }

    /**
     * Update work time
     */
    async update(id: string, data: UpdateDoctorWorkTimeInput) {
        const prisma = await getPrisma();

        const current = await prisma.doctorWorkTime.findUnique({
            where: { id },
        });

        if (!current) {
            throw new NotFoundException('Work time not found');
        }

        // If day or times are changing, check for overlaps
        if (data.day || data.startTime || data.endTime) {
            const newDay = data.day || current.day;
            const newStart = data.startTime || current.startTime;
            const newEnd = data.endTime || current.endTime;

            const existing = await prisma.doctorWorkTime.findMany({
                where: {
                    adminId: current.adminId,
                    day: newDay,
                    id: { not: id },
                },
            });

            if (this.hasOverlap(newStart, newEnd, existing)) {
                throw new ConflictException('Work time overlaps with an existing one on the same day');
            }
        }

        return prisma.doctorWorkTime.update({
            where: { id },
            data,
        });
    }

    /**
     * Delete work time
     */
    async delete(id: string) {
        const prisma = await getPrisma();
        const current = await prisma.doctorWorkTime.findUnique({
            where: { id },
        });

        if (!current) {
            throw new NotFoundException('Work time not found');
        }

        return prisma.doctorWorkTime.delete({
            where: { id },
        });
    }

    /**
     * Private helper to check for time overlaps
     */
    private hasOverlap(start: string, end: string, others: { startTime: string, endTime: string }[]): boolean {
        const s = this.toMinutes(start);
        const e = this.toMinutes(end);

        return others.some((o) => {
            const os = this.toMinutes(o.startTime);
            const oe = this.toMinutes(o.endTime);

            // overlap logic: two intervals [startA, endA) and [startB, endB) overlap if
            // max(startA, startB) < min(endA, endB)
            return Math.max(s, os) < Math.min(e, oe);
        });
    }

    private toMinutes(time: string): number {
        const [h, m] = time.split(':').map(Number);
        return h * 60 + m;
    }
}
