import { Controller, Get, Post, Body, Patch, Param, Delete, Query, UseGuards } from '@nestjs/common';
import { SubscriptionService } from './subscription.service';
import { CreateSubscriptionDto } from './dto/create-subscription.dto';
import { UpdateSubscriptionDto } from './dto/update-subscription.dto';
import { AdminGuard, AdminOnly } from '@/lib/guards/admin.guard';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@Controller('admin/subscriptions')
@UseGuards(CaslGuard, AdminGuard)
@AdminOnly()
export class SubscriptionController {
    constructor(private readonly subscriptionService: SubscriptionService) { }

    @Post()
    @CheckPermission('create', 'Subscription')
    async create(@Body() createSubscriptionDto: CreateSubscriptionDto) {
        return {
            success: true,
            data: await this.subscriptionService.create(createSubscriptionDto),
        };
    }

    @Get()
    @CheckPermission('read', 'Subscription')
    async findAll(@Query() query: any) {
        return {
            success: true,
            ...await this.subscriptionService.findAll(query),
        };
    }

    @Get(':id')
    @CheckPermission('read', 'Subscription')
    async findOne(@Param('id') id: string) {
        return {
            success: true,
            data: await this.subscriptionService.findOne(id),
        };
    }

    @Patch(':id')
    @CheckPermission('update', 'Subscription')
    async update(@Param('id') id: string, @Body() updateSubscriptionDto: UpdateSubscriptionDto) {
        return {
            success: true,
            data: await this.subscriptionService.update(id, updateSubscriptionDto),
        };
    }

    @Delete(':id')
    @CheckPermission('delete', 'Subscription')
    async remove(@Param('id') id: string) {
        return {
            success: true,
            data: await this.subscriptionService.remove(id),
        };
    }
}
