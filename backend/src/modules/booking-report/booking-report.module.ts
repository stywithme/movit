import { Module } from '@nestjs/common';
import { BookingReportService } from './booking-report.service';
import { BookingReportController, UserBookingReportController } from './booking-report.controller';

@Module({
    controllers: [BookingReportController, UserBookingReportController],
    providers: [BookingReportService],
    exports: [BookingReportService],
})
export class BookingReportModule { }
