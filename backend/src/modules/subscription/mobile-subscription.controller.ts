import {
    BadRequestException,
    Body,
    Controller,
    ForbiddenException,
    Get,
    NotFoundException,
    Param,
    Post,
    Req,
    Res,
    ServiceUnavailableException,
    UseGuards,
} from '@nestjs/common';
import { Throttle, ThrottlerGuard } from '@nestjs/throttler';
import { SubscriptionService } from './subscription.service';
import { verifyMobileToken } from '@/modules/auth/auth.service';
import type { Request, Response } from 'express';
import {
    cancelSubscriptionSchema,
    createSubscriptionCheckoutSchema,
    verifyGooglePlayPurchaseSchema,
    verifyAppStorePurchaseSchema,
} from './subscription.types';

@Controller('mobile/subscriptions')
export class MobileSubscriptionController {
    constructor(private readonly subscriptionService: SubscriptionService) {}

    @Get('mine')
    async findMine(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }

            const data = await this.subscriptionService.findByUserId(authResult.userId);
            return { success: true, data };
        } catch (error) {
            console.error('[Subscription] Error fetching mine:', error);
            res.status(500);
            return { success: false, error: 'Failed to fetch subscriptions' };
        }
    }

    @Get('status')
    async status(@Req() req: Request, @Res({ passthrough: true }) res: Response) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }
            const data = await this.subscriptionService.getStatus(authResult.userId);
            return { success: true, data };
        } catch (error) {
            console.error('[Subscription] Error status:', error);
            res.status(500);
            return { success: false, error: 'Failed to load subscription status' };
        }
    }

    @Post('checkout')
    async checkout(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
        @Body() body: unknown,
    ) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }
            const parsed = createSubscriptionCheckoutSchema.safeParse(body);
            if (!parsed.success) {
                res.status(400);
                return { success: false, error: 'Invalid checkout payload', details: parsed.error.flatten() };
            }
            const data = await this.subscriptionService.createCheckout(authResult.userId, parsed.data);
            return { success: true, data };
        } catch (error) {
            if (error instanceof NotFoundException) {
                res.status(404);
                return { success: false, error: error.message };
            }
            if (error instanceof BadRequestException) {
                res.status(400);
                return { success: false, error: error.message };
            }
            if (error instanceof ServiceUnavailableException) {
                res.status(503);
                return { success: false, error: error.message };
            }
            console.error('[Subscription] Error checkout:', error);
            res.status(500);
            return { success: false, error: 'Failed to create checkout' };
        }
    }

    @Get('checkout/:id')
    async getCheckout(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
        @Param('id') id: string,
    ) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }
            const data = await this.subscriptionService.getCheckout(authResult.userId, id);
            return { success: true, data };
        } catch (error) {
            if (error instanceof NotFoundException) {
                res.status(404);
                return { success: false, error: error.message };
            }
            if (error instanceof ForbiddenException) {
                res.status(403);
                return { success: false, error: error.message };
            }
            if (error instanceof BadRequestException) {
                res.status(400);
                return { success: false, error: error.message };
            }
            console.error('[Subscription] Error getCheckout:', error);
            res.status(500);
            return { success: false, error: 'Failed to load checkout' };
        }
    }

    @UseGuards(ThrottlerGuard)
    @Throttle({ default: { limit: 10, ttl: 60_000 } })
    @Post('google-play/verify')
    async verifyGooglePlay(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
        @Body() body: unknown,
    ) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }
            const parsed = verifyGooglePlayPurchaseSchema.safeParse(body);
            if (!parsed.success) {
                res.status(400);
                return { success: false, error: 'Invalid verify payload', details: parsed.error.flatten() };
            }
            const data = await this.subscriptionService.verifyGooglePlay(authResult.userId, parsed.data);
            return { success: true, data };
        } catch (error) {
            if (error instanceof NotFoundException) {
                res.status(404);
                return { success: false, error: error.message };
            }
            if (error instanceof BadRequestException) {
                res.status(400);
                return { success: false, error: error.message };
            }
            console.error('[Subscription] Error verifyGooglePlay:', error);
            res.status(500);
            return { success: false, error: 'Failed to verify purchase' };
        }
    }

    @UseGuards(ThrottlerGuard)
    @Throttle({ default: { limit: 10, ttl: 60_000 } })
    @Post('app-store/verify')
    async verifyAppStore(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
        @Body() body: unknown,
    ) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }
            const parsed = verifyAppStorePurchaseSchema.safeParse(body);
            if (!parsed.success) {
                res.status(400);
                return { success: false, error: 'Invalid verify payload', details: parsed.error.flatten() };
            }
            const data = await this.subscriptionService.verifyAppStore(authResult.userId, parsed.data);
            return { success: true, data };
        } catch (error) {
            if (error instanceof NotFoundException) {
                res.status(404);
                return { success: false, error: error.message };
            }
            if (error instanceof BadRequestException) {
                res.status(400);
                return { success: false, error: error.message };
            }
            console.error('[Subscription] Error verifyAppStore:', error);
            res.status(500);
            return { success: false, error: 'Failed to verify purchase' };
        }
    }

    @Post('cancel')
    async cancel(
        @Req() req: Request,
        @Res({ passthrough: true }) res: Response,
        @Body() body: unknown,
    ) {
        try {
            const authResult = await verifyMobileToken(req);
            if (!authResult.success || !authResult.userId) {
                res.status(401);
                return { success: false, error: 'Unauthorized' };
            }
            const parsed = cancelSubscriptionSchema.safeParse(body ?? {});
            if (!parsed.success) {
                res.status(400);
                return { success: false, error: 'Invalid cancel payload', details: parsed.error.flatten() };
            }
            const data = await this.subscriptionService.cancelForUser(authResult.userId, parsed.data);
            return { success: true, data };
        } catch (error) {
            if (error instanceof NotFoundException) {
                res.status(404);
                return { success: false, error: error.message };
            }
            if (error instanceof ForbiddenException) {
                res.status(403);
                return { success: false, error: error.message };
            }
            if (error instanceof BadRequestException) {
                res.status(400);
                return { success: false, error: error.message };
            }
            console.error('[Subscription] Error cancel:', error);
            res.status(500);
            return { success: false, error: 'Failed to cancel subscription' };
        }
    }
}
