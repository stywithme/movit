import {
  BadRequestException,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import {
  createPayment,
  getPaymentDetails,
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

@Injectable()
export class BookingPaymentService {
  async createCheckout(
    userId: string,
    dto: { bookingIds: string[] },
  ): Promise<{
    checkoutId: string;
    paymentUrl: string | null;
    totalAmount: number;
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

    const currencyRow = await prisma.system.findUnique({
      where: { key: 'currency' },
    });
    const currency = currencyRow?.value ?? 'EGP';

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
          totalAmount: cp.totalAmount,
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
      const apiResult = (await createPayment({
        OperationType: 'AUTHORIZE',
        PaymentExpiry: expiresAt.toISOString(),
        InvoiceValue: totalAmount,
        CurrencyIso: currency,
        CustomerName: bookings[0]?.user?.name ?? 'Customer',
        CustomerEmail: bookings[0]?.user?.email ?? '',
        ExternalIdentifier: reserved.id,
      })) as {
        IsSuccess: boolean;
        Data?: {
          InvoiceId?: string;
          PaymentId?: string;
          PaymentURL?: string;
        };
      };

      if (!apiResult?.IsSuccess || !apiResult.Data) {
        throw new BadRequestException('Payment gateway error');
      }

      const finalCheckout = (await tx.bookingPayment.update({
        where: { id: reserved.id },
        data: {
          status: 'pending',
          paymentUrl: apiResult.Data.PaymentURL ?? null,
          myFatoorahInvoiceId: apiResult.Data.InvoiceId ?? null,
          myFatoorahPaymentId: apiResult.Data.PaymentId ?? null,
          expiresAt,
        },
      })) as BookingPaymentRow;

      return {
        checkoutId: finalCheckout.id,
        paymentUrl: finalCheckout.paymentUrl,
        totalAmount: finalCheckout.totalAmount,
        bookingCount: bookingIds.length,
      };
    });
  }

  async handleWebhook(payload: unknown, signature: string): Promise<void> {
    if (!verifyWebhookSignature(payload, signature)) {
      throw new BadRequestException('Invalid webhook signature');
    }
  }

  async reconcilePayment(
    invoiceId: string,
    paymentId: string,
    checkoutId: string,
    _gatewayTransactionStatus: string,
    webhookEventId: string,
  ): Promise<void> {
    const prisma = await getDelegates();

    const payment = (await prisma.bookingPayment.findFirst({
      where: { id: checkoutId },
    })) as BookingPaymentRow | null;

    if (!payment) {
      throw new BadRequestException('Checkout not found');
    }

    const dedupeId = `${webhookEventId}:${invoiceId}`;
    const existing = await prisma.bookingPaymentEvent.findUnique({
      where: { id: dedupeId },
    });
    if (existing) {
      return;
    }

    await prisma.bookingPaymentEvent.create({
      data: {
        id: dedupeId,
        bookingPaymentId: payment.id,
        kind: 'webhook',
        payload: {},
      },
    });

    await getPaymentDetails(invoiceId);

    if (payment.status === 'superseded') {
      await updatePayment(paymentId, { OperationType: 'RELEASE' });
      await prisma.bookingPayment.update({
        where: { id: payment.id },
        data: { releasedAt: new Date() },
      });
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
  }
}
