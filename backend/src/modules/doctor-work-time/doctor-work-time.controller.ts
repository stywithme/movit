import {
    Controller,
    Get,
    Post,
    Put,
    Delete,
    Param,
    Body,
    UseGuards,
    Req,
    HttpCode,
    HttpStatus,
    BadRequestException,
} from '@nestjs/common';
import { DoctorWorkTimeService } from './doctor-work-time.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { DoctorGuard, DoctorOnly } from '@/lib/guards/doctor.guard';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { createDoctorWorkTimeSchema, updateDoctorWorkTimeSchema, createBulkDoctorWorkTimeSchema } from './doctor-work-time.types';
import type { Request } from 'express';

@Controller('admin/doctor-work-time')
export class DoctorWorkTimeController {
    constructor(private readonly service: DoctorWorkTimeService) { }

    /**
     * GET /admin/doctor-work-time/mine (Doctor Only)
     */
    @Get('mine')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async getMine(@Req() req: Request) {
        const admin = (req as any).admin;
        return { success: true, data: await this.service.getByAdmin(admin.adminId) };
    }

    /**
     * GET /admin/doctor-work-time (Full list)
     */
    @Get()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('read', 'DoctorWorkTime')
    async getAll() {
        return { success: true, data: await this.service.getAll() };
    }

    /**
     * GET /admin/doctor-work-time/:adminId (Specific Doctor)
     */
    @Get(':adminId')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('read', 'DoctorWorkTime')
    async getByAdmin(@Param('adminId') adminId: string) {
        return { success: true, data: await this.service.getByAdmin(adminId) };
    }

    /**
     * POST /admin/doctor-work-time/bulk (Set exact schedule for a doctor)
     */
    @Post('bulk')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('create', 'DoctorWorkTime')
    async setDoctorWorkTimes(@Body() body: unknown) {
        const parseResult = createBulkDoctorWorkTimeSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({
                message: 'Validation failed',
                errors: parseResult.error.flatten().fieldErrors,
            });
        }

        return { success: true, data: await this.service.setDoctorWorkTimes(parseResult.data) };
    }

    /**
     * POST /admin/doctor-work-time (Create)
     */
    @Post()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('create', 'DoctorWorkTime')
    async create(@Body() body: unknown) {
        const parseResult = createDoctorWorkTimeSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({
                message: 'Validation failed',
                errors: parseResult.error.flatten().fieldErrors,
            });
        }

        return { success: true, data: await this.service.create(parseResult.data) };
    }

    /**
     * PUT /admin/doctor-work-time/:id (Update)
     */
    @Put(':id')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('update', 'DoctorWorkTime')
    async update(@Param('id') id: string, @Body() body: unknown) {
        const parseResult = updateDoctorWorkTimeSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({
                message: 'Validation failed',
                errors: parseResult.error.flatten().fieldErrors,
            });
        }

        return { success: true, data: await this.service.update(id, parseResult.data) };
    }

    /**
     * DELETE /admin/doctor-work-time/:id (Delete)
     */
    @Delete(':id')
    @HttpCode(HttpStatus.OK)
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('delete', 'DoctorWorkTime')
    async delete(@Param('id') id: string) {
        await this.service.delete(id);
        return { success: true, message: 'Work time deleted' };
    }
}
