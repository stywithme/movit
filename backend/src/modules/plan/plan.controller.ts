import { Controller, Get, Post, Body, Patch, Param, Delete, Query, UseGuards } from '@nestjs/common';
import { PlanService } from './plan.service';
import { CreatePlanDto } from './dto/create-plan.dto';
import { UpdatePlanDto } from './dto/update-plan.dto';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@Controller('admin/plans')
@UseGuards(CaslGuard, AdminGuard)
@AdminOnly()
export class PlanController {
    constructor(private readonly planService: PlanService) { }

    @Post()
    @CheckPermission('create', 'Plan')
    async create(@Body() createPlanDto: CreatePlanDto) {
        return {
            success: true,
            data: await this.planService.create(createPlanDto),
        };
    }

    @Get()
    @CheckPermission('read', 'Plan')
    async findAll(@Query() query: any) {
        return {
            success: true,
            ...await this.planService.findAll(query),
        };
    }

    @Get(':id')
    @CheckPermission('read', 'Plan')
    async findOne(@Param('id') id: string) {
        return {
            success: true,
            data: await this.planService.findOne(id),
        };
    }

    @Patch(':id')
    @CheckPermission('update', 'Plan')
    async update(@Param('id') id: string, @Body() updatePlanDto: UpdatePlanDto) {
        return {
            success: true,
            data: await this.planService.update(id, updatePlanDto),
        };
    }

    @Delete(':id')
    @CheckPermission('delete', 'Plan')
    async remove(@Param('id') id: string) {
        return {
            success: true,
            data: await this.planService.remove(id),
        };
    }
}
