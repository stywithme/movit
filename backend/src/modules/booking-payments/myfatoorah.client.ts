/**
 * MyFatoorah API client (HTTP calls). Implementations are mocked in unit tests.
 */

export async function createPayment(
  _input: Record<string, unknown>,
): Promise<unknown> {
  throw new Error('MyFatoorah createPayment is not configured');
}

export async function getPaymentDetails(_invoiceId: string): Promise<unknown> {
  throw new Error('MyFatoorah getPaymentDetails is not configured');
}

export async function updatePayment(
  _paymentId: string,
  _input: Record<string, unknown>,
): Promise<unknown> {
  throw new Error('MyFatoorah updatePayment is not configured');
}

export function verifyWebhookSignature(
  _payload: unknown,
  _signature: string,
): boolean {
  return false;
}

export function getWebhookSecret(): string {
  return process.env.MYFATOORAH_WEBHOOK_SECRET ?? '';
}
