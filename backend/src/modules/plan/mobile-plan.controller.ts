import { Controller, Get, Query } from '@nestjs/common';
import { PlanService } from './plan.service';

@Controller('mobile/plans')
export class MobilePlanController {
    constructor(private readonly planService: PlanService) { }

    @Get()
    async findAll() {
        // Return only active plans for mobile users
        const result = await this.planService.findAll({ isActive: 'true', limit: 100 });
        return {
            success: true,
            data: result.data,
        };
    }
}
