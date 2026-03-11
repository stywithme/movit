import { Module } from '@nestjs/common';
import { PlanService } from './plan.service';
import { PlanController } from './plan.controller';
import { MobilePlanController } from './mobile-plan.controller';

@Module({
    controllers: [PlanController, MobilePlanController],
    providers: [PlanService],
    exports: [PlanService],
})
export class PlanModule { }
