import { Injectable, NotFoundException, BadRequestException, ForbiddenException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import { Prisma } from '@prisma/client';
import { CreateBookingReportInput, UpdateBookingReportInput } from './booking-report.types';

@Injectable()
export class BookingReportService {

    /** Task 6.2: create — only on completed bookings */
    async create(data: CreateBookingReportInput, adminId: string) {
        const prisma = await getPrisma();

        const booking = await prisma.booking.findFirst({
            where: { id: data.bookingId, deletedAt: null },
        });

        if (!booking) throw new NotFoundException('Booking not found');

        // Ensure doctor owns the booking (or is super admin)
        const creator = await prisma.admin.findUnique({ where: { id: adminId } });
        if (!creator?.isSuperAdmin && booking.adminId !== adminId) {
            throw new ForbiddenException('You can only create reports for your own bookings');
        }

        if (booking.status !== 'completed') {
            throw new BadRequestException('Can only create a report for a completed booking');
        }

        return prisma.bookingReport.create({
            data: {
                bookingId: data.bookingId,
                adminId,
                content: data.content as any,
                attachments: (data.attachments as any) ?? Prisma.JsonNull,
            },
            include: this.defaultInclude(),
        });
    }

    /** Task 6.2: getAll */
    async getAll() {
        const prisma = await getPrisma();
        return prisma.bookingReport.findMany({
            include: this.defaultInclude(),
            orderBy: { createdAt: 'desc' },
        });
    }

    /** Task 6.2: getById */
    async getById(id: string) {
        const prisma = await getPrisma();
        const report = await prisma.bookingReport.findUnique({
            where: { id },
            include: this.defaultInclude(),
        });
        if (!report) throw new NotFoundException('Booking report not found');
        return report;
    }

    /** Task 6.2: getByBooking */
    async getByBooking(bookingId: string) {
        const prisma = await getPrisma();
        return prisma.bookingReport.findMany({
            where: { bookingId },
            include: this.defaultInclude(),
            orderBy: { createdAt: 'desc' },
        });
    }

    /** Task 6.2: getByDoctor */
    async getByDoctor(adminId: string) {
        const prisma = await getPrisma();
        return prisma.bookingReport.findMany({
            where: {
                OR: [
                    { adminId }, // Created by the doctor
                    { booking: { adminId } } // Assigned to the doctor
                ]
            },
            include: this.defaultInclude(),
            orderBy: { createdAt: 'desc' },
        });
    }

    /** Task 6.2: update */
    async update(id: string, data: UpdateBookingReportInput, adminId?: string) {
        const prisma = await getPrisma();
        const report = await this.getById(id);

        // If adminId is passed (doctor request), ensure ownership or booking assignment
        if (adminId) {
            const isCreator = report.adminId === adminId;
            const isBookingDoctor = (report.booking as any).adminId === adminId || (report.booking as any).admin?.id === adminId;

            if (!isCreator && !isBookingDoctor) {
                throw new ForbiddenException('You can only edit your own reports or reports for your assigned bookings');
            }
        }

        return prisma.bookingReport.update({
            where: { id },
            data: {
                content: data.content !== undefined ? (data.content as any) : undefined,
                attachments: data.attachments !== undefined ? ((data.attachments as any) ?? Prisma.JsonNull) : undefined,
            },
            include: this.defaultInclude(),
        });
    }

    /** Task 6.2: delete */
    async delete(id: string) {
        const prisma = await getPrisma();
        await this.getById(id);
        return prisma.bookingReport.delete({ where: { id } });
    }

    // ──────────────────────────────────────────────────────────────────────────
    private defaultInclude() {
        return {
            booking: {
                select: {
                    id: true, startAt: true, endAt: true, status: true,
                    user: { select: { id: true, name: true, email: true } },
                    admin: { select: { id: true, name: true, email: true } },
                },
            },
            admin: { select: { id: true, name: true, email: true } },
        };
    }
}
