import { Controller, Get, Query, Res } from '@nestjs/common';
import { BookingPaymentService } from './booking-payment.service';
import type { Response } from 'express';

/**
 * Handles redirect from MyFatoorah after payment.
 * Serves a simple HTML result page; app can deep-link back using checkoutId.
 */
@Controller('payments/myfatoorah')
export class PaymentResultController {
  constructor(private readonly service: BookingPaymentService) {}

  /**
   * GET /payments/myfatoorah/result?paymentId=xxx
   * MyFatoorah may redirect with paymentId / PaymentId / Id depending on flow.
   * We accept all supported variants, then verify via GET Payment Details,
   * then show result. Never trust redirect params alone.
   */
  @Get('result')
  async getResult(
    @Query() query: Record<string, string | undefined>,
    @Res() res: Response,
  ) {
    const paymentId = query.paymentId || query.PaymentId || query.Id;
    if (!paymentId) {
      res
        .status(400)
        .send(
          '<html><body><h1>Payment Error</h1><p>Missing PaymentId</p></body></html>',
        );
      return;
    }

    const { status, checkoutId } =
      await this.service.getResultStatus(paymentId, query.checkoutId);

    const appScheme = process.env.MOBILE_APP_SCHEME || 'poseapp';
    const deepLink = checkoutId
      ? `${appScheme}://payment/result?checkoutId=${checkoutId}&status=${status}`
      : `${appScheme}://payment/result?status=${status}`;
    const visualStatus =
      status === 'paid'
        ? 'paid'
        : ['released', 'superseded'].includes(status)
          ? 'released'
          : ['failed', 'expired', 'canceled'].includes(status)
            ? 'failed'
            : 'pending';
    const title =
      status === 'paid'
        ? 'Payment Complete'
        : visualStatus === 'released'
          ? 'Payment Session Updated'
          : visualStatus === 'failed'
            ? 'Payment Failed'
            : 'Payment Processing';
    const description =
      status === 'released'
        ? 'This payment link was replaced by a newer checkout selection.'
        : status === 'superseded'
          ? 'This checkout is no longer the active one for your bookings.'
          : `Status: ${status}`;

    const html = `
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Payment Result</title>
  <style>
    body { font-family: system-ui, sans-serif; padding: 2rem; text-align: center; }
    .status { font-size: 1.2rem; margin: 1rem 0; }
    .paid { color: #22c55e; }
    .pending { color: #f59e0b; }
    .failed { color: #ef4444; }
    .released { color: #2563eb; }
    a { display: inline-block; margin-top: 1rem; padding: 0.75rem 1.5rem; background: #3b82f6; color: white; text-decoration: none; border-radius: 0.5rem; }
  </style>
</head>
<body>
  <h1>${title}</h1>
  <p class="status ${visualStatus}">${description}</p>
  <a href="${deepLink}">Return to App</a>
</body>
</html>`;

    res.type('text/html').send(html);
  }
}
