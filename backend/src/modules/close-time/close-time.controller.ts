import {
    Controller, Get, Post, Put, Delete,
    Param, Body, UseGuards, Req,
    HttpCode, HttpStatus, BadRequestException,
} from '@nestjs/common';
import { CloseTimeService } from './close-time.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { DoctorGuard, DoctorOnly } from '@/lib/guards/doctor.guard';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { createCloseTimeSchema, updateCloseTimeSchema } from './close-time.types';
import type { Request } from 'express';

@Controller('admin/close-time')
export class CloseTimeController {
    constructor(private readonly service: CloseTimeService) { }

    /** GET /admin/close-time/mine — Doctor: view own + global close times */
    @Get('mine')
    @UseGuards(DoctorGuard)
    @DoctorOnly()
    async getMine(@Req() req: Request) {
        const admin = (req as any).admin;
        return { success: true, data: await this.service.getByAdmin(admin.adminId) };
    }

    /** GET /admin/close-time */
    @Get()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('read', 'CloseTime')
    async getAll() {
        return { success: true, data: await this.service.getAll() };
    }

    /** POST /admin/close-time */
    @Post()
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('create', 'CloseTime')
    async create(@Body() body: unknown) {
        const parseResult = createCloseTimeSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.create(parseResult.data) };
    }

    /** PUT /admin/close-time/:id */
    @Put(':id')
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('update', 'CloseTime')
    async update(@Param('id') id: string, @Body() body: unknown) {
        const parseResult = updateCloseTimeSchema.safeParse(body);
        if (!parseResult.success) {
            throw new BadRequestException({ message: 'Validation failed', errors: parseResult.error.flatten().fieldErrors });
        }
        return { success: true, data: await this.service.update(id, parseResult.data) };
    }

    /** DELETE /admin/close-time/:id */
    @Delete(':id')
    @HttpCode(HttpStatus.OK)
    @UseGuards(CaslGuard, AdminGuard)
    @AdminOnly()
    @CheckPermission('delete', 'CloseTime')
    async delete(@Param('id') id: string) {
        await this.service.delete(id);
        return { success: true, message: 'Close time deleted' };
    }
}
