import {
    Injectable,
    BadRequestException,
    ConflictException,
    NotFoundException,
    ForbiddenException,
} from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import {
    CreateBookingInput,
    UpdateBookingInput,
    UpdateBookingStatusInput,
    UpdateBookingNotesInput,
    CreateFollowUpInput,
    RescheduleBookingInput,
    BookingFiltersInput,
} from './booking.types';

// ─── Helper: parse HH:mm ──────────────────────────────────────────────────────
function toMinutes(time: string): number {
    const [h, m] = time.split(':').map(Number);
    return h * 60 + m;
}

function toHHmm(minutes: number): string {
    const h = Math.floor(minutes / 60).toString().padStart(2, '0');
    const m = (minutes % 60).toString().padStart(2, '0');
    return `${h}:${m}`;
}

// ─── Day name ─────────────────────────────────────────────────────────────────
const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

// ─── Status permission helpers ────────────────────────────────────────────────
/** Statuses where the user/client can cancel */
const CANCELLABLE_STATUSES = ['payment_pending'];

/** Statuses where the user/client can reschedule */
const RESCHEDULABLE_STATUSES = ['payment_pending', 'pending', 'confirmed'];

/** Terminal statuses — no changes allowed */
const TERMINAL_STATUSES = ['completed', 'canceled'];

@Injectable()
export class BookingService {

    // ──────────────────────────────────────────────────────────────────────────
    // Slot Calculation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Task 5.2: getAvailableSlots
     * Calculates free time slots for a doctor on a given date.
     *  1. Fetches DoctorWorkTime for the weekday
     *  2. Subtracts CloseTimes (doctor-specific + global)
     *  3. Subtracts existing Bookings
     *  4. Splits remaining time into booking_duration-sized slots
     */
    async getAvailableSlots(adminId: string, date: string) {
        const prisma = await getPrisma();
        const targetDate = new Date(date);
        const dayName = DAY_NAMES[targetDate.getDay()];

        // 1. Get system config
        const durationCfg = await prisma.system.findUnique({ where: { key: 'booking_duration' } });
        const durationMin = parseInt(durationCfg?.value ?? '30', 10);

        // 2. Get work times for that day
        const workTimes = await prisma.doctorWorkTime.findMany({
            where: { adminId, day: dayName },
        });

        if (workTimes.length === 0) return [];

        // 3. Get close times affecting this date (doctor + global)
        const closeTimes = await prisma.closeTime.findMany({
            where: {
                OR: [{ adminId }, { adminId: null }],
                fromDate: { lte: targetDate },
                toDate: { gte: targetDate },
            },
        });

        // 4. Get existing bookings for this doctor on this date
        const dayStart = new Date(targetDate);
        dayStart.setHours(0, 0, 0, 0);
        const dayEnd = new Date(targetDate);
        dayEnd.setHours(23, 59, 59, 999);

        const existingBookings = await prisma.booking.findMany({
            where: {
                adminId,
                deletedAt: null,
                status: { notIn: ['canceled'] },
                startAt: { gte: dayStart, lte: dayEnd },
            },
        });

        // 5. Build blocked intervals in minutes
        const blockedIntervals: { start: number; end: number }[] = [];

        for (const ct of closeTimes) {
            blockedIntervals.push({ start: toMinutes(ct.fromTime), end: toMinutes(ct.toTime) });
        }

        for (const b of existingBookings) {
            const s = b.startAt.getHours() * 60 + b.startAt.getMinutes();
            const e = b.endAt.getHours() * 60 + b.endAt.getMinutes();
            blockedIntervals.push({ start: s, end: e });
        }

        // 6. Get min_booking_hours for today filter
        const minHoursCfg = await prisma.system.findUnique({ where: { key: 'min_booking_hours' } });
        const minHours = parseInt(minHoursCfg?.value ?? '2', 10);

        // 7. Generate slots from work times minus blocked
        const slots: { start: string; end: string }[] = [];
        const now = new Date();
        const isToday = targetDate.toDateString() === now.toDateString();
        // Add minHours buffer for today's slots
        const minFutureMin = isToday ? now.getHours() * 60 + now.getMinutes() + (minHours * 60) : 0;

        for (const wt of workTimes) {
            let cursor = toMinutes(wt.startTime);
            const end = toMinutes(wt.endTime);

            while (cursor + durationMin <= end) {
                const slotEnd = cursor + durationMin;
                const isBlocked = blockedIntervals.some(b => cursor < b.end && slotEnd > b.start);

                if (!isBlocked && cursor >= minFutureMin) {
                    const slotStartAt = new Date(targetDate);
                    slotStartAt.setHours(Math.floor(cursor / 60), cursor % 60, 0, 0);
                    const slotEndAt = new Date(targetDate);
                    slotEndAt.setHours(Math.floor(slotEnd / 60), slotEnd % 60, 0, 0);

                    slots.push({
                        start: slotStartAt.toISOString(),
                        end: slotEndAt.toISOString(),
                    });
                }
                cursor += durationMin;
            }
        }

        return slots;
    }

    /**
     * Task 5.2: getAvailableDoctors
     * Returns doctors who have at least one work slot on the given date.
     */
    async getAvailableDoctors(date: string) {
        const prisma = await getPrisma();
        const targetDate = new Date(date);
        const dayName = DAY_NAMES[targetDate.getDay()];

        const workTimes = await prisma.doctorWorkTime.findMany({
            where: { day: dayName },
            include: {
                admin: {
                    select: { id: true, name: true, email: true, isDoctor: true, isActive: true, deletedAt: true },
                },
            },
        });

        // Filter to active doctors only
        const doctors = workTimes
            .filter(wt => wt.admin.isDoctor && wt.admin.isActive && !wt.admin.deletedAt)
            .map(wt => wt.admin);

        // Deduplicate by id
        const unique = new Map(doctors.map(d => [d.id, d]));
        return Array.from(unique.values());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Validation
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Task 5.2: validateBooking
     * Centralised booking validation used by create and reschedule.
     */
    private async validateBooking(adminId: string, startAt: Date, endAt: Date, excludeBookingId?: string, isFollowUp: boolean = false) {
        const prisma = await getPrisma();

        // 1. System: allow_booking
        const allowCfg = await prisma.system.findUnique({ where: { key: 'allow_booking' } });
        if (allowCfg?.value !== 'true') throw new BadRequestException('Booking is currently disabled');

        // 2. Future check + min_booking_hours
        const minHoursCfg = await prisma.system.findUnique({ where: { key: 'min_booking_hours' } });
        const minHours = parseInt(minHoursCfg?.value ?? '2', 10);
        const minAllowedDate = new Date(Date.now() + minHours * 3600000);
        if (startAt <= minAllowedDate) {
            throw new BadRequestException(`Booking must be at least ${minHours} hour(s) in advance`);
        }

        // 3. Max advance days
        if (!isFollowUp) {
            const maxDaysCfg = await prisma.system.findUnique({ where: { key: 'max_advance_booking_days' } });
            const maxDays = parseInt(maxDaysCfg?.value ?? '30', 10);
            const maxDate = new Date();
            maxDate.setDate(maxDate.getDate() + maxDays);
            if (startAt > maxDate) throw new BadRequestException(`Cannot book more than ${maxDays} days in advance`);
        }

        // 4. Duration match
        const durationCfg = await prisma.system.findUnique({ where: { key: 'booking_duration' } });
        const expectedDuration = parseInt(durationCfg?.value ?? '30', 10);
        const actualDuration = (endAt.getTime() - startAt.getTime()) / 60000;
        if (actualDuration !== expectedDuration) {
            throw new BadRequestException(`Booking duration must be exactly ${expectedDuration} minutes`);
        }

        // 5. In doctor work time
        const dayName = DAY_NAMES[startAt.getDay()];
        const startMin = startAt.getHours() * 60 + startAt.getMinutes();
        const endMin = endAt.getHours() * 60 + endAt.getMinutes();

        const workTimes = await prisma.doctorWorkTime.findMany({ where: { adminId, day: dayName } });
        const inWorkTime = workTimes.some(wt => startMin >= toMinutes(wt.startTime) && endMin <= toMinutes(wt.endTime));
        if (!inWorkTime) throw new BadRequestException('Slot is outside doctor working hours');

        // 6. Not in close time
        const closeTimes = await prisma.closeTime.findMany({
            where: {
                OR: [{ adminId }, { adminId: null }],
                fromDate: { lte: startAt },
                toDate: { gte: startAt },
            },
        });
        const isBlocked = closeTimes.some(ct => startMin < toMinutes(ct.toTime) && endMin > toMinutes(ct.fromTime));
        if (isBlocked) throw new BadRequestException('Slot falls within a close time / holiday');

        // 7. Conflict with existing bookings
        const conflict = await prisma.booking.findFirst({
            where: {
                adminId,
                deletedAt: null,
                status: { notIn: ['canceled'] },
                id: excludeBookingId ? { not: excludeBookingId } : undefined,
                OR: [
                    { startAt: { lt: endAt }, endAt: { gt: startAt } },
                ],
            },
        });
        if (conflict) throw new ConflictException('This slot is already booked');
    }

    // ──────────────────────────────────────────────────────────────────────────
    // CRUD
    // ──────────────────────────────────────────────────────────────────────────

    async create(data: CreateBookingInput, creator?: { id: string; type: 'admin' | 'doctor' | 'user'; isDoctor: boolean; isSuperAdmin: boolean }) {
        const prisma = await getPrisma();
        const startAt = new Date(data.startAt);
        const endAt = new Date(data.endAt);

        // If creator is a doctor (and not super admin), enforce past client rule
        if (creator?.isDoctor && !creator?.isSuperAdmin) {
            const pastSession = await prisma.booking.findFirst({
                where: {
                    adminId: creator.id,
                    userId: data.userId,
                    status: 'completed',
                    deletedAt: null,
                },
            });
            if (!pastSession) {
                throw new ForbiddenException('Doctors can only create bookings for users they have seen before');
            }
            // Ensure doctor is booking for themselves
            if (data.adminId !== creator.id) {
                throw new ForbiddenException('Doctors can only create bookings for themselves');
            }
        }

        await this.validateBooking(data.adminId, startAt, endAt);

        const priceCfg = await prisma.system.findUnique({ where: { key: 'booking_price' } });
        const amount = data.amount ?? parseFloat(priceCfg?.value ?? '0');

        // Determine madeByType
        const madeByType = creator?.type ?? 'admin';
        const madeById = creator?.id ?? null;

        return prisma.booking.create({
            data: {
                userId: data.userId,
                adminId: data.adminId,
                startAt, endAt,
                amount,
                // All new bookings start as payment_pending
                status: 'payment_pending',
                paymentStatus: data.paymentStatus,
                paymentId: data.paymentId,
                paymentGateway: data.paymentGateway,
                sessionUrl: data.sessionUrl,
                notes: data.notes,
                madeByType,
                madeById,
            },
            include: this.defaultInclude(),
        });
    }

    /** Task 5.3: getAll */
    async getAll(filters: BookingFiltersInput = {}) {
        const prisma = await getPrisma();
        return prisma.booking.findMany({
            where: {
                deletedAt: null,
                status: filters.status,
                adminId: filters.adminId,
                userId: filters.userId,
                startAt: filters.dateFrom ? { gte: new Date(filters.dateFrom) } : undefined,
                endAt: filters.dateTo ? { lte: new Date(filters.dateTo) } : undefined,
            },
            include: this.defaultInclude(),
            orderBy: { startAt: 'desc' },
        });
    }

    /** Task 5.3: getById */
    async getById(id: string) {
        const prisma = await getPrisma();
        const booking = await prisma.booking.findFirst({
            where: { id, deletedAt: null },
            include: this.defaultInclude(),
        });
        if (!booking) throw new NotFoundException('Booking not found');
        return booking;
    }

    /** Task 5.3: update (admin full update) */
    async update(id: string, data: UpdateBookingInput) {
        const prisma = await getPrisma();
        await this.getById(id);
        return prisma.booking.update({
            where: { id },
            data,
            include: this.defaultInclude(),
        });
    }

    /** Task 5.3: soft delete */
    async delete(id: string) {
        const prisma = await getPrisma();
        await this.getById(id);
        return prisma.booking.update({
            where: { id },
            data: { deletedAt: new Date() },
        });
    }

    /**
     * Task 5.3: cancel
     * Rules:
     *  - payment_pending → allowed ✅
     *  - pending         → allowed ✅
     *  - confirmed       → allowed ✅ (admin/doctor only — user-facing controller restricts this)
     *  - completed       → NOT allowed ❌
     *  - canceled        → NOT allowed ❌
     */
    async cancel(id: string, cancelledBy: string) {
        const prisma = await getPrisma();
        const booking = await this.getById(id);
        if (TERMINAL_STATUSES.includes(booking.status)) {
            throw new BadRequestException('Cannot cancel a completed or already-cancelled booking');
        }
        return prisma.booking.update({
            where: { id },
            data: { status: 'canceled', cancelledBy },
            include: this.defaultInclude(),
        });
    }

    /**
     * Task 5.3: reschedule
     * Rules:
     *  - payment_pending → allowed ✅ (bypasses reschedule_allowed_time check)
     *  - pending         → allowed ✅ (bypasses reschedule_allowed_time check)
     *  - confirmed       → allowed ✅ (subject to reschedule_allowed_time)
     *  - completed       → NOT allowed ❌
     *  - canceled        → NOT allowed ❌
     */
    async reschedule(id: string, data: RescheduleBookingInput) {
        const prisma = await getPrisma();
        const booking = await this.getById(id);

        if (TERMINAL_STATUSES.includes(booking.status)) {
            throw new BadRequestException('Cannot reschedule a completed or cancelled booking');
        }

        // Only check reschedule_allowed_time for confirmed bookings
        if (booking.status === 'confirmed') {
            const allowedHoursCfg = await prisma.system.findUnique({ where: { key: 'reschedule_allowed_time' } });
            const allowedHours = parseInt(allowedHoursCfg?.value ?? '24', 10);
            const hoursUntilBooking = (booking.startAt.getTime() - Date.now()) / 3600000;
            if (hoursUntilBooking < allowedHours) {
                throw new ForbiddenException(`Can only reschedule at least ${allowedHours}h before the appointment`);
            }
        }

        const newStart = new Date(data.startAt);
        const newEnd = new Date(data.endAt);
        await this.validateBooking(booking.adminId, newStart, newEnd, id);

        return prisma.booking.update({
            where: { id },
            data: { startAt: newStart, endAt: newEnd, isRescheduled: true },
            include: this.defaultInclude(),
        });
    }

    /** Task 5.3: Doctor – update status */
    async updateStatus(id: string, data: UpdateBookingStatusInput, doctorId: string) {
        const prisma = await getPrisma();
        const booking = await this.getById(id);
        if (booking.adminId !== doctorId) throw new ForbiddenException('Not your booking');
        return prisma.booking.update({
            where: { id },
            data: { status: data.status },
            include: this.defaultInclude(),
        });
    }

    async updateNotes(id: string, data: UpdateBookingNotesInput, doctorId: string) {
        const prisma = await getPrisma();
        const booking = await this.getById(id);
        if (booking.adminId !== doctorId) throw new ForbiddenException('Not your booking');
        return prisma.booking.update({
            where: { id },
            data: { notes: data.notes },
            include: this.defaultInclude(),
        });
    }

    async confirm(id: string, userId: string) {
        const prisma = await getPrisma();
        const booking = await prisma.booking.findUnique({ where: { id } });
        if (!booking) throw new NotFoundException('Booking not found');
        if (booking.userId !== userId) throw new ForbiddenException('Cannot confirm others\' bookings');
        if (booking.status !== 'pending') throw new BadRequestException('Only pending bookings can be confirmed');

        return prisma.booking.update({
            where: { id },
            data: { status: 'confirmed' },
            include: this.defaultInclude(),
        });
    }

    /** Task 5.3: Doctor – create follow-up */
    async createFollowUp(doctorId: string, data: CreateFollowUpInput) {
        const prisma = await getPrisma();

        // Ensure doctor had a completed session with this user before
        const pastSession = await prisma.booking.findFirst({
            where: {
                adminId: doctorId,
                userId: data.userId,
                status: 'completed',
                deletedAt: null,
            },
        });
        if (!pastSession) {
            throw new ForbiddenException('Can only create follow-up for users with a completed session');
        }

        const startAt = new Date(data.startAt);
        const endAt = new Date(data.endAt);
        await this.validateBooking(doctorId, startAt, endAt, undefined, true);

        const priceCfg = await prisma.system.findUnique({ where: { key: 'follow_up_price' } });
        const amount = parseFloat(priceCfg?.value ?? '0');

        return prisma.booking.create({
            data: {
                userId: data.userId,
                adminId: doctorId,
                startAt, endAt, amount,
                notes: data.notes,
                status: 'payment_pending',
                madeByType: 'doctor',
                madeById: doctorId,
            },
            include: this.defaultInclude(),
        });
    }

    /** Task 5.3: get doctor's own bookings */
    async getMyBookings(adminId: string, filters: BookingFiltersInput = {}, isSuperAdmin = false) {
        const prisma = await getPrisma();
        return prisma.booking.findMany({
            where: {
                adminId: (isSuperAdmin && !filters.adminId) ? undefined : (filters.adminId || adminId),
                deletedAt: null,
                status: filters.status,
                userId: filters.userId,
                startAt: filters.dateFrom ? { gte: new Date(filters.dateFrom) } : undefined,
                endAt: filters.dateTo ? { lte: new Date(filters.dateTo) } : undefined,
            },
            include: this.defaultInclude(),
            orderBy: { startAt: 'desc' },
        });
    }

    /** Task 5.3: get user's current/upcoming bookings */
    async getUserBookings(userId: string) {
        const prisma = await getPrisma();
        return prisma.booking.findMany({
            where: {
                userId,
                deletedAt: null,
                status: { notIn: ['canceled', 'completed'] },
                startAt: { gte: new Date() },
            },
            include: this.defaultInclude(),
            orderBy: { startAt: 'asc' },
        });
    }

    /** Task 5.3: get user's booking history */
    async getUserBookingHistory(userId: string) {
        const prisma = await getPrisma();
        return prisma.booking.findMany({
            where: {
                userId,
                deletedAt: null,
                OR: [
                    { status: { in: ['completed', 'canceled'] } },
                    { startAt: { lt: new Date() } },
                ],
            },
            include: this.defaultInclude(),
            orderBy: { startAt: 'desc' },
        });
    }

    /**
     * User: cancel own booking
     * Client can only cancel payment_pending or pending bookings
     */
    async userCancel(id: string, userId: string) {
        const prisma = await getPrisma();
        const booking = await this.getById(id);

        if (booking.userId !== userId) throw new ForbiddenException('Not your booking');

        if (!CANCELLABLE_STATUSES.includes(booking.status)) {
            throw new ForbiddenException(
                `Cannot cancel a booking in status '${booking.status}'. Only ${CANCELLABLE_STATUSES.join(', ')} bookings can be cancelled by the user.`
            );
        }

        return prisma.booking.update({
            where: { id },
            data: { status: 'canceled', cancelledBy: userId },
            include: this.defaultInclude(),
        });
    }

    /**
     * User: reschedule own booking
     * Client can reschedule payment_pending, pending, or confirmed bookings
     */
    async userReschedule(id: string, userId: string, data: RescheduleBookingInput) {
        const prisma = await getPrisma();
        const booking = await this.getById(id);

        if (booking.userId !== userId) throw new ForbiddenException('Not your booking');

        if (!RESCHEDULABLE_STATUSES.includes(booking.status)) {
            throw new ForbiddenException(
                `Cannot reschedule a booking in status '${booking.status}'.`
            );
        }

        // For confirmed bookings, check reschedule_allowed_time
        if (booking.status === 'confirmed') {
            const allowedHoursCfg = await prisma.system.findUnique({ where: { key: 'reschedule_allowed_time' } });
            const allowedHours = parseInt(allowedHoursCfg?.value ?? '24', 10);
            const hoursUntilBooking = (booking.startAt.getTime() - Date.now()) / 3600000;
            if (hoursUntilBooking < allowedHours) {
                throw new ForbiddenException(`Can only reschedule at least ${allowedHours}h before the appointment`);
            }
        }

        const newStart = new Date(data.startAt);
        const newEnd = new Date(data.endAt);
        await this.validateBooking(booking.adminId, newStart, newEnd, id);

        return prisma.booking.update({
            where: { id },
            data: { startAt: newStart, endAt: newEnd, isRescheduled: true },
            include: this.defaultInclude(),
        });
    }

    // ──────────────────────────────────────────────────────────────────────────
    private defaultInclude() {
        return {
            user: { select: { id: true, name: true, email: true, avatarUrl: true } },
            admin: { select: { id: true, name: true, email: true } },
            reports: { select: { id: true, createdAt: true } },
        };
    }
}
