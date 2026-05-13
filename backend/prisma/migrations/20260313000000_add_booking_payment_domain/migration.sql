-- CreateTable
CREATE TABLE "booking_payments" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "gateway" TEXT NOT NULL DEFAULT 'myfatoorah',
    "status" TEXT NOT NULL DEFAULT 'pending',
    "currency" TEXT NOT NULL,
    "totalAmount" DOUBLE PRECISION NOT NULL,
    "myFatoorahInvoiceId" TEXT,
    "myFatoorahPaymentId" TEXT,
    "paymentUrl" TEXT,
    "expiresAt" TIMESTAMP(3),
    "idempotencyKey" TEXT,
    "lastError" TEXT,
    "paidAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "booking_payments_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "booking_payment_items" (
    "id" TEXT NOT NULL,
    "bookingPaymentId" TEXT NOT NULL,
    "bookingId" TEXT NOT NULL,
    "amountSnapshot" DOUBLE PRECISION NOT NULL,

    CONSTRAINT "booking_payment_items_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "booking_payment_events" (
    "id" TEXT NOT NULL,
    "bookingPaymentId" TEXT NOT NULL,
    "eventReference" TEXT NOT NULL,
    "eventCode" INTEGER,
    "rawPayload" JSONB,
    "processedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "booking_payment_events_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "booking_payments_idempotencyKey_key" ON "booking_payments"("idempotencyKey");

-- CreateIndex
CREATE INDEX "booking_payments_userId_idx" ON "booking_payments"("userId");

-- CreateIndex
CREATE INDEX "booking_payments_status_idx" ON "booking_payments"("status");

-- CreateIndex
CREATE INDEX "booking_payments_myFatoorahPaymentId_idx" ON "booking_payments"("myFatoorahPaymentId");

-- CreateIndex
CREATE UNIQUE INDEX "booking_payment_items_bookingPaymentId_bookingId_key" ON "booking_payment_items"("bookingPaymentId", "bookingId");

-- CreateIndex
CREATE INDEX "booking_payment_items_bookingPaymentId_idx" ON "booking_payment_items"("bookingPaymentId");

-- CreateIndex
CREATE INDEX "booking_payment_items_bookingId_idx" ON "booking_payment_items"("bookingId");

-- CreateIndex
CREATE UNIQUE INDEX "booking_payment_events_bookingPaymentId_eventReference_key" ON "booking_payment_events"("bookingPaymentId", "eventReference");

-- CreateIndex
CREATE INDEX "booking_payment_events_bookingPaymentId_idx" ON "booking_payment_events"("bookingPaymentId");

-- CreateIndex
CREATE INDEX "booking_payment_events_eventReference_idx" ON "booking_payment_events"("eventReference");

-- AddForeignKey
ALTER TABLE "booking_payments" ADD CONSTRAINT "booking_payments_userId_fkey" FOREIGN KEY ("userId") REFERENCES "users"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_payment_items" ADD CONSTRAINT "booking_payment_items_bookingPaymentId_fkey" FOREIGN KEY ("bookingPaymentId") REFERENCES "booking_payments"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_payment_items" ADD CONSTRAINT "booking_payment_items_bookingId_fkey" FOREIGN KEY ("bookingId") REFERENCES "bookings"("id") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "booking_payment_events" ADD CONSTRAINT "booking_payment_events_bookingPaymentId_fkey" FOREIGN KEY ("bookingPaymentId") REFERENCES "booking_payments"("id") ON DELETE CASCADE ON UPDATE CASCADE;
