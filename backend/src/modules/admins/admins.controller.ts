import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { adminsService } from './admins.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@UseGuards(CaslGuard)
@Controller('admins')
export class AdminsController {
  @Get()
  @CheckPermission('read', 'Admin')
  async list(
    @Query('status') status?: string,
    @Query('search') search?: string,
    @Query('isDoctor') isDoctor?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    const parsedPage = Number.parseInt(page || '1', 10);
    const parsedLimit = Number.parseInt(limit || '20', 10);
    const isActive = status === 'active' ? true : status === 'inactive' ? false : undefined;

    try {
      const result = await adminsService.list({
        isActive,
        isDoctor: isDoctor ? isDoctor === 'true' : undefined,
        search: search || undefined,
        page: parsedPage,
        limit: parsedLimit,
      });

      return {
        success: true,
        data: result.admins,
        pagination: result.pagination,
      };
    } catch (error) {
      console.error('Error fetching admins:', error);
      return { success: false, error: 'Failed to fetch admins' };
    }
  }

  @Post()
  @CheckPermission('create', 'Admin')
  async create(@Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.name || !body?.email || !body?.password) {
        res.status(400);
        return { success: false, error: 'Name, email, and password are required' };
      }

      if (String(body.password).length < 6) {
        res.status(400);
        return { success: false, error: 'Password must be at least 6 characters' };
      }

      const admin = await adminsService.create({
        name: body.name,
        email: body.email,
        password: body.password,
        roleId: body.roleId || null,
        isDoctor: body.isDoctor,
      });

      res.status(201);
      return { success: true, data: admin };
    } catch (error: any) {
      console.error('Error creating admin:', error);
      if (error?.message === 'Email already registered') {
        res.status(409);
        return { success: false, error: 'Email already registered' };
      }
      res.status(500);
      return { success: false, error: 'Failed to create admin' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'Admin')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const admin = await adminsService.getById(id);
      if (!admin) {
        res.status(404);
        return { success: false, error: 'Admin not found' };
      }
      return { success: true, data: admin };
    } catch (error) {
      console.error('Error fetching admin:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch admin' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'Admin')
  async update(@Param('id') id: string, @Body() body: any) {
    try {
      const admin =
        body?.isActive !== undefined
          ? await adminsService.setActive(id, Boolean(body.isActive))
          : await adminsService.update(id, {
            name: body?.name,
            email: body?.email,
            roleId: body?.roleId,
            isActive: body?.isActive,
            isDoctor: body?.isDoctor,
          });

      return { success: true, data: admin };
    } catch (error) {
      console.error('Error updating admin:', error);
      return { success: false, error: 'Failed to update admin' };
    }
  }

  @Put(':id/password')
  @CheckPermission('update', 'Admin')
  async updatePassword(@Param('id') id: string, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.password || String(body.password).length < 6) {
        res.status(400);
        return { success: false, error: 'Password must be at least 6 characters' };
      }

      const admin = await adminsService.updatePassword(id, body.password);
      return { success: true, data: admin };
    } catch (error) {
      console.error('Error updating admin password:', error);
      res.status(500);
      return { success: false, error: 'Failed to update admin password' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'Admin')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await adminsService.delete(id);
      return { success: true, message: 'Admin deleted successfully' };
    } catch (error) {
      console.error('Error deleting admin:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete admin' };
    }
  }
}
