import {
    Controller, Get, Post, Put, Delete,
    Param, Body, Query, UseGuards, Req,
    HttpCode, HttpStatus, BadRequestException,
} from '@nestjs/common';
import { BookingService } from './booking.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { DoctorGuard, DoctorOnly } from '@/lib/guards/doctor.guard';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import {
    createBookingSchema,
    updateBookingSchema,
    updateBookingStatusSchema,
    updateBookingNotesSchema,
    createFollowUpSchema,
    bookingFiltersSchema,
} from './booking.types';
import type { Request } from 'express';

@Controller('admin/bookings')
export class BookingController {
    constructor(private readonly service: BookingService) { }

    // ── Doctor Routes (must be BEFORE :id param routes) ───────────────────────

    /** GET /admin/bookings/mine — Doctor: own bookings */
    @Get('mine')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async getMyBookings(@Req() req: Request, @Query() query: Record<string, string>) {
        const admin = (req as any).admin;
        const parseResult = bookingFiltersSchema.safeParse(query);
        const filters = parseResult.success ? parseResult.data : {};
        return { success: true, data: await this.service.getMyBookings(admin.adminId, filters, admin.isSuperAdmin) };
    }

    /** POST /admin/bookings/follow-up — Doctor: create follow-up */
    @Post('follow-up')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async createFollowUp(@Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = createFollowUpSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.createFollowUp(admin.adminId, parseResult.data) };
    }

    /** PUT /admin/bookings/:id/status — Doctor: change status */
    @Put(':id/status')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async updateStatus(@Param('id') id: string, @Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = updateBookingStatusSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.updateStatus(id, parseResult.data, admin.adminId) };
    }

    /** PUT /admin/bookings/:id/notes — Doctor: update notes */
    @Put(':id/notes')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async updateNotes(@Param('id') id: string, @Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = updateBookingNotesSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.updateNotes(id, parseResult.data, admin.adminId) };
    }

    // ── Admin Routes ────────────────────────────────────────────────────────────

    @Get()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('read', 'Booking')
    async getAll(@Query() query: Record<string, string>) {
        const parseResult = bookingFiltersSchema.safeParse(query);
        const filters = parseResult.success ? parseResult.data : {};
        return { success: true, data: await this.service.getAll(filters) };
    }

    /** GET /admin/bookings/available-doctors */
    @Get('available-doctors')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly({ blockDoctors: false })
    @CheckPermission('read', 'Booking')
    async getAvailableDoctors(@Query('date') date: string) {
        if (!date) throw new BadRequestException('date query parameter is required (YYYY-MM-DD)');
        return { success: true, data: await this.service.getAvailableDoctors(date) };
    }

    /** GET /admin/bookings/available-slots/:adminId */
    @Get('available-slots/:adminId')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly({ blockDoctors: false })
    @CheckPermission('read', 'Booking')
    async getAvailableSlots(@Param('adminId') adminId: string, @Query('date') date: string) {
        if (!date) throw new BadRequestException('date query parameter is required (YYYY-MM-DD)');
        return { success: true, data: await this.service.getAvailableSlots(adminId, date) };
    }

    /** GET /admin/bookings/:id */
    @Get(':id')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('read', 'Booking')
    async getById(@Param('id') id: string) {
        return { success: true, data: await this.service.getById(id) };
    }

    /** POST /admin/bookings */
    @Post()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly({ blockDoctors: false })
    @CheckPermission('create', 'Booking')
    async create(@Req() req: Request, @Body() body: unknown) {
        const admin = (req as any).admin;
        const parseResult = createBookingSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return {
            success: true,
            data: await this.service.create(parseResult.data, {
                id: admin.adminId,
                type: admin.isDoctor ? 'doctor' : 'admin',
                isDoctor: admin.isDoctor,
                isSuperAdmin: admin.isSuperAdmin,
            })
        };
    }

    /** PUT /admin/bookings/:id */
    @Put(':id')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('update', 'Booking')
    async update(@Param('id') id: string, @Body() body: unknown) {
        const parseResult = updateBookingSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.update(id, parseResult.data) };
    }

    /** DELETE /admin/bookings/:id — soft delete */
    @Delete(':id')
    @HttpCode(HttpStatus.OK)
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('delete', 'Booking')
    async delete(@Param('id') id: string) {
        await this.service.delete(id);
        return { success: true, message: 'Booking deleted' };
    }
}
