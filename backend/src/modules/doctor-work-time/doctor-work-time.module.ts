import { Module } from '@nestjs/common';
import { DoctorWorkTimeService } from './doctor-work-time.service';
import { DoctorWorkTimeController } from './doctor-work-time.controller';

@Module({
    controllers: [DoctorWorkTimeController],
    providers: [DoctorWorkTimeService],
    exports: [DoctorWorkTimeService],
})
export class DoctorWorkTimeModule { }
