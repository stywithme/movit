import {
  BadRequestException,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import {
  createPayment,
  getPaymentDetails,
  getWebhookSecret,
  updatePayment,
  verifyWebhookSignature,
} from './myfatoorah.client';

/** Prisma delegates until BookingPayment models exist in schema (see tests / future migration). */
type BookingPaymentDelegate = {
  findUnique: (args: unknown) => Promise<unknown>;
  findFirst: (args: unknown) => Promise<unknown>;
  findMany: (args: unknown) => Promise<unknown[]>;
  create: (args: unknown) => Promise<unknown>;
  update: (args: unknown) => Promise<unknown>;
  updateMany: (args: unknown) => Promise<{ count: number }>;
};

type BookingPaymentEventDelegate = {
  findUnique: (args: unknown) => Promise<unknown>;
  create: (args: unknown) => Promise<unknown>;
};

type PrismaLike = {
  booking: {
    findMany: (args: unknown) => Promise<
      Array<{
        id: string;
        userId: string;
        status: string;
        amount: number;
        deletedAt: Date | null;
        user?: { id: string; name: string; email: string };
      }>
    >;
    updateMany: (args: unknown) => Promise<{ count: number }>;
  };
  system: { findUnique: (args: unknown) => Promise<{ value: string } | null> };
  bookingPayment: BookingPaymentDelegate;
  bookingPaymentEvent: BookingPaymentEventDelegate;
  $transaction: <T>(fn: (tx: PrismaLike) => Promise<T>) => Promise<T>;
};

function getDelegates(): Promise<PrismaLike> {
  return getPrisma() as unknown as Promise<PrismaLike>;
}

type CheckoutItem = { bookingId: string; amountSnapshot: number };

type BookingPaymentRow = {
  id: string;
  userId: string;
  gateway: string;
  revision: number;
  status: string;
  currency: string;
  totalAmount: number;
  myFatoorahInvoiceId: string | null;
  myFatoorahPaymentId: string | null;
  paymentUrl: string | null;
  expiresAt: Date | null;
  items: CheckoutItem[];
  supersededAt?: Date | null;
  paidAt?: Date | null;
  lastError?: string | null;
};

function selectionKey(ids: string[]): string {
  return [...ids].sort().join(',');
}

function itemsMatchSelection(
  items: CheckoutItem[] | undefined,
  bookingIds: string[],
): boolean {
  if (!items?.length) return false;
  return selectionKey(items.map((i) => i.bookingId)) === selectionKey(bookingIds);
}

async function paymentCurrency(prisma: PrismaLike): Promise<string> {
  const [currencyRow, bookingCurrencyRow] = await Promise.all([
    prisma.system.findUnique({ where: { key: 'currency' } }),
    prisma.system.findUnique({ where: { key: 'booking_currency' } }),
  ]);
  return currencyRow?.value || bookingCurrencyRow?.value || 'SAR';
}

function gatewayId(value: unknown): string | null {
  return value === undefined || value === null || value === '' ? null : String(value);
}

function publicApiUrl(): string | null {
  const raw =
    process.env.BACKEND_PUBLIC_URL ||
    process.env.PUBLIC_API_URL ||
    process.env.API_PUBLIC_URL ||
    process.env.API_BASE_URL ||
    process.env.APP_URL;
  return raw ? raw.replace(/\/$/, '') : null;
}

async function recordPaymentEvent(
  prisma: PrismaLike,
  input: {
    id: string;
    bookingPaymentId: string;
    eventReference: string;
    rawPayload: unknown;
  },
): Promise<void> {
  try {
    await prisma.bookingPaymentEvent.create({ data: input });
  } catch (error) {
    if ((error as { code?: string })?.code !== 'P2002') {
      throw error;
    }
  }
}

@Injectable()
export class BookingPaymentService {
  async createCheckout(
    userId: string,
    dto: { bookingIds: string[] },
  ): Promise<{
    checkoutId: string;
    paymentUrl: string;
    expiresAt: string;
    totalAmount: number;
    currency: string;
    bookingCount: number;
  }> {
    const prisma = await getDelegates();
    const bookingIds = [...new Set(dto.bookingIds)];

    const bookings = await prisma.booking.findMany({
      where: { id: { in: bookingIds }, deletedAt: null },
      include: { user: true },
    });

    if (bookings.length !== bookingIds.length) {
      throw new BadRequestException('One or more bookings were not found');
    }

    for (const b of bookings) {
      if (b.userId !== userId) {
        throw new ForbiddenException();
      }
      if (b.status !== 'payment_pending') {
        throw new BadRequestException('Booking is not awaiting payment');
      }
    }

    const totalAmount = bookings.reduce((s, b) => s + b.amount, 0);
    if (totalAmount <= 0) {
      throw new BadRequestException('Total amount must be greater than zero');
    }

    const currency = await paymentCurrency(prisma);

    const openCheckouts = await prisma.bookingPayment.findMany({
      where: {
        userId,
        status: { in: ['pending', 'creating'] },
      },
    });

    for (const cp of openCheckouts as BookingPaymentRow[]) {
      if (
        itemsMatchSelection(cp.items, bookingIds) &&
        cp.status === 'pending' &&
        cp.paymentUrl
      ) {
        return {
          checkoutId: cp.id,
          paymentUrl: cp.paymentUrl,
          expiresAt: cp.expiresAt?.toISOString() ?? new Date(Date.now() + 60 * 60 * 1000).toISOString(),
          totalAmount: cp.totalAmount,
          currency: cp.currency,
          bookingCount: bookingIds.length,
        };
      }
    }

    const lastRevRow = await prisma.bookingPayment.findFirst({
      where: { userId },
      orderBy: { revision: 'desc' },
      select: { revision: true },
    });
    const lastRevision =
      lastRevRow && typeof lastRevRow === 'object' && 'revision' in lastRevRow
        ? (lastRevRow as { revision: number }).revision
        : 0;
    const nextRevision = lastRevision + 1;

    const creatingBlock = await prisma.bookingPayment.findFirst({
      where: { userId, status: 'creating' },
    });
    if (creatingBlock) {
      throw new BadRequestException('Another checkout is being prepared');
    }

    return prisma.$transaction(async (tx) => {
      await tx.bookingPayment.updateMany({
        where: {
          userId,
          status: { in: ['pending', 'creating'] },
        },
        data: {
          status: 'superseded',
          supersededAt: new Date(),
        },
      });

      const fresh = await tx.booking.findMany({
        where: { id: { in: bookingIds } },
        select: { id: true, status: true, deletedAt: true },
      });
      for (const row of fresh) {
        if (row.deletedAt != null || row.status !== 'payment_pending') {
          throw new BadRequestException('Booking changed during checkout');
        }
      }

      const items: CheckoutItem[] = bookings.map((b) => ({
        bookingId: b.id,
        amountSnapshot: b.amount,
      }));

      const reserved = (await tx.bookingPayment.create({
        data: {
          userId,
          gateway: 'myfatoorah',
          revision: nextRevision,
          status: 'creating',
          currency,
          totalAmount,
          items: { create: items.map((i) => ({ ...i })) },
        },
      })) as BookingPaymentRow;

      const expiresAt = new Date(Date.now() + 60 * 60 * 1000);
      const apiBase = publicApiUrl();
      const apiResult = (await createPayment({
        OperationType: 'AUTHORIZE',
        PaymentExpiry: expiresAt.toISOString(),
        InvoiceValue: totalAmount,
        CurrencyIso: currency,
        CustomerName: bookings[0]?.user?.name ?? 'Customer',
        CustomerEmail: bookings[0]?.user?.email ?? '',
        ExternalIdentifier: reserved.id,
        CallBackUrl: apiBase
          ? `${apiBase}/api/payments/myfatoorah/result?checkoutId=${reserved.id}`
          : undefined,
        ErrorUrl: apiBase
          ? `${apiBase}/api/payments/myfatoorah/result?checkoutId=${reserved.id}&failed=true`
          : undefined,
        WebhookUrl: apiBase ? `${apiBase}/api/payments/myfatoorah/webhook` : undefined,
      })) as {
        IsSuccess: boolean;
        Data?: {
          InvoiceId?: unknown;
          PaymentId?: unknown;
          PaymentURL?: string;
        };
      };

      if (!apiResult?.IsSuccess || !apiResult.Data) {
        throw new BadRequestException('Payment gateway error');
      }
      const paymentUrl = apiResult.Data.PaymentURL;
      if (!paymentUrl) {
        throw new BadRequestException('Payment gateway did not return a payment URL');
      }

      const finalCheckout = (await tx.bookingPayment.update({
        where: { id: reserved.id },
        data: {
          status: 'pending',
          paymentUrl,
          myFatoorahInvoiceId: gatewayId(apiResult.Data.InvoiceId),
          myFatoorahPaymentId: gatewayId(apiResult.Data.PaymentId),
          expiresAt,
        },
      })) as BookingPaymentRow;

      return {
        checkoutId: finalCheckout.id,
        paymentUrl: finalCheckout.paymentUrl ?? paymentUrl,
        expiresAt: finalCheckout.expiresAt?.toISOString() ?? expiresAt.toISOString(),
        totalAmount: finalCheckout.totalAmount,
        currency: finalCheckout.currency,
        bookingCount: bookingIds.length,
      };
    });
  }

  async handleWebhook(payload: unknown, signature: string): Promise<void> {
    const sig = signature.trim();
    const env = (process.env.MYFATOORAH_ENV || '').toLowerCase();
    const sandboxLike = env === 'sandbox' || env === 'test' || env === 'development';
    const allowUnsigned =
      process.env.MYFATOORAH_WEBHOOK_ALLOW_UNSIGNED === 'true' ||
      (sandboxLike && !getWebhookSecret().trim());

    if (!sig) {
      if (!allowUnsigned) {
        throw new BadRequestException('Missing myfatoorah-signature header');
      }
    } else if (!verifyWebhookSignature(payload, sig)) {
      throw new BadRequestException('Invalid webhook signature');
    }

    await this.resolveGatewayPayment(payload, 'webhook');
  }

  async getResultStatus(
    paymentId: string,
    checkoutId?: string,
  ): Promise<{ status: string; checkoutId: string | null }> {
    return this.resolveGatewayPayment({ PaymentId: paymentId }, 'result', checkoutId, paymentId);
  }

  async getCheckoutStatus(checkoutId: string, userId: string) {
    const prisma = await getDelegates();
    let checkout = (await prisma.bookingPayment.findFirst({
      where: { id: checkoutId, userId },
      include: { items: true },
    })) as BookingPaymentRow | null;

    if (!checkout) {
      throw new BadRequestException('Checkout not found');
    }

    if (['pending', 'creating', 'authorized', 'capture_pending'].includes(checkout.status)) {
      await this.resolveGatewayPayment(
        {
          InvoiceId: checkout.myFatoorahInvoiceId,
          PaymentId: checkout.myFatoorahPaymentId,
          UserDefinedField: checkout.id,
        },
        'status',
        checkout.id,
        checkout.myFatoorahPaymentId ?? undefined,
      );
      checkout = (await prisma.bookingPayment.findFirst({
        where: { id: checkoutId, userId },
        include: { items: true },
      })) as BookingPaymentRow | null;
    }

    if (!checkout) {
      throw new BadRequestException('Checkout not found');
    }

    return {
      checkoutId: checkout.id,
      status: checkout.status,
      totalAmount: checkout.totalAmount,
      currency: checkout.currency,
      bookingIds: checkout.items.map((i) => i.bookingId),
      paidAt: checkout.paidAt?.toISOString(),
      lastError: checkout.lastError,
    };
  }

  async reconcilePayment(
    invoiceId: string,
    paymentId: string,
    checkoutId: string,
    _gatewayTransactionStatus: string,
    webhookEventId: string,
    rawPayload: unknown = {},
    detailsAlreadyVerified = false,
  ): Promise<void> {
    const prisma = await getDelegates();

    const payment = (await prisma.bookingPayment.findFirst({
      where: { id: checkoutId },
      include: { items: true },
    })) as BookingPaymentRow | null;

    if (!payment) {
      throw new BadRequestException('Checkout not found');
    }

    if (payment.status === 'paid') {
      return;
    }

    const dedupeId = `${webhookEventId}:${invoiceId}`;
    const existing = await prisma.bookingPaymentEvent.findUnique({
      where: { id: dedupeId },
    });
    if (existing && ['paid', 'released'].includes(payment.status)) {
      return;
    }
    const shouldRecordEvent = !existing;

    if (!detailsAlreadyVerified) {
      await getPaymentDetails(invoiceId);
    }

    if (payment.status === 'superseded') {
      await updatePayment(paymentId, { OperationType: 'RELEASE' });
      await prisma.bookingPayment.update({
        where: { id: payment.id },
        data: { status: 'released', releasedAt: new Date() },
      });
      if (shouldRecordEvent) {
        await recordPaymentEvent(prisma, {
          id: dedupeId,
          bookingPaymentId: payment.id,
          eventReference: webhookEventId,
          rawPayload,
        });
      }
      return;
    }

    await updatePayment(paymentId, {
      OperationType: 'CAPTURE',
      Amount: payment.totalAmount,
    });

    const bookingIds = payment.items.map((i) => i.bookingId);
    await prisma.booking.updateMany({
      where: { id: { in: bookingIds } },
      data: {
        status: 'pending',
        paymentStatus: 'paid',
        paymentGateway: 'myfatoorah',
        paymentId,
      },
    });

    await prisma.bookingPayment.update({
      where: { id: payment.id },
      data: {
        status: 'paid',
        paidAt: new Date(),
        myFatoorahInvoiceId: invoiceId,
        myFatoorahPaymentId: paymentId,
      },
    });

    if (shouldRecordEvent) {
      await recordPaymentEvent(prisma, {
        id: dedupeId,
        bookingPaymentId: payment.id,
        eventReference: webhookEventId,
        rawPayload,
      });
    }
  }

  private async resolveGatewayPayment(
    payload: unknown,
    source: string,
    checkoutId?: string,
    paymentIdOverride?: string,
  ): Promise<{ status: string; checkoutId: string | null }> {
    let details: any = payload;
    let paymentId = gatewayId(paymentIdOverride) || this.extractPaymentId(payload);
    let invoiceId = this.extractInvoiceId(payload);

    if (paymentId) {
      details = await getPaymentDetails(paymentId, 'PaymentId');
    } else if (invoiceId) {
      details = await getPaymentDetails(invoiceId, 'InvoiceId');
    }

    paymentId = this.extractPaymentId(details) || paymentId;
    invoiceId = this.extractInvoiceId(details) || invoiceId;
    const resolvedCheckoutId =
      checkoutId || this.extractCheckoutId(details) || this.extractCheckoutId(payload);

    const payment = await this.findCheckoutForGatewayRef({
      checkoutId: resolvedCheckoutId,
      invoiceId,
      paymentId,
    });

    if (!payment) {
      return {
        status: this.isMyFatoorahFailed(details) ? 'failed' : 'pending',
        checkoutId: resolvedCheckoutId ?? null,
      };
    }

    if (this.isMyFatoorahFailed(details)) {
      const prisma = await getDelegates();
      await prisma.bookingPayment.update({
        where: { id: payment.id },
        data: {
          status: 'failed',
          lastError: this.collectStatuses(details).join(',') || 'Payment failed',
        },
      });
      return { status: 'failed', checkoutId: payment.id };
    }

    if (this.isMyFatoorahAccepted(details)) {
      const finalInvoiceId = invoiceId || payment.myFatoorahInvoiceId;
      const finalPaymentId = paymentId || payment.myFatoorahPaymentId;
      if (!finalInvoiceId || !finalPaymentId) {
        return { status: payment.status, checkoutId: payment.id };
      }

      await this.reconcilePayment(
        finalInvoiceId,
        finalPaymentId,
        payment.id,
        this.collectStatuses(details).join(','),
        `${source}:${finalPaymentId || finalInvoiceId}`,
        details,
        true,
      );
      return { status: payment.status === 'superseded' ? 'released' : 'paid', checkoutId: payment.id };
    }

    return { status: payment.status, checkoutId: payment.id };
  }

  private async findCheckoutForGatewayRef(input: {
    checkoutId?: string | null;
    invoiceId?: string | null;
    paymentId?: string | null;
  }): Promise<BookingPaymentRow | null> {
    const prisma = await getDelegates();
    if (input.checkoutId) {
      const byId = (await prisma.bookingPayment.findFirst({
        where: { id: input.checkoutId },
        include: { items: true },
      })) as BookingPaymentRow | null;
      if (byId) {
        if (
          input.invoiceId &&
          byId.myFatoorahInvoiceId &&
          byId.myFatoorahInvoiceId !== input.invoiceId
        ) {
          return null;
        }
        if (
          input.paymentId &&
          byId.myFatoorahPaymentId &&
          byId.myFatoorahPaymentId !== input.paymentId
        ) {
          return null;
        }
        return byId;
      }
    }

    const OR = [
      input.invoiceId ? { myFatoorahInvoiceId: input.invoiceId } : null,
      input.paymentId ? { myFatoorahPaymentId: input.paymentId } : null,
    ].filter(Boolean);
    if (!OR.length) return null;

    return (await prisma.bookingPayment.findFirst({
      where: { OR },
      include: { items: true },
    })) as BookingPaymentRow | null;
  }

  private extractCheckoutId(payload: any): string | null {
    const value =
      payload?.Data?.CustomerReference ||
      payload?.Data?.UserDefinedField ||
      payload?.CustomerReference ||
      payload?.UserDefinedField ||
      payload?.Data?.customerReference ||
      payload?.userDefinedField;
    return gatewayId(value);
  }

  private extractInvoiceId(payload: any): string | null {
    const value =
      payload?.Data?.InvoiceId ||
      payload?.Data?.InvoiceID ||
      payload?.Data?.Invoice?.Id ||
      payload?.Data?.Invoice?.InvoiceId ||
      payload?.InvoiceId ||
      payload?.InvoiceID ||
      payload?.invoiceId;
    return gatewayId(value);
  }

  private extractPaymentId(payload: any): string | null {
    const tx = Array.isArray(payload?.Data?.InvoiceTransactions)
      ? payload.Data.InvoiceTransactions.find((item: any) => item?.PaymentId || item?.PaymentID)
      : null;
    const value =
      payload?.Data?.PaymentId ||
      payload?.Data?.PaymentID ||
      payload?.Data?.Transaction?.PaymentId ||
      payload?.Data?.Transaction?.PaymentID ||
      tx?.PaymentId ||
      tx?.PaymentID ||
      payload?.PaymentId ||
      payload?.PaymentID ||
      payload?.paymentId;
    return gatewayId(value);
  }

  private collectStatuses(payload: any): string[] {
    const statuses = [
      payload?.Data?.InvoiceStatus,
      payload?.Data?.TransactionStatus,
      payload?.Data?.Invoice?.Status,
      payload?.Data?.Transaction?.Status,
      payload?.InvoiceStatus,
      payload?.TransactionStatus,
      ...(payload?.Data?.InvoiceTransactions || []).map((tx: any) => tx.TransactionStatus),
    ];
    return statuses.filter(Boolean).map((status) => String(status).toUpperCase());
  }

  private isMyFatoorahAccepted(payload: any): boolean {
    return this.collectStatuses(payload).some((status) =>
      ['AUTHORIZE', 'AUTHORIZED', 'PAID', 'SUCCESS', 'SUCCSS', 'CAPTURED'].includes(status),
    );
  }

  private isMyFatoorahFailed(payload: any): boolean {
    return this.collectStatuses(payload).some((status) =>
      ['FAILED', 'CANCELED', 'CANCELLED', 'EXPIRED'].includes(status),
    );
  }
}
