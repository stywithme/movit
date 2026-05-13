import { Module } from '@nestjs/common';
import { BookingService } from './booking.service';
import { BookingController } from './booking.controller';
import { UserBookingController } from './user-booking.controller';
import { GoogleMeetModule } from '@/modules/google-meet/google-meet.module';

@Module({
    imports: [GoogleMeetModule],
    controllers: [BookingController, UserBookingController],
    providers: [BookingService],
    exports: [BookingService],
})
export class BookingModule { }
