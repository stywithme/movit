import {
  BadRequestException,
  Body,
  Controller,
  Headers,
  Logger,
  Post,
  Req,
} from '@nestjs/common';
import { BookingPaymentService } from './booking-payment.service';
import type { Request } from 'express';

function pickMyFatoorahSignature(req: Request): string {
  const headers = req.headers;
  const direct =
    (headers['myfatoorah-signature'] as string | undefined) ||
    (headers['Myfatoorah-Signature'] as string | undefined);
  if (direct) return String(direct).trim();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === 'myfatoorah-signature' && value) {
      return Array.isArray(value) ? value[0].trim() : String(value).trim();
    }
  }
  return '';
}

@Controller('payments/myfatoorah')
export class MyFatoorahWebhookController {
  private readonly logger = new Logger(MyFatoorahWebhookController.name);

  constructor(private readonly service: BookingPaymentService) {}

  @Post('webhook')
  async handleWebhook(
    @Req() req: Request,
    @Body() body: unknown,
    @Headers('myfatoorah-signature') sigLower: string | undefined,
    @Headers('Myfatoorah-Signature') sigTitle: string | undefined,
  ) {
    const signature = pickMyFatoorahSignature(req) || sigLower?.trim() || sigTitle?.trim() || '';
    if (!signature) {
      this.logger.warn('MyFatoorah webhook received without signature (Local/sandbox may omit it)');
    }
    if (!body || typeof body !== 'object') {
      throw new BadRequestException('Invalid webhook payload');
    }
    await this.service.handleWebhook(body, signature);
    return { success: true };
  }
}
