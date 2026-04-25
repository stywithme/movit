import { Module } from '@nestjs/common';
import { SubscriptionService } from './subscription.service';
import { SubscriptionController } from './subscription.controller';
import { MobileSubscriptionController } from './mobile-subscription.controller';
import { SubscriptionMyFatoorahController } from './subscription-myfatoorah.controller';

@Module({
    controllers: [
        SubscriptionController,
        MobileSubscriptionController,
        SubscriptionMyFatoorahController,
    ],
    providers: [SubscriptionService],
    exports: [SubscriptionService],
})
export class SubscriptionModule { }
