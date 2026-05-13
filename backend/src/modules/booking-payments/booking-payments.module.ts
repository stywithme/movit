import { Module } from '@nestjs/common';
import { BookingPaymentService } from './booking-payment.service';
import { BookingPaymentController } from './booking-payment.controller';
import { MyFatoorahWebhookController } from './myfatoorah-webhook.controller';
import { PaymentResultController } from './payment-result.controller';

@Module({
  controllers: [BookingPaymentController, MyFatoorahWebhookController, PaymentResultController],
  providers: [BookingPaymentService],
  exports: [BookingPaymentService],
})
export class BookingPaymentsModule {}
