import { Controller, Get, Post, Put, Delete, Param, Body, UseGuards } from '@nestjs/common';
import { PermissionsService } from './permissions.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { z } from 'zod';

const createRoleSchema = z.object({
    name: z.string().min(2),
    displayName: z.any(),
    description: z.any().optional(),
    permissionIds: z.array(z.string()).optional(),
    assignedAdminIds: z.array(z.string()).optional(),
});

const updateRoleSchema = z.object({
    name: z.string().min(2).optional(),
    displayName: z.any().optional(),
    description: z.any().optional(),
    permissionIds: z.array(z.string()).optional(),
    assignedAdminIds: z.array(z.string()).optional(),
});

@UseGuards(CaslGuard)
@Controller('admin/permissions')
export class PermissionsController {
    constructor(private readonly permissionsService: PermissionsService) { }

    @Get()
    @CheckPermission('read', 'Admin') // Requires admin read access to view permissions list
    async getAllPermissions() {
        const data = await this.permissionsService.getAllPermissions();
        return { success: true, data };
    }

    @Get('roles')
    @CheckPermission('read', 'Admin')
    async getRoles() {
        const data = await this.permissionsService.getRoles();
        return { success: true, data };
    }

    @Get('roles/:id')
    @CheckPermission('read', 'Admin')
    async getRole(@Param('id') id: string) {
        const data = await this.permissionsService.getRole(id);
        return { success: true, data };
    }

    @Post('roles')
    @CheckPermission('create', 'Admin')
    async createRole(@Body() body: unknown) {
        const result = createRoleSchema.safeParse(body);
        if (!result.success) {
            return { success: false, error: 'Validation failed', details: result.error.flatten() };
        }
        const data = await this.permissionsService.createRole(result.data);
        return { success: true, data };
    }

    @Put('roles/:id')
    @CheckPermission('update', 'Admin')
    async updateRole(@Param('id') id: string, @Body() body: unknown) {
        const result = updateRoleSchema.safeParse(body);
        if (!result.success) {
            return { success: false, error: 'Validation failed', details: result.error.flatten() };
        }
        const data = await this.permissionsService.updateRole(id, result.data);
        return { success: true, data };
    }

    @Delete('roles/:id')
    @CheckPermission('delete', 'Admin')
    async deleteRole(@Param('id') id: string) {
        await this.permissionsService.deleteRole(id);
        return { success: true };
    }
}
