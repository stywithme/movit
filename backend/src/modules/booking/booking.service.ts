import {
  Injectable,
  BadRequestException,
  ConflictException,
  NotFoundException,
  ForbiddenException,
  Logger,
} from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import {
  CreateBookingInput,
  UserCreateBookingInput,
  UpdateBookingInput,
  UpdateBookingStatusInput,
  UpdateBookingNotesInput,
  CreateFollowUpInput,
  RescheduleBookingInput,
  BookingFiltersInput,
} from './booking.types';
import { GoogleMeetService } from '@/modules/google-meet/google-meet.service';

// ─── Helper: parse HH:mm ──────────────────────────────────────────────────────
function toMinutes(time: string): number {
  const [h, m] = time.split(':').map(Number);
  return h * 60 + m;
}

// ─── Day name ─────────────────────────────────────────────────────────────────
const DAY_NAMES = [
  'Sunday',
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
];

// ─── Status permission helpers ────────────────────────────────────────────────
/** Statuses where the user/client can cancel */
const USER_CANCELLABLE_STATUSES = ['payment_pending', 'pending'];

/** Statuses where the user/client can reschedule */
const USER_RESCHEDULABLE_STATUSES = ['payment_pending', 'pending', 'confirmed'];

/** Terminal statuses — no changes allowed */
const TERMINAL_STATUSES = ['completed', 'canceled'];
const USER_PAYMENT_LOCK_STATUSES = [
  'creating',
  'authorized',
  'capture_pending',
  'release_pending',
  'pending_status_update',
];

/** Valid forward status transitions (state machine) */
const VALID_STATUS_TRANSITIONS: Record<string, string[]> = {
  payment_pending: ['pending', 'canceled'],
  pending: ['confirmed', 'canceled'],
  confirmed: ['completed', 'canceled'],
  completed: [],
  canceled: [],
};

function assertValidTransition(from: string, to: string) {
  if (from === to) return;
  const allowed = VALID_STATUS_TRANSITIONS[from];
  if (!allowed || !allowed.includes(to)) {
    throw new BadRequestException(
      `Cannot transition from '${from}' to '${to}'. Allowed: ${(allowed || []).join(', ') || 'none (terminal)'}`,
    );
  }
}

type BookingViewer = 'admin' | 'doctor' | 'user';

type BookingPresentationConfig = {
  confirmedRescheduleAllowedHours: number;
  bookingCurrency: string;
  meetingJoinBeforeMinutes: number;
  meetingJoinAfterMinutes: number;
};

export type JoinWindowState =
  | 'hidden'
  | 'disabled_before'
  | 'disabled_no_link'
  | 'active';

export type JoinWindow = {
  state: JoinWindowState;
  opensAt?: string;
  closesAt?: string;
};

type BookingAllowedActions = {
  canCancel: boolean;
  canReschedule: boolean;
  canJoin: boolean;
  canPay: boolean;
};

type PaymentSummary = {
  amount: number;
  currency: string;
};

type PaymentInfo = {
  checkoutId: string;
  revision: number;
  status: string;
  totalAmount: number;
  currency: string;
  paymentUrlPresent: boolean;
  lastError?: string;
  paidAt?: string;
  lastUpdated: string;
};

type PaymentInfoItem = {
  bookingPayment: {
    id: string;
    revision: number;
    status: string;
    totalAmount: number;
    currency: string;
    paymentUrl: string | null;
    lastError: string | null;
    paidAt: Date | null;
    updatedAt: Date;
  };
};

type BookingPresentationLike = BookingLike & {
  admin:
    | {
        id?: string;
        name?: string;
        email?: string | null;
        avatarUrl?: string | null;
        specialty?: string | null;
      }
    | null
    | undefined;
  amount?: number;
  paymentItems?: PaymentInfoItem[];
};

type BookingLike = {
  status: string;
  sessionUrl: string | null;
  startAt: Date;
  endAt: Date;
  [key: string]: any;
};

@Injectable()
export class BookingService {
  private readonly logger = new Logger(BookingService.name);

  constructor(private readonly googleMeetService: GoogleMeetService) {}

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
    const durationCfg = await prisma.system.findUnique({
      where: { key: 'booking_duration' },
    });
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
      blockedIntervals.push({
        start: toMinutes(ct.fromTime),
        end: toMinutes(ct.toTime),
      });
    }

    for (const b of existingBookings) {
      const s = b.startAt.getHours() * 60 + b.startAt.getMinutes();
      const e = b.endAt.getHours() * 60 + b.endAt.getMinutes();
      blockedIntervals.push({ start: s, end: e });
    }

    // 6. Get min_booking_hours for today filter
    const minHoursCfg = await prisma.system.findUnique({
      where: { key: 'min_booking_hours' },
    });
    const minHours = parseInt(minHoursCfg?.value ?? '2', 10);

    // 7. Generate slots from work times minus blocked
    const slots: { startAt: string; endAt: string; durationMinutes: number }[] =
      [];
    const now = new Date();
    const isToday = targetDate.toDateString() === now.toDateString();
    // Add minHours buffer for today's slots
    const minFutureMin = isToday
      ? now.getHours() * 60 + now.getMinutes() + minHours * 60
      : 0;

    for (const wt of workTimes) {
      let cursor = toMinutes(wt.startTime);
      const end = toMinutes(wt.endTime);

      while (cursor + durationMin <= end) {
        const slotEnd = cursor + durationMin;
        const isBlocked = blockedIntervals.some(
          (b) => cursor < b.end && slotEnd > b.start,
        );

        if (!isBlocked && cursor >= minFutureMin) {
          const slotStartAt = new Date(targetDate);
          slotStartAt.setHours(Math.floor(cursor / 60), cursor % 60, 0, 0);
          const slotEndAt = new Date(targetDate);
          slotEndAt.setHours(Math.floor(slotEnd / 60), slotEnd % 60, 0, 0);

          slots.push({
            startAt: slotStartAt.toISOString(),
            endAt: slotEndAt.toISOString(),
            durationMinutes: durationMin,
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
          select: {
            id: true,
            name: true,
            email: true,
            isDoctor: true,
            isActive: true,
            deletedAt: true,
          },
        },
      },
    });

    // Filter to active doctors only
    const doctors = workTimes
      .filter(
        (wt) => wt.admin.isDoctor && wt.admin.isActive && !wt.admin.deletedAt,
      )
      .map((wt) => wt.admin);

    // Deduplicate by id
    const unique = new Map(doctors.map((d) => [d.id, d]));
    return Array.from(unique.values()).map((doctor) =>
      this.presentDoctorSummary(doctor),
    );
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Validation
  // ──────────────────────────────────────────────────────────────────────────

  /**
   * Task 5.2: validateBooking
   * Centralised booking validation used by create and reschedule.
   */
  private async validateBooking(
    adminId: string,
    startAt: Date,
    endAt: Date,
    excludeBookingId?: string,
    isFollowUp: boolean = false,
  ) {
    const prisma = await getPrisma();

    // 1. System: allow_booking
    const allowCfg = await prisma.system.findUnique({
      where: { key: 'allow_booking' },
    });
    if (allowCfg?.value !== 'true')
      throw new BadRequestException('Booking is currently disabled');

    // 2. Future check + min_booking_hours
    const minHoursCfg = await prisma.system.findUnique({
      where: { key: 'min_booking_hours' },
    });
    const minHours = parseInt(minHoursCfg?.value ?? '2', 10);
    const minAllowedDate = new Date(Date.now() + minHours * 3600000);
    if (startAt <= minAllowedDate) {
      throw new BadRequestException(
        `Booking must be at least ${minHours} hour(s) in advance`,
      );
    }

    // 3. Max advance days — inclusive calendar days (UTC): last allowed day is today + maxDays
    if (!isFollowUp) {
      const maxDaysCfg = await prisma.system.findUnique({
        where: { key: 'max_advance_booking_days' },
      });
      const maxDays = Math.max(0, parseInt(maxDaysCfg?.value ?? '30', 10) || 30);
      const startDay = Date.UTC(
        startAt.getUTCFullYear(),
        startAt.getUTCMonth(),
        startAt.getUTCDate(),
      );
      const now = new Date();
      const todayDay = Date.UTC(
        now.getUTCFullYear(),
        now.getUTCMonth(),
        now.getUTCDate(),
      );
      const lastAllowedDay = todayDay + maxDays * 86400000;
      if (startDay > lastAllowedDay) {
        throw new BadRequestException(
          `Cannot book more than ${maxDays} days in advance`,
        );
      }
    }

    // 4. Duration match
    const durationCfg = await prisma.system.findUnique({
      where: { key: 'booking_duration' },
    });
    const expectedDuration = parseInt(durationCfg?.value ?? '30', 10);
    const actualDuration = (endAt.getTime() - startAt.getTime()) / 60000;
    if (actualDuration !== expectedDuration) {
      throw new BadRequestException(
        `Booking duration must be exactly ${expectedDuration} minutes`,
      );
    }

    // 5. In doctor work time
    const dayName = DAY_NAMES[startAt.getDay()];
    const startMin = startAt.getHours() * 60 + startAt.getMinutes();
    const endMin = endAt.getHours() * 60 + endAt.getMinutes();

    const workTimes = await prisma.doctorWorkTime.findMany({
      where: { adminId, day: dayName },
    });
    const inWorkTime = workTimes.some(
      (wt) =>
        startMin >= toMinutes(wt.startTime) && endMin <= toMinutes(wt.endTime),
    );
    if (!inWorkTime)
      throw new BadRequestException('Slot is outside doctor working hours');

    // 6. Not in close time
    const closeTimes = await prisma.closeTime.findMany({
      where: {
        OR: [{ adminId }, { adminId: null }],
        fromDate: { lte: startAt },
        toDate: { gte: startAt },
      },
    });
    const isBlocked = closeTimes.some(
      (ct) =>
        startMin < toMinutes(ct.toTime) && endMin > toMinutes(ct.fromTime),
    );
    if (isBlocked)
      throw new BadRequestException('Slot falls within a close time / holiday');

    // 7. Conflict with existing bookings
    const conflict = await prisma.booking.findFirst({
      where: {
        adminId,
        deletedAt: null,
        status: { notIn: ['canceled'] },
        id: excludeBookingId ? { not: excludeBookingId } : undefined,
        OR: [{ startAt: { lt: endAt }, endAt: { gt: startAt } }],
      },
    });
    if (conflict) throw new ConflictException('This slot is already booked');
  }

  /** Public rules for mobile date pickers (must stay in sync with validateBooking). */
  async getBookingRules() {
    const prisma = await getPrisma();
    const keys = [
      'allow_booking',
      'max_advance_booking_days',
      'min_booking_hours',
      'booking_duration',
    ] as const;
    const rows = await prisma.system.findMany({
      where: { key: { in: [...keys] } },
    });
    const map = Object.fromEntries(rows.map((r) => [r.key, r.value])) as Record<
      string,
      string
    >;
    return {
      allowBooking: map['allow_booking'] === 'true',
      maxAdvanceBookingDays: Math.max(
        0,
        parseInt(map['max_advance_booking_days'] ?? '30', 10) || 30,
      ),
      minBookingHours: Math.max(
        0,
        parseInt(map['min_booking_hours'] ?? '2', 10) || 2,
      ),
      bookingDurationMinutes: Math.max(
        1,
        parseInt(map['booking_duration'] ?? '30', 10) || 30,
      ),
    };
  }

  // ──────────────────────────────────────────────────────────────────────────
  // CRUD
  // ──────────────────────────────────────────────────────────────────────────

  async create(
    data: CreateBookingInput | UserCreateBookingInput,
    creator?: {
      id: string;
      type: 'admin' | 'doctor' | 'user';
      isDoctor: boolean;
      isSuperAdmin: boolean;
    },
  ) {
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
        throw new ForbiddenException(
          'Doctors can only create bookings for users they have seen before',
        );
      }
      // Ensure doctor is booking for themselves
      if (data.adminId !== creator.id) {
        throw new ForbiddenException(
          'Doctors can only create bookings for themselves',
        );
      }
    }

    await this.validateBooking(data.adminId, startAt, endAt);

    const priceCfg = await prisma.system.findUnique({
      where: { key: 'booking_price' },
    });
    const isUserCreate = creator?.type === 'user';
    // User create: always use booking_price; admin/doctor can override amount
    const amount = isUserCreate
      ? parseFloat(priceCfg?.value ?? '0')
      : ((data as CreateBookingInput).amount ??
        parseFloat(priceCfg?.value ?? '0'));

    // Determine madeByType
    const madeByType = creator?.type ?? 'admin';
    const madeById = creator?.id ?? null;

    // User create: never accept payment/session fields from client
    const fullData = data as CreateBookingInput;
    const paymentStatus = isUserCreate ? undefined : fullData.paymentStatus;
    const paymentId = isUserCreate ? undefined : fullData.paymentId;
    const paymentGateway = isUserCreate ? undefined : fullData.paymentGateway;
    const sessionUrl = isUserCreate ? undefined : fullData.sessionUrl;

    const booking = await prisma.booking.create({
      data: {
        userId: data.userId,
        adminId: data.adminId,
        startAt,
        endAt,
        amount,
        // All new bookings start as payment_pending
        status: 'payment_pending',
        paymentStatus,
        paymentId,
        paymentGateway,
        sessionUrl,
        notes: data.notes,
        madeByType,
        madeById,
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(booking, this.viewerFromCreator(creator));
  }

  /** Task 5.3: getAll */
  async getAll(filters: BookingFiltersInput = {}) {
    const prisma = await getPrisma();

    let startAtFilter: object | undefined;
    if (filters.date) {
      const dayStart = new Date(filters.date + 'T00:00:00.000Z');
      const dayEnd = new Date(filters.date + 'T23:59:59.999Z');
      startAtFilter = { gte: dayStart, lte: dayEnd };
    } else if (filters.dateFrom) {
      startAtFilter = { gte: new Date(filters.dateFrom) };
    }

    const bookings = await prisma.booking.findMany({
      where: {
        deletedAt: null,
        status: filters.status,
        adminId: filters.adminId,
        userId: filters.userId,
        startAt: startAtFilter,
        endAt: filters.dateTo ? { lte: new Date(filters.dateTo) } : undefined,
      },
      include: this.defaultInclude(),
      orderBy: { startAt: 'desc' },
    });
    return this.presentBookings(bookings, 'admin');
  }

  private async getRawById(id: string) {
    const prisma = await getPrisma();
    const booking = await prisma.booking.findFirst({
      where: { id, deletedAt: null },
      include: this.defaultInclude(),
    });
    if (!booking) throw new NotFoundException('Booking not found');
    return booking;
  }

  /** Task 5.3: getById */
  async getById(id: string) {
    const booking = await this.getRawById(id);
    return this.presentBooking(booking, 'admin');
  }

  /** Task 5.3: update (admin full update) */
  async update(id: string, data: UpdateBookingInput) {
    const prisma = await getPrisma();
    const existing = await this.getRawById(id);

    if (data.status && data.status !== existing.status) {
      assertValidTransition(existing.status, data.status);
      if (data.status === 'pending' && existing.status === 'payment_pending' && !data.paymentStatus) {
        data = { ...data, paymentStatus: 'paid' as const };
      }
    }

    if (data.status === 'confirmed' && existing.status !== 'confirmed') {
      const conferenceData = await this.buildMeetSpaceData(id, existing.adminId);
      data = { ...data, ...conferenceData };
    }

    const booking = await prisma.booking.update({
      where: { id },
      data,
      include: this.defaultInclude(),
    });
    return this.presentBooking(booking, 'admin');
  }

  /** Task 5.3: soft delete */
  async delete(id: string) {
    const prisma = await getPrisma();
    await this.getRawById(id);
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
    const booking = await this.getRawById(id);
    if (TERMINAL_STATUSES.includes(booking.status)) {
      throw new BadRequestException(
        'Cannot cancel a completed or already-cancelled booking',
      );
    }
    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: {
        status: 'canceled',
        cancelledBy,
        sessionUrl: null,
        sessionMeetingId: null,
        sessionPlatform: null,
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'admin');
  }

  /**
   * Task 5.3: reschedule
   * Rules:
   *  - payment_pending → allowed ✅ (subject to reschedule_allowed_time)
   *  - pending         → allowed ✅ (subject to reschedule_allowed_time)
   *  - confirmed       → allowed ✅ (subject to reschedule_allowed_time, resets to pending)
   *  - completed       → NOT allowed ❌
   *  - canceled        → NOT allowed ❌
   */
  async reschedule(id: string, data: RescheduleBookingInput) {
    const prisma = await getPrisma();
    const booking = await this.getRawById(id);
    const wasConfirmed = booking.status === 'confirmed';

    if (TERMINAL_STATUSES.includes(booking.status)) {
      throw new BadRequestException(
        'Cannot reschedule a completed or cancelled booking',
      );
    }

    const allowedHoursCfg = await prisma.system.findUnique({
      where: { key: 'reschedule_allowed_time' },
    });
    const allowedHours = parseInt(allowedHoursCfg?.value ?? '24', 10);
    const hoursUntilBooking =
      (booking.startAt.getTime() - Date.now()) / 3600000;
    if (hoursUntilBooking < allowedHours) {
      throw new ForbiddenException(
        `Can only reschedule at least ${allowedHours}h before the appointment`,
      );
    }

    const newStart = new Date(data.startAt);
    const newEnd = new Date(data.endAt);
    await this.validateBooking(booking.adminId, newStart, newEnd, id);

    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: {
        startAt: newStart,
        endAt: newEnd,
        isRescheduled: true,
        ...(wasConfirmed ? {
          status: 'pending' as const,
          sessionUrl: null,
          sessionMeetingId: null,
          sessionPlatform: null,
        } : {}),
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'admin');
  }

  /** Task 5.3: Doctor – update status */
  async updateStatus(
    id: string,
    data: UpdateBookingStatusInput,
    doctorId: string,
  ) {
    const prisma = await getPrisma();
    const booking = await this.getRawById(id);
    if (booking.adminId !== doctorId)
      throw new ForbiddenException('Not your booking');

    if (booking.status === data.status) {
      const unchangedBooking = await prisma.booking.findUniqueOrThrow({
        where: { id },
        include: this.defaultInclude(),
      });
      return this.presentBooking(unchangedBooking, 'doctor');
    }

    assertValidTransition(booking.status, data.status);

    if (data.status === 'pending' && booking.status === 'payment_pending') {
      const updatedBooking = await prisma.booking.update({
        where: { id },
        data: { status: 'pending', paymentStatus: 'paid' },
        include: this.defaultInclude(),
      });
      return this.presentBooking(updatedBooking, 'doctor');
    }

    if (data.status === 'confirmed') {
      const conferenceData = await this.buildMeetSpaceData(id, doctorId);
      const updatedBooking = await prisma.booking.update({
        where: { id },
        data: {
          status: 'confirmed',
          ...conferenceData,
        },
        include: this.defaultInclude(),
      });
      return this.presentBooking(updatedBooking, 'doctor');
    }

    if (data.status === 'canceled') {
      const updatedBooking = await prisma.booking.update({
        where: { id },
        data: {
          status: 'canceled',
          sessionUrl: null,
          sessionMeetingId: null,
          sessionPlatform: null,
        },
        include: this.defaultInclude(),
      });
      return this.presentBooking(updatedBooking, 'doctor');
    }

    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: { status: data.status },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'doctor');
  }

  async updateNotes(
    id: string,
    data: UpdateBookingNotesInput,
    doctorId: string,
  ) {
    const prisma = await getPrisma();
    const booking = await this.getRawById(id);
    if (booking.adminId !== doctorId)
      throw new ForbiddenException('Not your booking');
    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: { notes: data.notes },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'doctor');
  }

  async confirm(id: string, userId: string) {
    const prisma = await getPrisma();
    const booking = await prisma.booking.findUnique({ where: { id } });
    if (!booking) throw new NotFoundException('Booking not found');
    if (booking.userId !== userId)
      throw new ForbiddenException("Cannot confirm others' bookings");
    if (booking.status === 'confirmed') {
      if (booking.sessionMeetingId && booking.sessionUrl) {
        const unchangedBooking = await prisma.booking.findUniqueOrThrow({
          where: { id },
          include: this.defaultInclude(),
        });
        return this.presentBooking(unchangedBooking, 'user');
      }

      const conferenceData = await this.buildMeetSpaceData(id, booking.adminId);
      const updatedBooking = await prisma.booking.update({
        where: { id },
        data: conferenceData,
        include: this.defaultInclude(),
      });
      return this.presentBooking(updatedBooking, 'user');
    }

    if (booking.status !== 'pending')
      throw new BadRequestException('Only pending bookings can be confirmed');

    const conferenceData = await this.buildMeetSpaceData(id, booking.adminId);
    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: {
        status: 'confirmed',
        ...conferenceData,
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'user');
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
      throw new ForbiddenException(
        'Can only create follow-up for users with a completed session',
      );
    }

    const startAt = new Date(data.startAt);
    const endAt = new Date(data.endAt);
    await this.validateBooking(doctorId, startAt, endAt, undefined, true);

    const priceCfg = await prisma.system.findUnique({
      where: { key: 'follow_up_price' },
    });
    const amount = parseFloat(priceCfg?.value ?? '0');

    const booking = await prisma.booking.create({
      data: {
        userId: data.userId,
        adminId: doctorId,
        startAt,
        endAt,
        amount,
        notes: data.notes,
        status: 'payment_pending',
        madeByType: 'doctor',
        madeById: doctorId,
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(booking, 'doctor');
  }

  /** Task 5.3: get doctor's own bookings */
  async getMyBookings(
    adminId: string,
    filters: BookingFiltersInput = {},
    isSuperAdmin = false,
  ) {
    const prisma = await getPrisma();

    let startAtFilter: object | undefined;
    if (filters.date) {
      const dayStart = new Date(filters.date + 'T00:00:00.000Z');
      const dayEnd = new Date(filters.date + 'T23:59:59.999Z');
      startAtFilter = { gte: dayStart, lte: dayEnd };
    } else if (filters.dateFrom) {
      startAtFilter = { gte: new Date(filters.dateFrom) };
    }

    const bookings = await prisma.booking.findMany({
      where: {
        adminId:
          isSuperAdmin && !filters.adminId
            ? undefined
            : filters.adminId || adminId,
        deletedAt: null,
        status: filters.status,
        userId: filters.userId,
        startAt: startAtFilter,
        endAt: filters.dateTo ? { lte: new Date(filters.dateTo) } : undefined,
      },
      include: this.defaultInclude(),
      orderBy: { startAt: 'desc' },
    });
    return this.presentBookings(bookings, 'doctor');
  }

  /** Task 5.3: get user's current/upcoming bookings (includes grace period after meeting end) */
  async getUserBookings(userId: string) {
    const prisma = await getPrisma();
    const afterCfg = await prisma.system.findUnique({
      where: { key: 'meeting_join_after_minutes' },
    });
    const afterMin = Math.max(0, parseInt(afterCfg?.value ?? '15', 10));
    const graceEnd = new Date(Date.now() - afterMin * 60 * 1000);

    const bookings = await prisma.booking.findMany({
      where: {
        userId,
        deletedAt: null,
        status: { notIn: ['canceled', 'completed'] },
        OR: [{ startAt: { gte: new Date() } }, { endAt: { gte: graceEnd } }],
      },
      include: this.defaultInclude(),
      orderBy: { startAt: 'asc' },
    });
    return this.presentBookings(bookings, 'user');
  }

  /** Task 5.3: get user's booking history */
  async getUserBookingHistory(userId: string) {
    const prisma = await getPrisma();
    const afterCfg = await prisma.system.findUnique({
      where: { key: 'meeting_join_after_minutes' },
    });
    const afterMin = Math.max(0, parseInt(afterCfg?.value ?? '15', 10));
    const graceEnd = new Date(Date.now() - afterMin * 60 * 1000);

    const bookings = await prisma.booking.findMany({
      where: {
        userId,
        deletedAt: null,
        OR: [
          { status: { in: ['completed', 'canceled'] } },
          {
            AND: [{ startAt: { lt: new Date() } }, { endAt: { lt: graceEnd } }],
          },
        ],
      },
      include: this.defaultInclude(),
      orderBy: { startAt: 'desc' },
    });
    return this.presentBookings(bookings, 'user');
  }

  /**
   * User: cancel own booking
   * Client can only cancel payment_pending or pending bookings
   */
  async userCancel(id: string, userId: string) {
    const prisma = await getPrisma();
    const booking = await this.getRawById(id);

    if (booking.userId !== userId)
      throw new ForbiddenException('Not your booking');

    if (!USER_CANCELLABLE_STATUSES.includes(booking.status)) {
      throw new ForbiddenException(
        `Cannot cancel a booking in status '${booking.status}'. Only ${USER_CANCELLABLE_STATUSES.join(', ')} bookings can be cancelled by the user.`,
      );
    }

    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: {
        status: 'canceled',
        cancelledBy: userId,
        sessionUrl: null,
        sessionMeetingId: null,
        sessionPlatform: null,
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'user');
  }

  /**
   * User: reschedule own booking
   * Client can reschedule payment_pending, pending, or confirmed bookings
   */
  async userReschedule(
    id: string,
    userId: string,
    data: RescheduleBookingInput,
  ) {
    const prisma = await getPrisma();
    const booking = await this.getRawById(id);
    const wasConfirmed = booking.status === 'confirmed';

    if (booking.userId !== userId)
      throw new ForbiddenException('Not your booking');

    if (!USER_RESCHEDULABLE_STATUSES.includes(booking.status)) {
      throw new ForbiddenException(
        `Cannot reschedule a booking in status '${booking.status}'.`,
      );
    }

    const allowedHoursCfg = await prisma.system.findUnique({
      where: { key: 'reschedule_allowed_time' },
    });
    const allowedHours = parseInt(allowedHoursCfg?.value ?? '24', 10);
    const hoursUntilBooking =
      (booking.startAt.getTime() - Date.now()) / 3600000;
    if (hoursUntilBooking < allowedHours) {
      throw new ForbiddenException(
        `Can only reschedule at least ${allowedHours}h before the appointment`,
      );
    }

    const newStart = new Date(data.startAt);
    const newEnd = new Date(data.endAt);
    await this.validateBooking(booking.adminId, newStart, newEnd, id);

    const updatedBooking = await prisma.booking.update({
      where: { id },
      data: {
        startAt: newStart,
        endAt: newEnd,
        isRescheduled: true,
        ...(wasConfirmed ? {
          status: 'pending' as const,
          sessionUrl: null,
          sessionMeetingId: null,
          sessionPlatform: null,
        } : {}),
      },
      include: this.defaultInclude(),
    });
    return this.presentBooking(updatedBooking, 'user');
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Response presentation helpers
  // ──────────────────────────────────────────────────────────────────────────

  private viewerFromCreator(creator?: {
    type: 'admin' | 'doctor' | 'user';
  }): BookingViewer {
    switch (creator?.type) {
      case 'user':
        return 'user';
      case 'doctor':
        return 'doctor';
      default:
        return 'admin';
    }
  }

  private presentDoctorSummary<
    T extends
      | {
          id?: string;
          name?: string;
          email?: string | null;
          avatarUrl?: string | null;
          specialty?: string | null;
          [key: string]: unknown;
        }
      | null
      | undefined,
  >(doctor: T) {
    if (!doctor) return doctor ?? null;
    return {
      ...doctor,
      avatarUrl: doctor.avatarUrl ?? null,
      specialty: doctor.specialty ?? null,
    };
  }

  private async getBookingPresentationConfig(): Promise<BookingPresentationConfig> {
    const prisma = await getPrisma();
    const [allowedHoursCfg, currencyCfg, joinBeforeCfg, joinAfterCfg] =
      await Promise.all([
        prisma.system.findUnique({ where: { key: 'reschedule_allowed_time' } }),
        prisma.system.findUnique({ where: { key: 'booking_currency' } }),
        prisma.system.findUnique({
          where: { key: 'meeting_join_before_minutes' },
        }),
        prisma.system.findUnique({
          where: { key: 'meeting_join_after_minutes' },
        }),
      ]);

    return {
      confirmedRescheduleAllowedHours: parseInt(
        allowedHoursCfg?.value ?? '24',
        10,
      ),
      bookingCurrency: currencyCfg?.value || 'EGP',
      meetingJoinBeforeMinutes: Math.max(
        0,
        parseInt(joinBeforeCfg?.value ?? '30', 10) || 30,
      ),
      meetingJoinAfterMinutes: Math.max(
        0,
        parseInt(joinAfterCfg?.value ?? '15', 10) || 15,
      ),
    };
  }

  private extractPaymentInfo(
    items: Array<{
      bookingPayment: {
        id: string;
        revision: number;
        status: string;
        totalAmount: number;
        currency: string;
        paymentUrl: string | null;
        lastError: string | null;
        paidAt: Date | null;
        updatedAt: Date;
      };
    }>,
  ): PaymentInfo | undefined {
    if (!items?.length) return undefined;
    const latest = items.reduce((a, b) =>
      b.bookingPayment.revision > a.bookingPayment.revision ||
      (b.bookingPayment.revision === a.bookingPayment.revision &&
        b.bookingPayment.updatedAt > a.bookingPayment.updatedAt)
        ? b
        : a,
    );
    const bp = latest.bookingPayment;
    return {
      checkoutId: bp.id,
      revision: bp.revision,
      status: bp.status,
      totalAmount: bp.totalAmount,
      currency: bp.currency,
      paymentUrlPresent: Boolean(bp.paymentUrl?.trim()),
      lastError: bp.lastError ?? undefined,
      paidAt: bp.paidAt?.toISOString(),
      lastUpdated: bp.updatedAt.toISOString(),
    };
  }

  private canRescheduleConfirmedBooking(
    booking: Pick<BookingLike, 'startAt'>,
    config: BookingPresentationConfig,
  ): boolean {
    const hoursUntilBooking =
      (booking.startAt.getTime() - Date.now()) / 3600000;
    return hoursUntilBooking >= config.confirmedRescheduleAllowedHours;
  }

  private buildJoinWindow(
    booking: BookingLike,
    config: BookingPresentationConfig,
  ): { joinWindow: JoinWindow; canJoin: boolean } {
    const now = Date.now();
    const startAt =
      booking.startAt instanceof Date
        ? booking.startAt
        : new Date(booking.startAt);
    const endAt =
      booking.endAt instanceof Date ? booking.endAt : new Date(booking.endAt);

    if (booking.status !== 'confirmed') {
      return { joinWindow: { state: 'hidden' }, canJoin: false };
    }

    const beforeMs = config.meetingJoinBeforeMinutes * 60 * 1000;
    const afterMs = config.meetingJoinAfterMinutes * 60 * 1000;
    const opensAt = new Date(startAt.getTime() - beforeMs);
    const closesAt = new Date(endAt.getTime() + afterMs);

    if (!booking.sessionUrl?.trim()) {
      return {
        joinWindow: {
          state: 'disabled_no_link',
          opensAt: opensAt.toISOString(),
          closesAt: closesAt.toISOString(),
        },
        canJoin: false,
      };
    }

    if (now > closesAt.getTime()) {
      return { joinWindow: { state: 'hidden' }, canJoin: false };
    }

    if (now < opensAt.getTime()) {
      return {
        joinWindow: {
          state: 'disabled_before',
          opensAt: opensAt.toISOString(),
          closesAt: closesAt.toISOString(),
        },
        canJoin: false,
      };
    }

    return {
      joinWindow: {
        state: 'active',
        opensAt: opensAt.toISOString(),
        closesAt: closesAt.toISOString(),
      },
      canJoin: true,
    };
  }

  private buildAllowedActions(
    booking: BookingLike,
    viewer: BookingViewer,
    config: BookingPresentationConfig,
    canJoin: boolean,
    paymentInfo?: PaymentInfo,
  ): BookingAllowedActions {
    const canRescheduleConfirmed = this.canRescheduleConfirmedBooking(
      booking,
      config,
    );
    const paymentLockedForUser =
      viewer === 'user' &&
      booking.status === 'payment_pending' &&
      !!paymentInfo &&
      USER_PAYMENT_LOCK_STATUSES.includes(paymentInfo.status);
    const canPay =
      booking.status === 'payment_pending' &&
      viewer === 'user' &&
      !paymentLockedForUser;

    if (viewer === 'user') {
      return {
        canCancel: USER_CANCELLABLE_STATUSES.includes(booking.status),
        canReschedule:
          USER_RESCHEDULABLE_STATUSES.includes(booking.status) &&
          (booking.status !== 'confirmed' || canRescheduleConfirmed),
        canJoin,
        canPay,
      };
    }

    return {
      canCancel: !TERMINAL_STATUSES.includes(booking.status),
      canReschedule:
        !TERMINAL_STATUSES.includes(booking.status) &&
        (booking.status !== 'confirmed' || canRescheduleConfirmed),
      canJoin,
      canPay: false, // canPay only for user-facing
    };
  }

  private async presentBooking<T extends BookingPresentationLike>(
    booking: T,
    viewer: BookingViewer,
  ): Promise<
    T & {
      allowedActions: BookingAllowedActions;
      joinWindow?: JoinWindow;
      paymentSummary?: PaymentSummary;
      paymentInfo?: PaymentInfo;
    }
  > {
    const config = await this.getBookingPresentationConfig();
    const { joinWindow, canJoin } = this.buildJoinWindow(booking, config);
    const paymentInfo = booking.paymentItems
      ? this.extractPaymentInfo(booking.paymentItems)
      : undefined;
    const allowedActions = this.buildAllowedActions(
      booking,
      viewer,
      config,
      canJoin,
      paymentInfo,
    );
    const paymentSummary: PaymentSummary | undefined =
      allowedActions.canPay && typeof booking.amount === 'number'
        ? { amount: booking.amount, currency: config.bookingCurrency }
        : undefined;
    return {
      ...booking,
      admin: this.presentDoctorSummary(booking.admin),
      allowedActions,
      joinWindow,
      paymentSummary,
      paymentInfo,
    };
  }

  private async presentBookings<T extends BookingPresentationLike>(
    bookings: T[],
    viewer: BookingViewer,
  ): Promise<
    Array<
      T & {
        allowedActions: BookingAllowedActions;
        joinWindow?: JoinWindow;
        paymentSummary?: PaymentSummary;
        paymentInfo?: PaymentInfo;
      }
    >
  > {
    const config = await this.getBookingPresentationConfig();
    return bookings.map((booking) => {
      const { joinWindow, canJoin } = this.buildJoinWindow(booking, config);
      const paymentInfo = booking.paymentItems
        ? this.extractPaymentInfo(booking.paymentItems)
        : undefined;
      const allowedActions = this.buildAllowedActions(
        booking,
        viewer,
        config,
        canJoin,
        paymentInfo,
      );
      const paymentSummary: PaymentSummary | undefined =
        allowedActions.canPay && typeof booking.amount === 'number'
          ? {
              amount: booking.amount,
              currency: config.bookingCurrency,
            }
          : undefined;
      return {
        ...booking,
        admin: this.presentDoctorSummary(booking.admin),
        allowedActions,
        joinWindow,
        paymentSummary,
        paymentInfo,
      };
    });
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Google Meet conference management
  // ──────────────────────────────────────────────────────────────────────────

  /** Build conference fields before mutating a confirmed booking. */
  private async buildMeetSpaceData(bookingId: string, doctorAdminId: string) {
    try {
      const space = await this.googleMeetService.createSpace(doctorAdminId);
      this.logger.log(
        `Meet space created for booking ${bookingId}: ${space.meetingUri}`,
      );
      return {
        sessionUrl: space.meetingUri,
        sessionMeetingId: space.spaceName,
        sessionPlatform: 'google_meet' as const,
      };
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'unknown error';
      this.logger.error(
        `Failed to build Meet space for booking ${bookingId} (doctor ${doctorAdminId}): ${message}`,
      );
      throw new BadRequestException(
        `Could not create Google Meet session: ${message}. ` +
          'Please ensure the doctor has connected their Google account.',
      );
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  private defaultInclude() {
    return {
      user: { select: { id: true, name: true, email: true, avatarUrl: true } },
      admin: { select: { id: true, name: true, email: true } },
      reports: { select: { id: true, createdAt: true } },
      paymentItems: {
        include: {
          bookingPayment: {
            select: {
              id: true,
              revision: true,
              status: true,
              totalAmount: true,
              currency: true,
              paymentUrl: true,
              lastError: true,
              paidAt: true,
              updatedAt: true,
            },
          },
        },
      },
    };
  }
}
