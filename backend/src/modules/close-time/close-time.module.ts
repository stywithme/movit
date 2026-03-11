import { Module } from '@nestjs/common';
import { CloseTimeService } from './close-time.service';
import { CloseTimeController } from './close-time.controller';

@Module({
    controllers: [CloseTimeController],
    providers: [CloseTimeService],
    exports: [CloseTimeService],
})
export class CloseTimeModule { }
