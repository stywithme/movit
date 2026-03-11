import { Module } from '@nestjs/common';
import { BookingService } from './booking.service';
import { BookingController } from './booking.controller';
import { UserBookingController } from './user-booking.controller';

@Module({
    controllers: [BookingController, UserBookingController],
    providers: [BookingService],
    exports: [BookingService],
})
export class BookingModule { }
