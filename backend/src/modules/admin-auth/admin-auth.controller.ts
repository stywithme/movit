import { Body, Controller, Get, Post, Put, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { adminAuthService } from './admin-auth.service';
import {
  adminLoginSchema,
  adminRequestResetSchema,
  adminResetPasswordSchema,
  adminUpdateProfileSchema,
} from './admin-auth.types';
import {
  clearAdminAuthCookie,
  getAdminIdFromRequest,
  setAdminAuthCookie,
  signAdminToken,
} from '@/lib/auth/admin';

@Controller('admin/auth')
export class AdminAuthController {
  @Post('login')
  async login(@Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = adminLoginSchema.safeParse(body);
    if (!parseResult.success) {
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      const admin = await adminAuthService.login(parseResult.data);
      const token = signAdminToken({
        adminId: admin.id,
        email: admin.email,
        roleId: admin.roleId,
        isSuperAdmin: admin.isSuperAdmin,
      });
      setAdminAuthCookie(res, token);

      return {
        success: true,
        data: {
          ...admin,
          token
        }
      };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Login failed';
      if (message === 'Invalid email or password' || message === 'Account is deactivated') {
        res.status(401);
        return { success: false, error: message };
      }
      res.status(500);
      return { success: false, error: message };
    }
  }

  @Post('logout')
  async logout(@Res({ passthrough: true }) res: Response) {
    clearAdminAuthCookie(res);
    return { success: true };
  }

  @Get('profile')
  async profile(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    const adminId = getAdminIdFromRequest(req);
    if (!adminId) {
      res.status(401);
      return { success: false, error: 'Unauthorized' };
    }

    const admin = await adminAuthService.getProfile(adminId);
    if (!admin) {
      res.status(404);
      return { success: false, error: 'Admin not found' };
    }

    return { success: true, data: admin };
  }

  @Put('profile')
  async updateProfile(
    @Req() req: Request,
    @Body() body: unknown,
    @Res({ passthrough: true }) res: Response
  ) {
    const adminId = getAdminIdFromRequest(req);
    if (!adminId) {
      res.status(401);
      return { success: false, error: 'Unauthorized' };
    }

    const parseResult = adminUpdateProfileSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    const admin = await adminAuthService.updateProfile(adminId, parseResult.data);
    return { success: true, data: admin };
  }

  @Post('request-reset')
  async requestReset(@Body() body: unknown) {
    const parseResult = adminRequestResetSchema.safeParse(body);
    if (!parseResult.success) {
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    await adminAuthService.requestReset(parseResult.data.email);
    return { success: true };
  }

  @Post('reset-password')
  async resetPassword(@Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = adminResetPasswordSchema.safeParse(body);
    if (!parseResult.success) {
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      await adminAuthService.resetPassword(
        parseResult.data.token,
        parseResult.data.password
      );
      clearAdminAuthCookie(res);
      return { success: true };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Reset failed';
      res.status(400);
      return { success: false, error: message };
    }
  }
}
