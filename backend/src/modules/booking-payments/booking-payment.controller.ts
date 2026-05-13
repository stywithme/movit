import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { BookingPaymentService } from './booking-payment.service';
import { MobileAuthGuard, MobileAuth } from '@/lib/guards/mobile-auth.guard';
import { UserPermissionGuard } from '@/lib/guards/user-permission.guard';
import { createCheckoutSchema } from './booking-payment.types';
import type { Request } from 'express';

@Controller('bookings/payments')
@UseGuards(MobileAuthGuard, UserPermissionGuard)
@MobileAuth()
export class BookingPaymentController {
  constructor(private readonly service: BookingPaymentService) {}

  @Post('checkout')
  async createCheckout(@Req() req: Request, @Body() body: unknown) {
    const userId = (req as any).userId;
    const parseResult = createCheckoutSchema.safeParse(body);
    if (!parseResult.success) {
      throw new BadRequestException({
        message: 'Validation failed',
        errors: parseResult.error.flatten().fieldErrors,
      });
    }
    return {
      success: true,
      data: await this.service.createCheckout(userId, parseResult.data),
    };
  }

  @Get(':checkoutId')
  async getCheckoutStatus(@Param('checkoutId') checkoutId: string, @Req() req: Request) {
    const userId = (req as any).userId;
    return {
      success: true,
      data: await this.service.getCheckoutStatus(checkoutId, userId),
    };
  }
}
