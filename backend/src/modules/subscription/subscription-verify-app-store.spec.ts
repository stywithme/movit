/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access */

import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import { SubscriptionService } from './subscription.service';
import { PrismaService } from '@/prisma/prisma.service';

jest.mock('./google-play.client', () => ({
    cancelGooglePlaySubscription: jest.fn(),
    verifyGooglePlaySubscription: jest.fn(),
}));

jest.mock('./app-store.client', () => ({
    getExpectedAppStoreBundleId: jest.fn(() => 'com.example.ios'),
    isAppStoreEntitlementActive: jest.fn(() => true),
    assertAppStorePayloadMatchesRequest: jest.fn(),
    verifyAppStoreSignedTransaction: jest.fn(),
}));

import {
    assertAppStorePayloadMatchesRequest,
    isAppStoreEntitlementActive,
    verifyAppStoreSignedTransaction,
} from './app-store.client';

describe('SubscriptionService.verifyAppStore', () => {
    let service: SubscriptionService;

    const mockPrisma = {
        plan: {
            findFirst: jest.fn(),
        },
        subscription: {
            findUnique: jest.fn(),
            updateMany: jest.fn(),
            update: jest.fn(),
            create: jest.fn(),
        },
        subscriptionCheckout: {
            create: jest.fn(),
        },
        user: {
            update: jest.fn(),
        },
    };

    beforeEach(async () => {
        jest.clearAllMocks();

        const module: TestingModule = await Test.createTestingModule({
            providers: [
                SubscriptionService,
                {
                    provide: PrismaService,
                    useValue: mockPrisma,
                },
            ],
        }).compile();

        service = module.get(SubscriptionService);
    });

    const baseDto = {
        planId: 'plan-1',
        billingPeriod: 'monthly' as const,
        productId: 'pro.monthly',
        transactionId: 'tx-1',
        originalTransactionId: 'orig-1',
        signedTransactionInfo: 'header.payload.signature',
    };

    it('rejects forged JWS before creating a subscription', async () => {
        mockPrisma.plan.findFirst.mockResolvedValue({
            id: 'plan-1',
            isActive: true,
            currency: 'SAR',
            monthlyPrice: 10,
            yearlyPrice: 100,
            monthlyAppStoreProductId: 'pro.monthly',
            yearlyAppStoreProductId: null,
        });
        (verifyAppStoreSignedTransaction as jest.Mock).mockRejectedValue(
            new Error('App Store JWS verification failed: Invalid JWS signature'),
        );

        await expect(service.verifyAppStore('user-1', baseDto)).rejects.toBeInstanceOf(
            BadRequestException,
        );
        expect(mockPrisma.subscription.create).not.toHaveBeenCalled();
        expect(assertAppStorePayloadMatchesRequest).not.toHaveBeenCalled();
    });

    it('rejects cross-user reassignment for an existing App Store transaction', async () => {
        mockPrisma.plan.findFirst.mockResolvedValue({
            id: 'plan-1',
            isActive: true,
            currency: 'SAR',
            monthlyPrice: 10,
            yearlyPrice: 100,
            monthlyAppStoreProductId: 'pro.monthly',
            yearlyAppStoreProductId: null,
        });
        (verifyAppStoreSignedTransaction as jest.Mock).mockResolvedValue({
            transactionId: 'tx-1',
            originalTransactionId: 'orig-1',
            productId: 'pro.monthly',
            bundleId: 'com.example.ios',
            expiresDate: Date.now() + 60_000,
        });
        (isAppStoreEntitlementActive as jest.Mock).mockReturnValue(true);
        mockPrisma.subscription.updateMany.mockResolvedValue({ count: 0 });
        mockPrisma.subscription.findUnique.mockResolvedValue({
            id: 'sub-existing',
            userId: 'other-user',
        });

        await expect(service.verifyAppStore('user-1', baseDto)).rejects.toBeInstanceOf(
            ForbiddenException,
        );
        expect(mockPrisma.subscription.update).not.toHaveBeenCalled();
        expect(mockPrisma.subscription.create).not.toHaveBeenCalled();
    });
});
