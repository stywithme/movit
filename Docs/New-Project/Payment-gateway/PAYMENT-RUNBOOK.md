# Booking Payment Runbook

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `MYFATOORAH_API_KEY` | API key from MyFatoorah portal | Yes |
| `MYFATOORAH_WEBHOOK_SECRET` | Webhook V2 secret from portal | Yes |
| `MYFATOORAH_ENV` | `sandbox` or `production` | No (default: sandbox) |
| `API_BASE_URL` or `PAYMENT_BASE_URL` | Base URL for webhook/redirect (e.g. `https://api.example.com`) | Yes for production |
| `MOBILE_APP_SCHEME` | Deep link scheme for return (e.g. `poseapp`) | No (default: poseapp) |

## Sandbox vs Live

- **Sandbox**: `MYFATOORAH_ENV=sandbox` (default). Uses `apitest.myfatoorah.com`.
- **Live**: `MYFATOORAH_ENV=production`. Uses `api.myfatoorah.com` (region-specific URLs for AE/SA/QA/EG).

## Webhook Setup

1. In MyFatoorah portal: **Integration Settings → Webhook Settings**
2. Enable Webhook Feature
3. Set Endpoint URL: `https://your-api.com/api/payments/myfatoorah/webhook`
4. Select Webhook Version: **V2**
5. Enable Secure Key and copy to `MYFATOORAH_WEBHOOK_SECRET`

## Webhook Secret Rotation

1. Generate new secret in MyFatoorah portal
2. Update `MYFATOORAH_WEBHOOK_SECRET` in backend env
3. Deploy backend
4. Old secret stops working; ensure no in-flight webhooks

## Stuck Checkouts

If a checkout is `pending_status_update` (verification failed after retries):

1. Query `booking_payments` for status = `pending_status_update`
2. Get `myFatoorahPaymentId` from the row
3. Call MyFatoorah `GET /v3/payments/{paymentId}` manually
4. If `Transaction.Status === SUCCESS`, update `booking_payments` to status `paid` and linked `bookings` to status `pending` in a transaction

## Manual Reconciliation

For disputed or stuck payments:

```sql
-- Find payments stuck in pending_status_update
SELECT id, "myFatoorahPaymentId", "myFatoorahInvoiceId", status, "lastError"
FROM booking_payments
WHERE status = 'pending_status_update';
```

Then verify via MyFatoorah API or portal and update accordingly.
