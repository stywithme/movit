import {
    BadRequestException,
    ForbiddenException,
    Injectable,
    NotFoundException,
    ServiceUnavailableException,
} from '@nestjs/common';
import { PrismaService } from '@/prisma/prisma.service';
import { CreateSubscriptionDto } from './dto/create-subscription.dto';
import { UpdateSubscriptionDto } from './dto/update-subscription.dto';
import {
    BillingPeriod,
    CancelSubscriptionInput,
    CreateSubscriptionCheckoutInput,
    VerifyGooglePlayPurchaseInput,
    VerifyAppStorePurchaseInput,
} from './subscription.types';
import {
    createPayment,
    getPaymentDetails,
    verifyWebhookSignature,
} from '@/modules/booking-payments/myfatoorah.client';
import {
    cancelGooglePlaySubscription,
    verifyGooglePlaySubscription,
} from './google-play.client';
import {
    assertAppStorePayloadMatchesRequest,
    getExpectedAppStoreBundleId,
    isAppStoreEntitlementActive,
    verifyAppStoreSignedTransaction,
} from './app-store.client';
import { Prisma } from '@prisma/client';

const ACTIVE_ENTITLEMENT_STATUSES = ['active', 'cancelled'];
const DEFAULT_PAYMENT_CURRENCY = 'SAR';

export type MyFatoorahReconcileOutcome = {
    outcome: 'paid' | 'pending' | 'failed';
    checkout: any;
};

@Injectable()
export class SubscriptionService {
    constructor(private readonly db: PrismaService) { }

    private get prisma(): any {
        return this.db as any;
    }

    private toNumber(value: unknown): number {
        if (value == null) return 0;
        return Number(value);
    }

    private serializePlan(plan: any) {
        if (!plan) return plan;
        return {
            ...plan,
            monthlyPrice: this.toNumber(plan.monthlyPrice),
            yearlyPrice: this.toNumber(plan.yearlyPrice),
            discount: plan.discount == null ? 0 : this.toNumber(plan.discount),
        };
    }

    private serializeSubscription(subscription: any) {
        if (!subscription) return subscription;
        return {
            ...subscription,
            amountPaid: this.toNumber(subscription.amountPaid),
            plan: this.serializePlan(subscription.plan),
        };
    }

    private serializeCheckout(checkout: any) {
        if (!checkout) return checkout;
        return {
            ...checkout,
            amount: this.toNumber(checkout.amount),
            plan: this.serializePlan(checkout.plan),
            subscription: this.serializeSubscription(checkout.subscription),
        };
    }

    private planAmount(plan: any, billingPeriod: BillingPeriod): number {
        return billingPeriod === 'yearly'
            ? this.toNumber(plan.yearlyPrice)
            : this.toNumber(plan.monthlyPrice);
    }

    private planGoogleProductId(plan: any, billingPeriod: BillingPeriod): string | null {
        return billingPeriod === 'yearly'
            ? plan.yearlyGooglePlayProductId || null
            : plan.monthlyGooglePlayProductId || null;
    }

    private planAppStoreProductId(plan: any, billingPeriod: BillingPeriod): string | null {
        return billingPeriod === 'yearly'
            ? plan.yearlyAppStoreProductId || null
            : plan.monthlyAppStoreProductId || null;
    }

    private async paymentCurrency(plan: any): Promise<string> {
        const [systemCurrency, bookingCurrency] = await Promise.all([
            this.prisma.system.findUnique({ where: { key: 'currency' } }),
            this.prisma.system.findUnique({ where: { key: 'booking_currency' } }),
        ]);
        return systemCurrency?.value || bookingCurrency?.value || plan.currency || DEFAULT_PAYMENT_CURRENCY;
    }

    private addPeriod(start: Date, billingPeriod: BillingPeriod): Date {
        const end = new Date(start);
        if (billingPeriod === 'yearly') {
            end.setFullYear(end.getFullYear() + 1);
        } else {
            end.setMonth(end.getMonth() + 1);
        }
        return end;
    }

    private publicApiUrl(): string | null {
        const raw =
            process.env.BACKEND_PUBLIC_URL ||
            process.env.PUBLIC_API_URL ||
            process.env.API_PUBLIC_URL ||
            process.env.API_BASE_URL ||
            process.env.APP_URL;
        return raw ? raw.replace(/\/$/, '') : null;
    }

    private async getActivePlanOrThrow(planId: string) {
        const plan = await this.prisma.plan.findFirst({
            where: { id: planId, isActive: true },
        });
        if (!plan) throw new NotFoundException('Plan not found or inactive');
        return plan;
    }

    async create(createSubscriptionDto: CreateSubscriptionDto) {
        const subscription = await this.prisma.subscription.create({
            data: {
                ...createSubscriptionDto,
                amountPaid: Number(createSubscriptionDto.amountPaid),
                startDate: createSubscriptionDto.startDate
                    ? new Date(createSubscriptionDto.startDate)
                    : new Date(),
                endDate: new Date(createSubscriptionDto.endDate),
            },
            include: {
                user: true,
                plan: true,
            },
        });
        await this.syncUserEntitlement(createSubscriptionDto.userId);
        return this.serializeSubscription(subscription);
    }

    async findAll(query: any = {}) {
        const { status, search, userId, page = 1, limit = 10 } = query;
        const skip = (Number(page) - 1) * Number(limit);

        const where: any = {};
        if (status) where.status = status;
        if (userId) where.userId = userId;

        if (search) {
            where.OR = [
                { user: { name: { contains: search, mode: 'insensitive' } } },
                { user: { email: { contains: search, mode: 'insensitive' } } },
            ];
        }

        const [data, total] = await Promise.all([
            this.prisma.subscription.findMany({
                where,
                skip,
                take: Number(limit),
                orderBy: { createdAt: 'desc' },
                include: {
                    user: {
                        select: { id: true, name: true, email: true, avatarUrl: true },
                    },
                    plan: true,
                },
            }),
            this.prisma.subscription.count({ where }),
        ]);

        return {
            data: data.map((item: any) => this.serializeSubscription(item)),
            meta: {
                total,
                page: Number(page),
                limit: Number(limit),
                totalPages: Math.ceil(total / Number(limit)),
            },
        };
    }

    async findByUserId(userId: string) {
        const data = await this.prisma.subscription.findMany({
            where: { userId },
            orderBy: { createdAt: 'desc' },
            include: { plan: true },
        });
        return data.map((item: any) => this.serializeSubscription(item));
    }

    async findOne(id: string) {
        const subscription = await this.prisma.subscription.findUnique({
            where: { id },
            include: {
                user: {
                    select: { id: true, name: true, email: true, avatarUrl: true },
                },
                plan: true,
            },
        });

        if (!subscription) {
            throw new NotFoundException(`Subscription with ID ${id} not found`);
        }

        return this.serializeSubscription(subscription);
    }

    async update(id: string, updateSubscriptionDto: UpdateSubscriptionDto) {
        const existing = await this.findOne(id);

        const subscription = await this.prisma.subscription.update({
            where: { id },
            data: {
                ...updateSubscriptionDto,
                ...(updateSubscriptionDto.amountPaid !== undefined
                    ? { amountPaid: Number(updateSubscriptionDto.amountPaid) }
                    : {}),
                ...(updateSubscriptionDto.startDate
                    ? { startDate: new Date(updateSubscriptionDto.startDate) }
                    : {}),
                ...(updateSubscriptionDto.endDate
                    ? { endDate: new Date(updateSubscriptionDto.endDate) }
                    : {}),
            },
            include: {
                user: {
                    select: { id: true, name: true, email: true, avatarUrl: true },
                },
                plan: true,
            },
        });
        await this.syncUserEntitlement(existing.userId);
        return this.serializeSubscription(subscription);
    }

    async remove(id: string) {
        const existing = await this.findOne(id);
        const subscription = await this.prisma.subscription.update({
            where: { id },
            data: { status: 'cancelled', cancelAtPeriodEnd: true, cancelledAt: new Date() },
            include: { plan: true },
        });
        await this.syncUserEntitlement(existing.userId);
        return this.serializeSubscription(subscription);
    }

    async getStatus(userId: string) {
        await this.syncUserEntitlement(userId);
        const now = new Date();
        const [activeSubscription, pendingCheckouts, user] = await Promise.all([
            this.prisma.subscription.findFirst({
                where: {
                    userId,
                    status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                    endDate: { gt: now },
                },
                orderBy: { endDate: 'desc' },
                include: { plan: true },
            }),
            this.prisma.subscriptionCheckout.findMany({
                where: { userId, status: { in: ['creating', 'pending'] } },
                orderBy: { createdAt: 'desc' },
                include: { plan: true },
            }),
            this.prisma.user.findUnique({
                where: { id: userId },
                select: { isPro: true, subscriptionExpiry: true },
            }),
        ]);

        const isPro = Boolean(user?.isPro && user?.subscriptionExpiry && user.subscriptionExpiry > now);
        return {
            isPro,
            isFree: !isPro,
            subscriptionExpiry: user?.subscriptionExpiry?.toISOString() ?? null,
            activeSubscription: this.serializeSubscription(activeSubscription),
            pendingCheckouts: pendingCheckouts.map((checkout: any) => this.serializeCheckout(checkout)),
        };
    }

    async createCheckout(userId: string, dto: CreateSubscriptionCheckoutInput) {
        if (dto.gateway !== 'myfatoorah') {
            throw new BadRequestException('Use /mobile/subscriptions/google-play/verify for Google Play purchases');
        }

        const plan = await this.getActivePlanOrThrow(dto.planId);
        const amount = this.planAmount(plan, dto.billingPeriod);
        if (amount < 0) throw new BadRequestException('Plan price cannot be negative');

        if (dto.idempotencyKey) {
            const existing = await this.prisma.subscriptionCheckout.findUnique({
                where: { idempotencyKey: dto.idempotencyKey },
                include: { plan: true, subscription: { include: { plan: true } } },
            });
            if (existing && existing.userId === userId) {
                return this.serializeCheckout(existing);
            }
        }

        const user = await this.prisma.user.findUnique({
            where: { id: userId },
            select: { name: true, email: true },
        });
        if (!user) throw new NotFoundException('User not found');

        const currency = await this.paymentCurrency(plan);
        const expiresAt = new Date(Date.now() + 60 * 60 * 1000);

        const checkout = await this.prisma.subscriptionCheckout.create({
            data: {
                userId,
                planId: plan.id,
                gateway: 'myfatoorah',
                billingPeriod: dto.billingPeriod,
                status: amount === 0 ? 'paid' : 'creating',
                currency,
                amount,
                expiresAt,
                idempotencyKey: dto.idempotencyKey,
                rawPayload: dto.replaceSubscriptionId
                    ? { replaceSubscriptionId: dto.replaceSubscriptionId }
                    : undefined,
            },
            include: { plan: true },
        });

        if (amount === 0) {
            return this.activateCheckout(checkout.id, {
                myFatoorahInvoiceId: null,
                myFatoorahPaymentId: null,
                rawPayload: { freeCheckout: true },
            });
        }

        try {
            const apiBase = this.publicApiUrl();
            const result = await createPayment({
                NotificationOption: 'LNK',
                InvoiceValue: amount,
                CurrencyIso: currency,
                CustomerName: user.name,
                CustomerEmail: user.email,
                ExternalIdentifier: checkout.id,
                CallBackUrl: apiBase
                    ? `${apiBase}/api/payments/myfatoorah/subscriptions/result?checkoutId=${checkout.id}`
                    : undefined,
                ErrorUrl: apiBase
                    ? `${apiBase}/api/payments/myfatoorah/subscriptions/result?checkoutId=${checkout.id}&failed=true`
                    : undefined,
            }) as any;

            const updated = await this.prisma.subscriptionCheckout.update({
                where: { id: checkout.id },
                data: {
                    status: 'pending',
                    paymentUrl: result?.Data?.PaymentURL || result?.Data?.InvoiceURL || null,
                    myFatoorahInvoiceId: result?.Data?.InvoiceId
                        ? String(result.Data.InvoiceId)
                        : null,
                    myFatoorahPaymentId: result?.Data?.PaymentId
                        ? String(result.Data.PaymentId)
                        : null,
                    rawPayload: result,
                },
                include: { plan: true },
            });
            return this.serializeCheckout(updated);
        } catch (error) {
            const lastError = error instanceof Error ? error.message : 'Payment gateway error';
            console.warn('[Subscription] MyFatoorah checkout failed', {
                checkoutId: checkout.id,
                planId: plan.id,
                billingPeriod: dto.billingPeriod,
                amount,
                currency,
                error: lastError,
            });
            await this.prisma.subscriptionCheckout.update({
                where: { id: checkout.id },
                data: {
                    status: 'failed',
                    failedAt: new Date(),
                    lastError,
                },
            });
            if (
                lastError.includes('MYFATOORAH_API_TOKEN') ||
                lastError.includes('MYFATOORAH_TOKEN') ||
                lastError.includes('MYFATOORAH_API_KEY') ||
                lastError.includes('MyFatoorah API token is not configured')
            ) {
                throw new ServiceUnavailableException(
                    'Payment gateway is not configured on the server (set MYFATOORAH_API_KEY, MYFATOORAH_API_TOKEN, or MYFATOORAH_TOKEN, and use MYFATOORAH_ENV=sandbox or MYFATOORAH_API_BASE_URL for test mode; then restart).',
                );
            }
            throw new BadRequestException(lastError);
        }
    }

    async getCheckout(userId: string, checkoutId: string) {
        const checkout = await this.prisma.subscriptionCheckout.findUnique({
            where: { id: checkoutId },
            include: { plan: true, subscription: { include: { plan: true } } },
        });
        if (!checkout) throw new NotFoundException('Checkout not found');
        if (checkout.userId !== userId) throw new ForbiddenException();
        return this.serializeCheckout(checkout);
    }

    async reconcileMyFatoorahResult(input: {
        checkoutId?: string | null;
        paymentId?: string | null;
        invoiceId?: string | null;
        failed?: boolean;
    }): Promise<MyFatoorahReconcileOutcome> {
        let checkout = await this.findCheckoutForGatewayRef(input);
        if (!checkout) throw new NotFoundException('Checkout not found');

        if (input.failed) {
            const serialized = await this.failCheckout(checkout.id, 'Payment failed or was cancelled');
            return { outcome: 'failed', checkout: serialized };
        }

        let details: any = null;
        if (input.paymentId) {
            details = await getPaymentDetails(input.paymentId, 'PaymentId');
        } else if (input.invoiceId || checkout.myFatoorahInvoiceId) {
            details = await getPaymentDetails(input.invoiceId || checkout.myFatoorahInvoiceId, 'InvoiceId');
        }

        if (!this.isMyFatoorahPaid(details)) {
            const fresh = await this.prisma.subscriptionCheckout.findUnique({
                where: { id: checkout.id },
                include: { plan: true, subscription: { include: { plan: true } } },
            });
            return {
                outcome: 'pending',
                checkout: this.serializeCheckout(fresh || checkout),
            };
        }

        const serialized = await this.activateCheckout(checkout.id, {
            myFatoorahInvoiceId: this.extractInvoiceId(details) || checkout.myFatoorahInvoiceId,
            myFatoorahPaymentId: this.extractPaymentId(details) || input.paymentId || checkout.myFatoorahPaymentId,
            rawPayload: details,
        });
        return { outcome: 'paid', checkout: serialized };
    }

    async handleMyFatoorahWebhook(payload: unknown, signature: string) {
        if (!verifyWebhookSignature(payload, signature)) {
            throw new BadRequestException('Invalid webhook signature');
        }

        const invoiceId = this.extractInvoiceId(payload);
        const paymentId = this.extractPaymentId(payload);
        const checkout = await this.findCheckoutForGatewayRef({ invoiceId, paymentId });
        if (!checkout) return { handled: false };

        const details = invoiceId
            ? await getPaymentDetails(invoiceId, 'InvoiceId')
            : paymentId
                ? await getPaymentDetails(paymentId, 'PaymentId')
                : payload;

        if (this.isMyFatoorahPaid(details) || this.isMyFatoorahPaid(payload)) {
            await this.activateCheckout(checkout.id, {
                myFatoorahInvoiceId: invoiceId || checkout.myFatoorahInvoiceId,
                myFatoorahPaymentId: paymentId || checkout.myFatoorahPaymentId,
                rawPayload: { webhook: payload, details },
            });
        } else if (this.isMyFatoorahFailed(details) || this.isMyFatoorahFailed(payload)) {
            await this.failCheckout(checkout.id, 'Payment failed');
        }

        return { handled: true };
    }

    async verifyGooglePlay(userId: string, dto: VerifyGooglePlayPurchaseInput) {
        const plan = await this.getActivePlanOrThrow(dto.planId);
        const expectedProductId = this.planGoogleProductId(plan, dto.billingPeriod);
        const allowUnmapped = process.env.ALLOW_UNMAPPED_GOOGLE_PLAY_PRODUCTS === 'true';
        if (expectedProductId && expectedProductId !== dto.productId) {
            throw new BadRequestException('Google Play product does not match selected plan');
        }
        if (!expectedProductId && !allowUnmapped) {
            throw new BadRequestException('Plan is missing Google Play product mapping');
        }

        const packageName =
            dto.packageName ||
            process.env.GOOGLE_PLAY_PACKAGE_NAME ||
            process.env.ANDROID_PACKAGE_NAME;
        if (!packageName) throw new BadRequestException('Google Play package name is not configured');

        const verification = await verifyGooglePlaySubscription({
            packageName,
            purchaseToken: dto.purchaseToken,
            productId: dto.productId,
        });

        if (verification.productId && verification.productId !== dto.productId) {
            throw new BadRequestException('Verified product does not match purchase request');
        }

        const expiry = verification.expiryTime ? new Date(verification.expiryTime) : null;
        if (!verification.isEntitlementActive || !expiry || expiry <= new Date()) {
            await this.prisma.subscriptionCheckout.create({
                data: {
                    userId,
                    planId: plan.id,
                    gateway: 'google_play',
                    billingPeriod: dto.billingPeriod,
                    status: 'failed',
                    currency: plan.currency || DEFAULT_PAYMENT_CURRENCY,
                    amount: this.planAmount(plan, dto.billingPeriod),
                    googlePlayProductId: dto.productId,
                    googlePlayPurchaseToken: dto.purchaseToken,
                    failedAt: new Date(),
                    lastError: verification.subscriptionState,
                    rawPayload: verification.raw,
                },
            });
            throw new BadRequestException('Google Play subscription is not active');
        }

        const subscription = await this.upsertGooglePlaySubscription(userId, plan, dto, verification, expiry);
        await this.syncUserEntitlement(userId);
        return {
            subscription: this.serializeSubscription(subscription),
            status: await this.getStatus(userId),
        };
    }

    async verifyAppStore(userId: string, dto: VerifyAppStorePurchaseInput) {
        const plan = await this.getActivePlanOrThrow(dto.planId);
        const expectedProductId = this.planAppStoreProductId(plan, dto.billingPeriod);
        const allowUnmapped = process.env.ALLOW_UNMAPPED_APP_STORE_PRODUCTS === 'true';
        if (expectedProductId && expectedProductId !== dto.productId) {
            throw new BadRequestException('App Store product does not match selected plan');
        }
        if (!expectedProductId && !allowUnmapped) {
            throw new BadRequestException('Plan is missing App Store product mapping');
        }

        let payload;
        try {
            payload = await verifyAppStoreSignedTransaction(dto.signedTransactionInfo);
            assertAppStorePayloadMatchesRequest(payload, {
                productId: dto.productId,
                transactionId: dto.transactionId,
                originalTransactionId: dto.originalTransactionId,
            }, getExpectedAppStoreBundleId());
        } catch (error) {
            const message = error instanceof Error ? error.message : 'Invalid App Store signed transaction';
            throw new BadRequestException(message);
        }

        const expiry = payload.expiresDate ? new Date(payload.expiresDate) : null;
        if (!isAppStoreEntitlementActive(payload) || (expiry && expiry <= new Date())) {
            await this.prisma.subscriptionCheckout.create({
                data: {
                    userId,
                    planId: plan.id,
                    gateway: 'app_store',
                    billingPeriod: dto.billingPeriod,
                    status: 'failed',
                    currency: plan.currency || DEFAULT_PAYMENT_CURRENCY,
                    amount: this.planAmount(plan, dto.billingPeriod),
                    failedAt: new Date(),
                    lastError: 'App Store subscription is not active',
                    rawPayload: payload,
                },
            });
            throw new BadRequestException('App Store subscription is not active');
        }

        const subscription = await this.upsertAppStoreSubscription(
            userId,
            plan,
            dto,
            payload,
            expiry ?? this.addPeriod(new Date(), dto.billingPeriod),
        );
        await this.syncUserEntitlement(userId);
        return {
            subscription: this.serializeSubscription(subscription),
            status: await this.getStatus(userId),
        };
    }

    async cancelForUser(userId: string, dto: CancelSubscriptionInput) {
        const subscription = dto.subscriptionId
            ? await this.prisma.subscription.findUnique({ where: { id: dto.subscriptionId }, include: { plan: true } })
            : await this.prisma.subscription.findFirst({
                where: {
                    userId,
                    status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                    endDate: { gt: new Date() },
                },
                orderBy: { endDate: 'desc' },
                include: { plan: true },
            });

        if (!subscription) throw new NotFoundException('Active subscription not found');
        if (subscription.userId !== userId) throw new ForbiddenException();

        if (subscription.gateway === 'google_play' && subscription.googlePlayPurchaseToken && subscription.googlePlayProductId) {
            await cancelGooglePlaySubscription({
                packageName:
                    subscription.googlePlayPackageName ||
                    process.env.GOOGLE_PLAY_PACKAGE_NAME ||
                    process.env.ANDROID_PACKAGE_NAME ||
                    '',
                productId: subscription.googlePlayProductId,
                purchaseToken: subscription.googlePlayPurchaseToken,
                cancellationType: dto.immediate
                    ? 'DEVELOPER_REQUESTED_STOP_PAYMENTS'
                    : 'USER_REQUESTED_STOP_RENEWALS',
            });
        }

        const endDate = dto.immediate ? new Date() : subscription.endDate;
        const updated = await this.prisma.subscription.update({
            where: { id: subscription.id },
            data: {
                status: 'cancelled',
                cancelAtPeriodEnd: !dto.immediate,
                cancelledAt: new Date(),
                endDate,
                currentPeriodEnd: endDate,
                metadata: {
                    ...(subscription.metadata || {}),
                    cancelReason: dto.reason || null,
                    cancelledBy: 'user',
                },
            },
            include: { plan: true },
        });

        await this.syncUserEntitlement(userId);
        return {
            subscription: this.serializeSubscription(updated),
            status: await this.getStatus(userId),
        };
    }

    async syncUserEntitlement(userId: string) {
        const now = new Date();
        await this.prisma.subscription.updateMany({
            where: {
                userId,
                status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                endDate: { lte: now },
            },
            data: { status: 'expired', autoRenew: false },
        });

        const active = await this.prisma.subscription.findFirst({
            where: {
                userId,
                status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                endDate: { gt: now },
            },
            orderBy: { endDate: 'desc' },
        });

        await this.prisma.user.update({
            where: { id: userId },
            data: {
                isPro: Boolean(active),
                subscriptionExpiry: active?.endDate ?? null,
            },
        });

        return active;
    }

    private async upsertGooglePlaySubscription(
        userId: string,
        plan: any,
        dto: VerifyGooglePlayPurchaseInput,
        verification: any,
        expiry: Date,
    ) {
        const now = new Date();
        const amount = this.planAmount(plan, dto.billingPeriod);
        const linkedPurchaseToken = dto.linkedPurchaseToken || verification.linkedPurchaseToken;
        const previous = linkedPurchaseToken
            ? await this.prisma.subscription.findUnique({
                where: { googlePlayPurchaseToken: linkedPurchaseToken },
            })
            : null;

        if (previous) {
            await this.prisma.subscription.update({
                where: { id: previous.id },
                data: {
                    status: 'replaced',
                    endDate: now,
                    currentPeriodEnd: now,
                    cancelledAt: now,
                    autoRenew: false,
                },
            });
        }

        await this.prisma.subscription.updateMany({
            where: {
                userId,
                id: previous ? { not: previous.id } : undefined,
                status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                endDate: { gt: now },
                OR: [{ googlePlayPurchaseToken: null }, { googlePlayPurchaseToken: { not: dto.purchaseToken } }],
            },
            data: {
                status: 'replaced',
                endDate: now,
                currentPeriodEnd: now,
                cancelledAt: now,
                autoRenew: false,
            },
        });

        const existing = await this.prisma.subscription.findUnique({
            where: { googlePlayPurchaseToken: dto.purchaseToken },
            include: { plan: true },
        });

        const data = {
            userId,
            planId: plan.id,
            status: 'active',
            billingPeriod: dto.billingPeriod,
            gateway: 'google_play',
            amountPaid: amount,
            startDate: verification.startTime ? new Date(verification.startTime) : now,
            endDate: expiry,
            currentPeriodStart: verification.startTime ? new Date(verification.startTime) : now,
            currentPeriodEnd: expiry,
            autoRenew: verification.autoRenewing,
            cancelAtPeriodEnd: !verification.autoRenewing,
            upgradedFromId: previous?.id || null,
            googlePlayPackageName: verification.packageName,
            googlePlayProductId: dto.productId,
            googlePlayPurchaseToken: dto.purchaseToken,
            googlePlayOrderId: dto.orderId || verification.orderId,
            lastVerifiedAt: now,
            metadata: {
                subscriptionState: verification.subscriptionState,
                linkedPurchaseToken,
                raw: verification.raw,
            },
        };

        const subscription = existing
            ? await this.prisma.subscription.update({
                where: { id: existing.id },
                data,
                include: { plan: true },
            })
            : await this.prisma.subscription.create({
                data,
                include: { plan: true },
            });

        await this.prisma.subscriptionCheckout.create({
            data: {
                userId,
                planId: plan.id,
                subscriptionId: subscription.id,
                gateway: 'google_play',
                billingPeriod: dto.billingPeriod,
                status: 'paid',
                currency: plan.currency || DEFAULT_PAYMENT_CURRENCY,
                amount,
                googlePlayProductId: dto.productId,
                googlePlayPurchaseToken: dto.purchaseToken,
                paidAt: now,
                rawPayload: verification.raw,
            },
        });

        return subscription;
    }

    private async upsertAppStoreSubscription(
        userId: string,
        plan: any,
        dto: VerifyAppStorePurchaseInput,
        payload: any,
        expiry: Date,
    ) {
        const now = new Date();
        const amount = this.planAmount(plan, dto.billingPeriod);

        await this.prisma.subscription.updateMany({
            where: {
                userId,
                status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                endDate: { gt: now },
                OR: [
                    { appStoreTransactionId: null },
                    { appStoreTransactionId: { not: dto.transactionId } },
                ],
            },
            data: {
                status: 'replaced',
                endDate: now,
                currentPeriodEnd: now,
                cancelledAt: now,
                autoRenew: false,
            },
        });

        const existing = await this.prisma.subscription.findUnique({
            where: { appStoreTransactionId: dto.transactionId },
            include: { plan: true },
        });

        if (existing && existing.userId !== userId) {
            throw new ForbiddenException('This App Store transaction is already linked to another account');
        }

        const data = {
            userId,
            planId: plan.id,
            status: 'active',
            billingPeriod: dto.billingPeriod,
            gateway: 'app_store',
            amountPaid: amount,
            startDate: now,
            endDate: expiry,
            currentPeriodStart: now,
            currentPeriodEnd: expiry,
            autoRenew: true,
            cancelAtPeriodEnd: false,
            appStoreProductId: dto.productId,
            appStoreTransactionId: dto.transactionId,
            appStoreOriginalTransactionId: dto.originalTransactionId,
            lastVerifiedAt: now,
            metadata: {
                environment: payload.environment ?? null,
                raw: payload,
            },
        };

        const subscription = existing
            ? await this.prisma.subscription.update({
                where: { id: existing.id },
                data,
                include: { plan: true },
            })
            : await this.prisma.subscription.create({
                data,
                include: { plan: true },
            });

        await this.prisma.subscriptionCheckout.create({
            data: {
                userId,
                planId: plan.id,
                subscriptionId: subscription.id,
                gateway: 'app_store',
                billingPeriod: dto.billingPeriod,
                status: 'paid',
                currency: plan.currency || DEFAULT_PAYMENT_CURRENCY,
                amount,
                paidAt: now,
                rawPayload: payload,
            },
        });

        return subscription;
    }

    private async activateCheckout(
        checkoutId: string,
        refs: {
            myFatoorahInvoiceId: string | null;
            myFatoorahPaymentId: string | null;
            rawPayload: unknown;
        },
    ) {
        const now = new Date();
        const result = await this.prisma.$transaction(
            async (tx: any) => {
            const checkout = await tx.subscriptionCheckout.findUnique({
                where: { id: checkoutId },
                include: { plan: true, subscription: { include: { plan: true } } },
            });
            if (!checkout) throw new NotFoundException('Checkout not found');

            if (checkout.status === 'paid' && checkout.subscription) {
                return checkout;
            }

            const existingActive = await tx.subscription.findFirst({
                where: {
                    userId: checkout.userId,
                    status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                    endDate: { gt: now },
                },
                orderBy: { endDate: 'desc' },
            });

            const replaceSubscriptionId = checkout.rawPayload?.replaceSubscriptionId;
            const shouldReplaceExisting =
                replaceSubscriptionId ||
                (existingActive && existingActive.planId !== checkout.planId);

            if (shouldReplaceExisting) {
                await tx.subscription.updateMany({
                    where: {
                        userId: checkout.userId,
                        status: { in: ACTIVE_ENTITLEMENT_STATUSES },
                        endDate: { gt: now },
                    },
                    data: {
                        status: 'replaced',
                        endDate: now,
                        currentPeriodEnd: now,
                        cancelledAt: now,
                        autoRenew: false,
                        cancelAtPeriodEnd: false,
                    },
                });
            }

            const startsAt =
                !shouldReplaceExisting && existingActive?.endDate && existingActive.endDate > now
                    ? existingActive.endDate
                    : now;
            const endsAt = this.addPeriod(startsAt, checkout.billingPeriod);

            const subscription = await tx.subscription.create({
                data: {
                    userId: checkout.userId,
                    planId: checkout.planId,
                    status: 'active',
                    billingPeriod: checkout.billingPeriod,
                    gateway: 'myfatoorah',
                    amountPaid: checkout.amount,
                    startDate: startsAt,
                    endDate: endsAt,
                    currentPeriodStart: startsAt,
                    currentPeriodEnd: endsAt,
                    autoRenew: false,
                    myFatoorahInvoiceId: refs.myFatoorahInvoiceId,
                    myFatoorahPaymentId: refs.myFatoorahPaymentId,
                    lastVerifiedAt: now,
                    upgradedFromId: shouldReplaceExisting ? existingActive?.id ?? null : null,
                    metadata: refs.rawPayload ? { payment: refs.rawPayload } : undefined,
                },
                include: { plan: true },
            });

            const updatedCheckout = await tx.subscriptionCheckout.update({
                where: { id: checkout.id },
                data: {
                    status: 'paid',
                    paidAt: now,
                    subscriptionId: subscription.id,
                    myFatoorahInvoiceId: refs.myFatoorahInvoiceId,
                    myFatoorahPaymentId: refs.myFatoorahPaymentId,
                    rawPayload: refs.rawPayload,
                },
                include: { plan: true, subscription: { include: { plan: true } } },
            });

            await tx.user.update({
                where: { id: checkout.userId },
                data: {
                    isPro: true,
                    subscriptionExpiry: endsAt,
                },
            });

            return updatedCheckout;
            },
            {
                isolationLevel: Prisma.TransactionIsolationLevel.Serializable,
                maxWait: 5000,
                timeout: 15000,
            },
        );

        return this.serializeCheckout(result);
    }

    private async failCheckout(checkoutId: string, reason: string) {
        const checkout = await this.prisma.subscriptionCheckout.update({
            where: { id: checkoutId },
            data: {
                status: 'failed',
                failedAt: new Date(),
                lastError: reason,
            },
            include: { plan: true },
        });
        return this.serializeCheckout(checkout);
    }

    private async findCheckoutForGatewayRef(input: {
        checkoutId?: string | null;
        paymentId?: string | null;
        invoiceId?: string | null;
    }) {
        if (input.checkoutId) {
            const byId = await this.prisma.subscriptionCheckout.findUnique({
                where: { id: input.checkoutId },
                include: { plan: true, subscription: { include: { plan: true } } },
            });
            if (byId) return byId;
        }

        const OR = [
            input.invoiceId ? { myFatoorahInvoiceId: String(input.invoiceId) } : null,
            input.paymentId ? { myFatoorahPaymentId: String(input.paymentId) } : null,
        ].filter(Boolean);
        if (!OR.length) return null;

        return this.prisma.subscriptionCheckout.findFirst({
            where: { OR },
            include: { plan: true, subscription: { include: { plan: true } } },
            orderBy: { createdAt: 'desc' },
        });
    }

    private localizedName(value: any): string {
        if (!value) return 'Subscription';
        if (typeof value === 'string') return value;
        return value.en || value.ar || Object.values(value)[0] || 'Subscription';
    }

    private extractInvoiceId(payload: any): string | null {
        const value =
            payload?.Data?.InvoiceId ||
            payload?.Data?.InvoiceID ||
            payload?.InvoiceId ||
            payload?.InvoiceID ||
            payload?.Data?.invoiceId ||
            payload?.invoiceId;
        return value ? String(value) : null;
    }

    private extractPaymentId(payload: any): string | null {
        const value =
            payload?.Data?.PaymentId ||
            payload?.Data?.PaymentID ||
            payload?.PaymentId ||
            payload?.PaymentID ||
            payload?.Data?.paymentId ||
            payload?.paymentId;
        return value ? String(value) : null;
    }

    private collectStatuses(payload: any): string[] {
        const statuses = [
            payload?.Data?.InvoiceStatus,
            payload?.Data?.TransactionStatus,
            payload?.InvoiceStatus,
            payload?.TransactionStatus,
            ...(payload?.Data?.InvoiceTransactions || []).map((tx: any) => tx.TransactionStatus),
        ];
        return statuses.filter(Boolean).map((status) => String(status).toUpperCase());
    }

    private isMyFatoorahPaid(payload: any): boolean {
        return this.collectStatuses(payload).some((status) => ['PAID', 'SUCCESS', 'CAPTURED'].includes(status));
    }

    private isMyFatoorahFailed(payload: any): boolean {
        return this.collectStatuses(payload).some((status) =>
            ['FAILED', 'CANCELED', 'CANCELLED', 'EXPIRED'].includes(status),
        );
    }
}
