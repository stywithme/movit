import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { usersService } from './users.service';

@Controller('users')
export class UsersController {
  @Get()
  async list(
    @Query('status') status?: string,
    @Query('search') search?: string,
    @Query('page') page?: string,
    @Query('limit') limit?: string
  ) {
    try {
      const parsedPage = Number.parseInt(page || '1', 10);
      const parsedLimit = Number.parseInt(limit || '20', 10);
      const isActive = status === 'active' ? true : status === 'inactive' ? false : undefined;

      const result = await usersService.list({
        isActive,
        search: search || undefined,
        page: parsedPage,
        limit: parsedLimit,
      });

      return {
        success: true,
        data: result.users,
        pagination: result.pagination,
      };
    } catch (error) {
      console.error('Error fetching users:', error);
      return { success: false, error: 'Failed to fetch users' };
    }
  }

  @Post()
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

      const subscriptionExpiry = body.subscriptionExpiry
        ? new Date(`${body.subscriptionExpiry}T00:00:00.000Z`)
        : undefined;

      const user = await usersService.create({
        name: body.name,
        email: body.email,
        password: body.password,
        avatarUrl: body.avatarUrl || undefined,
        isPro: body.isPro ?? false,
        subscriptionExpiry,
      });

      res.status(201);
      return { success: true, data: user };
    } catch (error: any) {
      console.error('Error creating user:', error);
      if (error?.message === 'Email already registered') {
        res.status(409);
        return { success: false, error: 'Email already registered' };
      }
      res.status(500);
      return { success: false, error: 'Failed to create user' };
    }
  }

  @Put(':id')
  async update(@Param('id') id: string, @Body() body: any) {
    try {
      const subscriptionExpiry =
        body.subscriptionExpiry === null
          ? null
          : body.subscriptionExpiry
            ? new Date(`${body.subscriptionExpiry}T00:00:00.000Z`)
            : undefined;

      const user =
        body?.isActive !== undefined
          ? await usersService.setActive(id, Boolean(body.isActive))
          : await usersService.update(id, {
              name: body?.name,
              email: body?.email,
              avatarUrl: body?.avatarUrl,
              isPro: body?.isPro,
              subscriptionExpiry,
              isActive: body?.isActive,
            });

      return { success: true, data: user };
    } catch (error) {
      console.error('Error updating user:', error);
      return { success: false, error: 'Failed to update user' };
    }
  }

  @Delete(':id')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await usersService.delete(id);
      return { success: true, message: 'User deleted successfully' };
    } catch (error) {
      console.error('Error deleting user:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete user' };
    }
  }
}
