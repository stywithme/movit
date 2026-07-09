import { Body, Controller, Delete, ForbiddenException, Get, Param, Post, Put, Query, Req, Res, UseGuards } from '@nestjs/common';
import type { Request, Response } from 'express';
import { adminsService } from './admins.service';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { getAdminFromRequest } from '@/lib/auth/admin';

@UseGuards(CaslGuard)
@Controller('admins')
export class AdminsController {
  private canAccessSuperAdmins(req: Request) {
    return getAdminFromRequest(req)?.isSuperAdmin === true;
  }

  @Get()
  @CheckPermission('read', 'Admin')
  async list(
    @Req() req: Request,
    @Query('status') status?: string,
    @Query('search') search?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    const parsedPage = Number.parseInt(page || '1', 10);
    const parsedLimit = Number.parseInt(limit || '20', 10);
    const isActive = status === 'active' ? true : status === 'inactive' ? false : undefined;

    try {
      const result = await adminsService.list({
        isActive,
        search: search || undefined,
        page: parsedPage,
        limit: parsedLimit,
        includeSuperAdmins: this.canAccessSuperAdmins(req),
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
  async getById(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const admin = await adminsService.getById(id, this.canAccessSuperAdmins(req));
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
  async update(@Param('id') id: string, @Body() body: any, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const canAccessSuperAdmins = this.canAccessSuperAdmins(req);
      const admin =
        body?.isActive !== undefined
          ? await adminsService.setActive(id, Boolean(body.isActive), canAccessSuperAdmins)
          : await adminsService.update(id, {
            name: body?.name,
            email: body?.email,
            roleId: body?.roleId,
            isActive: body?.isActive,
          }, canAccessSuperAdmins);

      return { success: true, data: admin };
    } catch (error) {
      if (error instanceof ForbiddenException) {
        res.status(403);
        return { success: false, error: error.message };
      }
      console.error('Error updating admin:', error);
      return { success: false, error: 'Failed to update admin' };
    }
  }

  @Put(':id/password')
  @CheckPermission('update', 'Admin')
  async updatePassword(@Param('id') id: string, @Body() body: any, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.password || String(body.password).length < 6) {
        res.status(400);
        return { success: false, error: 'Password must be at least 6 characters' };
      }

      const admin = await adminsService.updatePassword(id, body.password, this.canAccessSuperAdmins(req));
      return { success: true, data: admin };
    } catch (error) {
      if (error instanceof ForbiddenException) {
        res.status(403);
        return { success: false, error: error.message };
      }
      console.error('Error updating admin password:', error);
      res.status(500);
      return { success: false, error: 'Failed to update admin password' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'Admin')
  async remove(@Param('id') id: string, @Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      await adminsService.delete(id, this.canAccessSuperAdmins(req));
      return { success: true, message: 'Admin deleted successfully' };
    } catch (error) {
      if (error instanceof ForbiddenException) {
        res.status(403);
        return { success: false, error: error.message };
      }
      console.error('Error deleting admin:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete admin' };
    }
  }
}
