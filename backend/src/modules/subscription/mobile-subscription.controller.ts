import { Controller, Get, Post, Body, Req, Res, UseGuards } from '@nestjs/common';
import { SubscriptionService } from './subscription.service';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import type { Request, Response } from 'express';

@Controller('mobile/subscriptions')
export class MobileSubscriptionController {
    constructor(private readonly subscriptionService: SubscriptionService) { }

    @Get('mine')
    async findMine(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }

            // Reuse findAll with filter for the current user
            const result = await this.subscriptionService.findAll({ search: authResult.userId, limit: 100 });

            // Filter precisely by userId to be safe
            const mySubscriptions = result.data.filter(s => s.userId === authResult.userId);

            return {
                success: true,
                data: mySubscriptions,
            };
        } catch (error) {
            console.error('[Subscription] Error fetching mine:', error);
            res.status(500);
            return { success: false, error: 'Failed to fetch subscriptions' };
        }
    }

    @Post()
    async subscribe(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
        @Body() body: { planId: string, amountPaid: number, type?: string }
    ) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }

            if (!body.planId) {
                res.status(400);
                return { success: false, error: 'planId is required' };
            }

            const subscription = await this.subscriptionService.create({
                userId: authResult.userId,
                planId: body.planId,
                amountPaid: body.amountPaid || 0,
                status: 'active',
                startDate: new Date().toISOString(),
                // Default expiry based on typical plan logic (placeholder for now)
                endDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000).toISOString(),
            });

            return {
                success: true,
                data: subscription,
            };
        } catch (error) {
            console.error('[Subscription] Error creating:', error);
            res.status(500);
            return { success: false, error: 'Failed to create subscription' };
        }
    }
}
