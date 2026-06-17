import { z } from 'zod';

export const billingPeriodSchema = z.enum(['monthly', 'yearly']);
export type BillingPeriod = z.infer<typeof billingPeriodSchema>;

export const subscriptionGatewaySchema = z.enum(['myfatoorah', 'google_play', 'app_store']);
export type SubscriptionGateway = z.infer<typeof subscriptionGatewaySchema>;

export const createSubscriptionCheckoutSchema = z.object({
    planId: z.string().uuid(),
    billingPeriod: billingPeriodSchema,
    gateway: subscriptionGatewaySchema.default('myfatoorah'),
    idempotencyKey: z.string().min(8).max(128).optional(),
    replaceSubscriptionId: z.string().uuid().optional(),
});

export const verifyGooglePlayPurchaseSchema = z.object({
    planId: z.string().uuid(),
    billingPeriod: billingPeriodSchema,
    productId: z.string().min(1).max(256),
    purchaseToken: z.string().min(8).max(4096),
    packageName: z.string().min(1).max(256).optional(),
    orderId: z.string().max(256).optional(),
    linkedPurchaseToken: z.string().max(4096).optional(),
});

export const cancelSubscriptionSchema = z.object({
    subscriptionId: z.string().uuid().optional(),
    immediate: z.boolean().optional().default(false),
    reason: z.string().max(300).optional(),
});

export type CreateSubscriptionCheckoutInput = z.infer<typeof createSubscriptionCheckoutSchema>;
export type VerifyGooglePlayPurchaseInput = z.infer<typeof verifyGooglePlayPurchaseSchema>;

export const verifyAppStorePurchaseSchema = z.object({
    planId: z.string().uuid(),
    billingPeriod: billingPeriodSchema,
    productId: z.string().min(1).max(256),
    transactionId: z.string().min(1).max(256),
    originalTransactionId: z.string().min(1).max(256),
    signedTransactionInfo: z.string().min(10).max(65536),
});

export type VerifyAppStorePurchaseInput = z.infer<typeof verifyAppStorePurchaseSchema>;
export type CancelSubscriptionInput = z.infer<typeof cancelSubscriptionSchema>;

export type SubscriptionStatusDto = {
    isPro: boolean;
    isFree: boolean;
    subscriptionExpiry: string | null;
    activeSubscription: unknown | null;
    pendingCheckouts: unknown[];
};
