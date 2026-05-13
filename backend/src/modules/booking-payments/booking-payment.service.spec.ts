/**
 * BookingPaymentService tests
 * Covers: checkout reservation/reuse, authorize/capture flow, and release of superseded revisions.
 */

/* eslint-disable @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-call, @typescript-eslint/no-unsafe-member-access, @typescript-eslint/no-unsafe-return, @typescript-eslint/require-await */

import { Test, TestingModule } from '@nestjs/testing';
import { BadRequestException, ForbiddenException } from '@nestjs/common';
import { BookingPaymentService } from './booking-payment.service';

// Mock getPrisma
const mockPrisma = {
  booking: {
    findMany: jest.fn(),
    findFirst: jest.fn(),
    findUnique: jest.fn(),
    updateMany: jest.fn(),
  },
  system: {
    findUnique: jest.fn(),
  },
  bookingPayment: {
    findUnique: jest.fn(),
    findFirst: jest.fn(),
    findMany: jest.fn(),
    create: jest.fn(),
    update: jest.fn(),
    updateMany: jest.fn(),
  },
  bookingPaymentEvent: {
    findUnique: jest.fn(),
    create: jest.fn(),
  },
  $transaction: jest.fn((fn) => fn(mockPrisma)),
};

jest.mock('@/lib/prisma/client', () => ({
  getPrisma: () => Promise.resolve(mockPrisma),
}));

// Mock myfatoorah client
jest.mock('./myfatoorah.client', () => ({
  createPayment: jest.fn(),
  getPaymentDetails: jest.fn(),
  updatePayment: jest.fn(),
  verifyWebhookSignature: jest.fn(),
  getWebhookSecret: jest.fn(() => 'test-secret'),
}));

import {
  createPayment,
  getPaymentDetails,
  updatePayment,
  verifyWebhookSignature,
} from './myfatoorah.client';

describe('BookingPaymentService', () => {
  let service: BookingPaymentService;

  const userId = 'user-1';
  const now = new Date('2026-03-21T12:00:00.000Z');
  const booking1 = {
    id: 'b1',
    userId,
    status: 'payment_pending',
    deletedAt: null,
    amount: 50,
    user: { id: userId, name: 'Test', email: 'test@test.com' },
  };
  const booking2 = {
    id: 'b2',
    userId,
    status: 'payment_pending',
    deletedAt: null,
    amount: 30,
    user: { id: userId, name: 'Test', email: 'test@test.com' },
  };
  const makePayment = (overrides: Record<string, unknown> = {}) => ({
    id: 'cp-1',
    userId,
    gateway: 'myfatoorah',
    revision: 1,
    status: 'pending',
    currency: 'SAR',
    totalAmount: 50,
    myFatoorahInvoiceId: 'inv-1',
    myFatoorahPaymentId: 'pay-1',
    paymentUrl: 'https://pay.example.com/xxx',
    expiresAt: new Date(now.getTime() + 60 * 60 * 1000),
    idempotencyKey: null,
    operationType: 'AUTHORIZE',
    authorizedAt: null,
    releasedAt: null,
    supersededAt: null,
    supersededById: null,
    lastError: null,
    paidAt: null,
    createdAt: now,
    updatedAt: now,
    items: [{ bookingId: 'b1', amountSnapshot: 50 }],
    ...overrides,
  });

  beforeEach(async () => {
    jest.clearAllMocks();
    mockPrisma.system.findUnique.mockResolvedValue({ value: 'SAR' });
    mockPrisma.bookingPayment.updateMany.mockResolvedValue({ count: 0 });
    mockPrisma.booking.updateMany.mockResolvedValue({ count: 0 });
    mockPrisma.$transaction.mockImplementation(async (fn) => fn(mockPrisma));
    const module: TestingModule = await Test.createTestingModule({
      providers: [BookingPaymentService],
    }).compile();
    service = module.get<BookingPaymentService>(BookingPaymentService);
  });

  describe('createCheckout', () => {
    it('rejects when booking belongs to another user', async () => {
      mockPrisma.booking.findMany.mockResolvedValue([
        { ...booking1, userId: 'other-user' },
      ]);
      await expect(
        service.createCheckout(userId, { bookingIds: ['b1'] }),
      ).rejects.toThrow(ForbiddenException);
    });

    it('rejects when booking is not payment_pending', async () => {
      mockPrisma.booking.findMany.mockResolvedValue([
        { ...booking1, status: 'pending' },
      ]);
      await expect(
        service.createCheckout(userId, { bookingIds: ['b1'] }),
      ).rejects.toThrow(BadRequestException);
    });

    it('rejects when total amount is zero', async () => {
      mockPrisma.booking.findMany.mockResolvedValue([
        { ...booking1, amount: 0 },
      ]);
      await expect(
        service.createCheckout(userId, { bookingIds: ['b1'] }),
      ).rejects.toThrow(BadRequestException);
    });

    it('reuses the current open checkout for the same selection', async () => {
      const currentCheckout = makePayment();
      mockPrisma.booking.findMany.mockResolvedValue([booking1]);
      mockPrisma.bookingPayment.findMany.mockResolvedValue([currentCheckout]);

      const result = await service.createCheckout(userId, {
        bookingIds: ['b1'],
      });

      expect(createPayment).not.toHaveBeenCalled();
      expect(result.checkoutId).toBe(currentCheckout.id);
      expect(result.paymentUrl).toBe(currentCheckout.paymentUrl);
    });

    it('creates an authorize checkout and supersedes older pending selections', async () => {
      const reservedCheckout = makePayment({
        id: 'cp-2',
        revision: 3,
        status: 'creating',
        totalAmount: 80,
        paymentUrl: null,
        myFatoorahInvoiceId: null,
        myFatoorahPaymentId: null,
        items: [
          { bookingId: 'b1', amountSnapshot: 50 },
          { bookingId: 'b2', amountSnapshot: 30 },
        ],
      });
      const finalCheckout = {
        ...reservedCheckout,
        status: 'pending',
        paymentUrl: 'https://pay.example.com/new',
        myFatoorahInvoiceId: 'inv-2',
        myFatoorahPaymentId: 'pay-2',
      };

      mockPrisma.booking.findMany
        .mockResolvedValueOnce([booking1, booking2])
        .mockResolvedValueOnce([
          { id: 'b1', status: 'payment_pending', deletedAt: null },
          { id: 'b2', status: 'payment_pending', deletedAt: null },
        ]);
      mockPrisma.bookingPayment.findMany.mockResolvedValue([]);
      mockPrisma.bookingPayment.findFirst
        .mockResolvedValueOnce({ revision: 2 })
        .mockResolvedValueOnce(null);
      (createPayment as jest.Mock).mockResolvedValue({
        IsSuccess: true,
        Data: {
          InvoiceId: 6762796,
          PaymentId: 987654,
          PaymentURL: 'https://pay.example.com/new',
        },
      });
      mockPrisma.bookingPayment.create.mockResolvedValue(reservedCheckout);
      mockPrisma.bookingPayment.update.mockResolvedValue(finalCheckout);

      const result = await service.createCheckout(userId, {
        bookingIds: ['b1', 'b2'],
      });

      expect(createPayment).toHaveBeenCalledWith(
        expect.objectContaining({
          OperationType: 'AUTHORIZE',
          PaymentExpiry: expect.any(String),
        }),
      );
      expect(
        mockPrisma.bookingPayment.updateMany.mock.calls.some(
          ([args]: any[]) => args?.data?.status === 'superseded',
        ),
      ).toBe(true);
      expect(mockPrisma.bookingPayment.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            myFatoorahInvoiceId: '6762796',
            myFatoorahPaymentId: '987654',
          }),
        }),
      );
      expect(result.checkoutId).toBe('cp-2');
      expect(result.totalAmount).toBe(80);
      expect(result.bookingCount).toBe(2);
    });
  });

  describe('handleWebhook', () => {
    it('rejects invalid signature', async () => {
      (verifyWebhookSignature as jest.Mock).mockReturnValue(false);

      await expect(
        service.handleWebhook({ Event: {}, Data: {} }, 'bad-sig'),
      ).rejects.toThrow(BadRequestException);
    });
  });

  describe('reconcilePayment', () => {
    it('captures the active authorized checkout and updates bookings', async () => {
      const payment = makePayment();
      mockPrisma.bookingPayment.findFirst.mockResolvedValue(payment);
      mockPrisma.bookingPaymentEvent.findUnique.mockResolvedValue(null);
      mockPrisma.bookingPaymentEvent.create.mockResolvedValue({});
      (getPaymentDetails as jest.Mock).mockResolvedValue({
        IsSuccess: true,
        Data: {
          Invoice: {
            Id: 'inv-1',
            Status: 'PENDING',
            ExternalIdentifier: 'cp-1',
          },
          Transaction: { Status: 'AUTHORIZE', PaymentId: 'pay-1' },
        },
      });
      (updatePayment as jest.Mock).mockResolvedValue({
        IsSuccess: true,
        Data: {
          Invoice: { Id: 'inv-1', Status: 'PAID' },
          Transaction: { Status: 'SUCCESS', PaymentId: 'pay-1' },
        },
      });
      mockPrisma.bookingPayment.update.mockResolvedValue({});

      await service.reconcilePayment(
        'inv-1',
        'pay-1',
        'cp-1',
        'AUTHORIZE',
        'wh-1',
      );

      expect(updatePayment).toHaveBeenCalledWith('pay-1', {
        OperationType: 'CAPTURE',
        Amount: 50,
      });
      expect(mockPrisma.booking.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            status: 'pending',
            paymentStatus: 'paid',
            paymentGateway: 'myfatoorah',
          }),
        }),
      );
    });

    it('marks an already settled checkout as paid without capturing again', async () => {
      const payment = makePayment();
      mockPrisma.bookingPayment.findFirst.mockResolvedValue(payment);
      mockPrisma.bookingPaymentEvent.findUnique.mockResolvedValue(null);
      mockPrisma.bookingPaymentEvent.create.mockResolvedValue({});
      mockPrisma.bookingPayment.update.mockResolvedValue({});

      await service.reconcilePayment(
        'inv-1',
        'pay-1',
        'cp-1',
        'PAID',
        'result:pay-1',
        {
          IsSuccess: true,
          Data: {
            InvoiceStatus: 'Paid',
            InvoiceTransactions: [{ TransactionStatus: 'Succss', PaymentId: 'pay-1' }],
          },
        },
        true,
      );

      expect(updatePayment).not.toHaveBeenCalled();
      expect(mockPrisma.booking.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            paymentStatus: 'paid',
            paymentGateway: 'myfatoorah',
          }),
        }),
      );
    });

    it('releases a superseded authorized checkout instead of capturing it', async () => {
      const payment = makePayment({
        status: 'superseded',
        supersededAt: now,
      });
      mockPrisma.bookingPayment.findFirst.mockResolvedValue(payment);
      mockPrisma.bookingPaymentEvent.findUnique.mockResolvedValue(null);
      mockPrisma.bookingPaymentEvent.create.mockResolvedValue({});
      (getPaymentDetails as jest.Mock).mockResolvedValue({
        IsSuccess: true,
        Data: {
          Invoice: {
            Id: 'inv-1',
            Status: 'PENDING',
            ExternalIdentifier: 'cp-1',
          },
          Transaction: { Status: 'AUTHORIZE', PaymentId: 'pay-1' },
        },
      });
      (updatePayment as jest.Mock).mockResolvedValue({
        IsSuccess: true,
        Data: {
          Invoice: { Id: 'inv-1', Status: 'PENDING' },
          Transaction: { Status: 'CANCELED', PaymentId: 'pay-1' },
        },
      });
      mockPrisma.bookingPayment.update.mockResolvedValue({});

      await service.reconcilePayment(
        'inv-1',
        'pay-1',
        'cp-1',
        'AUTHORIZE',
        'wh-2',
      );

      expect(updatePayment).toHaveBeenCalledWith('pay-1', {
        OperationType: 'RELEASE',
      });
      expect(mockPrisma.booking.updateMany).not.toHaveBeenCalled();
    });

    it('continues reconciliation if a previous attempt recorded the event before failing', async () => {
      const payment = makePayment({ status: 'pending' });
      mockPrisma.bookingPayment.findFirst.mockResolvedValue(payment);
      mockPrisma.bookingPaymentEvent.findUnique.mockResolvedValue({ id: 'wh-3:inv-1' });
      (getPaymentDetails as jest.Mock).mockResolvedValue({
        IsSuccess: true,
        Data: {
          Invoice: { Id: 'inv-1', Status: 'PENDING', ExternalIdentifier: 'cp-1' },
          Transaction: { Status: 'AUTHORIZE', PaymentId: 'pay-1' },
        },
      });
      (updatePayment as jest.Mock).mockResolvedValue({ IsSuccess: true });
      mockPrisma.bookingPayment.update.mockResolvedValue({});

      await service.reconcilePayment('inv-1', 'pay-1', 'cp-1', 'AUTHORIZE', 'wh-3');

      expect(updatePayment).toHaveBeenCalledWith('pay-1', {
        OperationType: 'CAPTURE',
        Amount: 50,
      });
      expect(mockPrisma.booking.updateMany).toHaveBeenCalled();
      expect(mockPrisma.bookingPaymentEvent.create).not.toHaveBeenCalled();
    });
  });
});
