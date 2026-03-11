import { Controller, Get, Put, Body, Param, UseGuards, BadRequestException } from '@nestjs/common';
import { SystemService } from './system.service';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@Controller('admin/system')
@UseGuards(CaslGuard, AdminGuard)
@AdminOnly()
export class SystemController {
    constructor(private readonly service: SystemService) { }

    @Get()
    @CheckPermission('read', 'System') // Specific permission for system settings
    async getAll() {
        return { success: true, data: await this.service.getAll() };
    }

    @Put(':key')
    @CheckPermission('update', 'System')
    async update(@Param('key') key: string, @Body('value') value: string) {
        if (value === undefined) throw new BadRequestException('Value is required');
        return { success: true, data: await this.service.update(key, value) };
    }

    @Put()
    @CheckPermission('update', 'System')
    async updateMany(@Body() body: Record<string, string>) {
        return { success: true, data: await this.service.updateMany(body) };
    }
}
