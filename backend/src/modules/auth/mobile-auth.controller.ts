import {
  Body,
  Controller,
  Delete,
  Get,
  Patch,
  Post,
  Req,
  Res,
  UseGuards,
} from '@nestjs/common';
import { Throttle, ThrottlerGuard } from '@nestjs/throttler';
import type { Request, Response } from 'express';
import { authService } from './auth.service';
import {
  changePasswordSchema,
  forgotPasswordSchema,
  googleAuthSchema,
  loginSchema,
  refreshTokenSchema,
  registerSchema,
  resetPasswordSchema,
  updateProfileSchema,
  updateSettingsSchema,
} from './auth.types';
import { getDeviceInfo } from '@/lib/auth/middleware';
import { MobileAuthGuard } from '@/lib/guards/mobile-auth.guard';
import { MobileAuth } from '@/lib/guards/mobile-auth.guard';
import { UserPermissionGuard } from '@/lib/guards/user-permission.guard';

@Controller('mobile/auth')
@UseGuards(ThrottlerGuard)
export class MobileAuthController {

  // ─── Public Routes (no guard) ──────────────────────────────────────────────

  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Post('register')
  async register(@Req() req: Request, @Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = registerSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      const deviceInfo = getDeviceInfo(req);
      const result = await authService.register(parseResult.data, deviceInfo);
      res.status(201);
      return { success: true, data: result };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Registration failed';
      if (message === 'Email already registered') {
        res.status(409);
        return { success: false, error: message };
      }
      res.status(500);
      return { success: false, error: message };
    }
  }

  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Post('login')
  async login(@Req() req: Request, @Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = loginSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      const deviceInfo = getDeviceInfo(req);
      const result = await authService.login(parseResult.data, deviceInfo);
      return { success: true, data: result };
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

  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  @Post('google')
  async googleAuth(@Req() req: Request, @Body() body: any, @Res({ passthrough: true }) res: Response) {
    const parseResult = googleAuthSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      const { idToken } = parseResult.data;
      const { googleId, email, name, avatarUrl } = body;
      const deviceInfo = getDeviceInfo(req);
      const result = await authService.googleAuth(
        idToken,
        googleId,
        email,
        name,
        avatarUrl,
        deviceInfo
      );

      return { success: true, data: result };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Google auth failed';
      if (
        message === 'Account is deactivated' ||
        message === 'Invalid Google token' ||
        message === 'Google ID token is required' ||
        message === 'Missing Google user data'
      ) {
        res.status(401);
        return { success: false, error: message };
      }
      res.status(500);
      return { success: false, error: message };
    }
  }

  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @Post('refresh')
  async refresh(@Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = refreshTokenSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      const tokens = await authService.refreshAccessToken(parseResult.data.refreshToken);
      return { success: true, data: tokens };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Token refresh failed';
      if (message.includes('token') || message === 'Account is deactivated') {
        res.status(401);
        return { success: false, error: message };
      }
      res.status(500);
      return { success: false, error: message };
    }
  }

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('forgot-password')
  async forgotPassword(@Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = forgotPasswordSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      await authService.forgotPassword(parseResult.data.email);
      return { success: true, message: 'Reset email sent if account exists' };
    } catch (error) {
      console.error('Forgot password error:', error);
      res.status(500);
      return { success: false, error: 'Failed to request password reset' };
    }
  }

  @Throttle({ default: { limit: 5, ttl: 60_000 } })
  @Post('reset-password')
  async resetPassword(@Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = resetPasswordSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      await authService.resetPassword(parseResult.data.token, parseResult.data.password);
      return { success: true, message: 'Password reset successfully' };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Reset failed';
      res.status(400);
      return { success: false, error: message };
    }
  }

  // ─── Protected Routes (require auth + user permission) ────────────────────

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Post('logout')
  async logout(@Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    const parseResult = refreshTokenSchema.safeParse(body);
    if (!parseResult.success) {
      res.status(400);
      return {
        success: false,
        error: 'Validation failed',
        details: parseResult.error.flatten().fieldErrors,
      };
    }

    try {
      await authService.logout(parseResult.data.refreshToken);
      return { success: true, message: 'Logged out successfully' };
    } catch (error) {
      console.error('Logout error:', error);
      res.status(500);
      return { success: false, error: 'Logout failed' };
    }
  }

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Delete('logout')
  async logoutAll(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const userId = (req as any).userId;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }
      await authService.logoutAll(userId);
      return { success: true, message: 'Logged out from all devices' };
    } catch (error) {
      console.error('Logout all error:', error);
      res.status(500);
      return { success: false, error: 'Logout failed' };
    }
  }

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Get('profile')
  async profile(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const userId = (req as any).userId;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const user = await authService.getProfile(userId);
      if (!user) {
        res.status(404);
        return { success: false, error: 'User not found' };
      }

      // Attach permission info to the response
      const userPermission = (req as any).userPermission;
      return {
        success: true,
        data: {
          ...user,
          subscription: {
            isPro: userPermission?.isPro ?? false,
            isFree: userPermission?.isFree ?? true,
            subscriptionExpiry: userPermission?.subscriptionExpiry ?? null,
          },
        },
      };
    } catch (error) {
      console.error('Get profile error:', error);
      res.status(500);
      return { success: false, error: 'Failed to get profile' };
    }
  }

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Patch('profile')
  async updateProfile(@Req() req: Request, @Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    try {
      const userId = (req as any).userId;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const parseResult = updateProfileSchema.safeParse(body);
      if (!parseResult.success) {
        res.status(400);
        return {
          success: false,
          error: 'Validation failed',
          details: parseResult.error.flatten().fieldErrors,
        };
      }

      const user = await authService.updateProfile(userId, parseResult.data);
      return { success: true, data: user };
    } catch (error) {
      console.error('Update profile error:', error);
      res.status(500);
      return { success: false, error: 'Failed to update profile' };
    }
  }

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Patch('settings')
  async updateSettings(@Req() req: Request, @Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    try {
      const userId = (req as any).userId;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const parseResult = updateSettingsSchema.safeParse(body);
      if (!parseResult.success) {
        res.status(400);
        return {
          success: false,
          error: 'Validation failed',
          details: parseResult.error.flatten().fieldErrors,
        };
      }

      const user = await authService.updateSettings(userId, parseResult.data);
      return { success: true, data: user };
    } catch (error) {
      console.error('Update settings error:', error);
      res.status(500);
      return { success: false, error: 'Failed to update settings' };
    }
  }

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Post('change-password')
  async changePassword(@Req() req: Request, @Body() body: unknown, @Res({ passthrough: true }) res: Response) {
    try {
      const userId = (req as any).userId;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      const parseResult = changePasswordSchema.safeParse(body);
      if (!parseResult.success) {
        res.status(400);
        return {
          success: false,
          error: 'Validation failed',
          details: parseResult.error.flatten().fieldErrors,
        };
      }

      await authService.changePassword(userId, parseResult.data);
      return { success: true, message: 'Password updated successfully' };
    } catch (error) {
      console.error('Change password error:', error);
      res.status(500);
      return { success: false, error: 'Failed to change password' };
    }
  }

  @UseGuards(MobileAuthGuard, UserPermissionGuard)
  @MobileAuth()
  @Delete('account')
  async deleteAccount(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
    try {
      const userId = (req as any).userId;
      if (!userId) {
        res.status(401);
        return { success: false, error: 'Unauthorized' };
      }

      await authService.deleteAccount(userId);
      return { success: true, message: 'Account deleted' };
    } catch (error) {
      console.error('Delete account error:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete account' };
    }
  }
}
