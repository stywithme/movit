import {
    Controller, Get, Post, Put,
    Param, Body, Query, UseGuards, Req,
    BadRequestException,
} from '@nestjs/common';
import { BookingService } from './booking.service';
import { MobileAuthGuard, MobileAuth } from '@/lib/guards/mobile-auth.guard';
import { UserPermissionGuard } from '@/lib/guards/user-permission.guard';
import {
    cancelBookingSchema,
    rescheduleBookingSchema,
    createBookingSchema,
} from './booking.types';
import type { Request } from 'express';

@Controller('bookings')
@UseGuards(MobileAuthGuard, UserPermissionGuard)
@MobileAuth()
export class UserBookingController {
    constructor(private readonly service: BookingService) { }

    /** GET /bookings/rules — limits for mobile booking UI (max advance days, min hours, duration). */
    @Get('rules')
    async getBookingRules() {
        return { success: true, data: await this.service.getBookingRules() };
    }

    /** GET /bookings/available-doctors?date=YYYY-MM-DD */
    @Get('available-doctors')
    async getAvailableDoctors(@Query('date') date: string) {
        if (!date) throw new BadRequestException('date query parameter is required (YYYY-MM-DD)');
        return { success: true, data: await this.service.getAvailableDoctors(date) };
    }

    /** GET /bookings/available-slots/:adminId?date=YYYY-MM-DD */
    @Get('available-slots/:adminId')
    async getAvailableSlots(@Param('adminId') adminId: string, @Query('date') date: string) {
        if (!date) throw new BadRequestException('date query parameter is required (YYYY-MM-DD)');
        return { success: true, data: await this.service.getAvailableSlots(adminId, date) };
    }

    /** GET /bookings/my — current & upcoming */
    @Get('my')
    async getMyBookings(@Req() req: Request) {
        const userId = (req as any).userId;
        return { success: true, data: await this.service.getUserBookings(userId) };
    }

    /** GET /bookings/history */
    @Get('history')
    async getHistory(@Req() req: Request) {
        const userId = (req as any).userId;
        return { success: true, data: await this.service.getUserBookingHistory(userId) };
    }

    /** POST /bookings — create a new booking */
    @Post()
    async create(@Req() req: Request, @Body() body: unknown) {
        const userId = (req as any).userId;

        // ensure user can only book for themselves
        const payload = body as any;
        if (payload?.userId && payload.userId !== userId) {
            throw new BadRequestException('Cannot create booking on behalf of another user');
        }

        const parseResult = createBookingSchema.safeParse({ ...payload, userId });
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return {
            success: true,
            data: await this.service.create(parseResult.data, {
                id: userId,
                type: 'user',
                isDoctor: false,
                isSuperAdmin: false,
            })
        };
    }

    /**
     * PUT /bookings/:id/reschedule
     * Allowed statuses: payment_pending, pending, confirmed
     */
    @Put(':id/reschedule')
    async reschedule(@Param('id') id: string, @Body() body: unknown, @Req() req: Request) {
        const userId = (req as any).userId;

        const parseResult = rescheduleBookingSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.userReschedule(id, userId, parseResult.data) };
    }

    /**
     * PUT /bookings/:id/cancel
     * Allowed statuses: payment_pending, pending
     * Users CANNOT cancel confirmed bookings (only reschedule)
     */
    @Put(':id/cancel')
    async cancel(@Param('id') id: string, @Body() body: unknown, @Req() req: Request) {
        const userId = (req as any).userId;
        cancelBookingSchema.safeParse(body); // parse but optional
        return { success: true, data: await this.service.userCancel(id, userId) };
    }

    /** PUT /bookings/:id/confirm */
    @Put(':id/confirm')
    async confirm(@Param('id') id: string, @Req() req: Request) {
        const userId = (req as any).userId;
        return { success: true, data: await this.service.confirm(id, userId) };
    }
}
