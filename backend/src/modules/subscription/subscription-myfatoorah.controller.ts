import {
    BadRequestException,
    Body,
    Controller,
    Get,
    Headers,
    HttpStatus,
    Post,
    Query,
    Res,
} from '@nestjs/common';
import type { Response } from 'express';
import { SubscriptionService } from './subscription.service';

/**
 * Public MyFatoorah return + webhook URLs for subscription checkout.
 * Paths match {@link SubscriptionService.createCheckout} callback configuration.
 */
@Controller('payments/myfatoorah/subscriptions')
export class SubscriptionMyFatoorahController {
    constructor(private readonly subscriptionService: SubscriptionService) {}

    /**
     * Browser redirect after payment (CallBackUrl / ErrorUrl).
     * MyFatoorah may send different query key casings depending on version.
     */
    @Get('result')
    async result(
        @Query('checkoutId') checkoutId: string | undefined,
        @Query('paymentId') paymentId: string | undefined,
        @Query('PaymentId') paymentIdAlt: string | undefined,
        @Query('Id') idAlt: string | undefined,
        @Query('invoiceId') invoiceId: string | undefined,
        @Query('InvoiceId') invoiceIdAlt: string | undefined,
        @Query('failed') failed: string | undefined,
        @Res() res: Response,
    ) {
        const failedBool = failed === 'true' || failed === '1' || failed === 'True';
        try {
            const { outcome } = await this.subscriptionService.reconcileMyFatoorahResult({
                checkoutId: checkoutId || null,
                paymentId: paymentId || paymentIdAlt || null,
                invoiceId: invoiceId || invoiceIdAlt || idAlt || null,
                failed: failedBool,
            });
            let title: string;
            let body: string;
            if (outcome === 'failed') {
                title = 'Payment failed';
                body = 'You can close this window and return to the app.';
            } else if (outcome === 'paid') {
                title = 'Payment successful';
                body = 'Your subscription will update in the app shortly. You can close this window.';
            } else {
                title = 'Payment pending';
                body =
                    'We could not confirm payment yet. Return to the app and pull to refresh, or wait for confirmation.';
            }
            res.status(HttpStatus.OK);
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            return res.send(this.resultHtml(title, body));
        } catch (e) {
            const message = e instanceof Error ? e.message : 'Unable to confirm payment';
            res.status(HttpStatus.BAD_REQUEST);
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            return res.send(this.resultHtml('Payment status', message));
        }
    }

    @Post('webhook')
    async webhook(
        @Body() body: unknown,
        @Headers('myfatoorah-signature') sigLower: string | undefined,
        @Headers('Myfatoorah-Signature') sigTitle: string | undefined,
    ) {
        const signature = (sigLower || sigTitle || '').trim();
        if (!signature) {
            throw new BadRequestException('Missing webhook signature');
        }
        return this.subscriptionService.handleMyFatoorahWebhook(body, signature);
    }

    private resultHtml(title: string, message: string): string {
        const safeTitle = title.replace(/</g, '&lt;').replace(/>/g, '&gt;');
        const safeMessage = message.replace(/</g, '&lt;').replace(/>/g, '&gt;');
        return `<!DOCTYPE html><html><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/><title>${safeTitle}</title></head><body style="font-family:system-ui,sans-serif;padding:24px;background:#0f172a;color:#e2e8f0;"><h1 style="font-size:1.25rem;">${safeTitle}</h1><p style="opacity:.9;">${safeMessage}</p></body></html>`;
    }
}
