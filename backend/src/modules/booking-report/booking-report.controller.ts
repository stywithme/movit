import {
    Controller, Get, Post, Put, Delete,
    Param, Body, UseGuards, Req,
    HttpCode, HttpStatus, BadRequestException,
} from '@nestjs/common';
import { BookingReportService } from './booking-report.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { DoctorGuard, DoctorOnly } from '@/lib/guards/doctor.guard';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { MobileAuthGuard, MobileAuth } from '@/lib/guards/mobile-auth.guard';
import { createBookingReportSchema, updateBookingReportSchema } from './booking-report.types';
import type { Request } from 'express';

// ─── Admin Controller ──────────────────────────────────────────────────────────
@Controller('admin/booking-reports')
export class BookingReportController {
    constructor(private readonly service: BookingReportService) { }

    /** GET /admin/booking-reports/mine — Doctor: own reports */
    @Get('mine')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async getMine(@Req() req: Request) {
        const admin = (req as any).admin;
        return { success: true, data: await this.service.getByDoctor(admin.adminId) };
    }

    /** POST /admin/booking-reports — Doctor: create report */
    @Post()
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async createByDoctor(@Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = createBookingReportSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.create(parseResult.data, admin.adminId) };
    }

    /** PUT /admin/booking-reports/:id — Doctor: update own report */
    @Put(':id/doctor')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async updateByDoctor(@Param('id') id: string, @Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = updateBookingReportSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.update(id, parseResult.data, admin.adminId) };
    }

    /** GET /admin/booking-reports */
    @Get()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('read', 'BookingReport')
    async getAll() {
        return { success: true, data: await this.service.getAll() };
    }

    /** GET /admin/booking-reports/:id */
    @Get(':id')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly({ blockDoctors: false })
    @CheckPermission('read', 'BookingReport')
    async getById(@Param('id') id: string) {
        return { success: true, data: await this.service.getById(id) };
    }

    /** PUT /admin/booking-reports/:id */
    @Put(':id')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly({ blockDoctors: false })
    @CheckPermission('update', 'BookingReport')
    async update(@Param('id') id: string, @Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = updateBookingReportSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.update(id, parseResult.data, admin.adminId) };
    }

    /** DELETE /admin/booking-reports/:id */
    @Delete(':id')
    @HttpCode(HttpStatus.OK)
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('delete', 'BookingReport')
    async delete(@Param('id') id: string) {
        await this.service.delete(id);
        return { success: true, message: 'Report deleted' };
    }
}

// ─── User (Mobile) Controller ──────────────────────────────────────────────────
@Controller('bookings')
@UseGuards(MobileAuthGuard)
@MobileAuth()
export class UserBookingReportController {
    constructor(private readonly service: BookingReportService) { }

    /** GET /bookings/:id/report */
    @Get(':id/report')
    async getReportForBooking(@Param('id') bookingId: string, @Req() req: Request) {
        const userId = (req as any).userId;
        const reports = await this.service.getByBooking(bookingId);

        // Verify ownership: report belongs to this user's booking
        if (reports.length > 0 && (reports[0] as any).booking?.user?.id !== userId) {
            throw new BadRequestException('Access denied');
        }

        return { success: true, data: reports };
    }
}
