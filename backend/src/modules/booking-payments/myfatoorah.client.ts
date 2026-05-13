import crypto from 'crypto';

/**
 * MyFatoorah API client.
 *
 * The app uses hosted invoice links for mobile checkout. SendPayment returns
 * InvoiceURL, while older booking code expects PaymentURL, so responses are
 * normalized to expose both names.
 */

type MyFatoorahKeyType = 'InvoiceId' | 'PaymentId';

const DEFAULT_BASE_URL = 'https://api.myfatoorah.com';
const SANDBOX_BASE_URL = 'https://apitest.myfatoorah.com';

function baseUrl(): string {
  const explicit = process.env.MYFATOORAH_API_BASE_URL?.trim();
  if (explicit) return explicit.replace(/\/$/, '');
  const env = (process.env.MYFATOORAH_ENV || '').toLowerCase();
  if (env === 'sandbox' || env === 'test' || env === 'development') {
    return SANDBOX_BASE_URL;
  }
  return DEFAULT_BASE_URL;
}

/** Same secret MyFatoorah calls "API Key" in the portal — support all common env names. */
function apiToken(): string {
  const token =
    process.env.MYFATOORAH_API_TOKEN?.trim() ||
    process.env.MYFATOORAH_TOKEN?.trim() ||
    process.env.MYFATOORAH_API_KEY?.trim();
  if (!token) {
    throw new Error(
      'MyFatoorah API token is not configured (set MYFATOORAH_API_KEY, MYFATOORAH_API_TOKEN, or MYFATOORAH_TOKEN)',
    );
  }
  return token;
}

/**
 * MyFatoorah expects ExpiryDate as `Y-m-dTH:i:s` in a Gulf time zone.
 * ISO-8601 strings with `Z` are parsed incorrectly server-side and trigger "Expire Date should be in Future!".
 */
function formatExpiryDateForMyFatoorah(date: Date): string {
  const timeZone = process.env.MYFATOORAH_EXPIRY_TIMEZONE?.trim() || 'Asia/Riyadh';
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).formatToParts(date);

  const pick = (type: Intl.DateTimeFormatPart['type']) =>
    parts.find((p) => p.type === type)?.value ?? '';

  const year = pick('year');
  const month = pick('month').padStart(2, '0');
  const day = pick('day').padStart(2, '0');
  const hour = pick('hour').padStart(2, '0');
  const minute = pick('minute').padStart(2, '0');
  const second = (pick('second') || '0').padStart(2, '0');

  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

function normalizeExpiryDateForApi(paymentExpiry: unknown): string | undefined {
  if (paymentExpiry === undefined || paymentExpiry === null || paymentExpiry === '') {
    return undefined;
  }
  const date =
    paymentExpiry instanceof Date
      ? paymentExpiry
      : new Date(
          typeof paymentExpiry === 'string' || typeof paymentExpiry === 'number'
            ? paymentExpiry
            : NaN,
        );
  if (Number.isNaN(date.getTime())) {
    return undefined;
  }
  return formatExpiryDateForMyFatoorah(date);
}

async function postToMyFatoorah(path: string, body: Record<string, unknown>) {
  const response = await fetch(`${baseUrl()}${path}`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${apiToken()}`,
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(body),
  });

  const payload = await response.json().catch(() => null);
  if (!response.ok || payload?.IsSuccess === false) {
    const validationErrors = Array.isArray(payload?.ValidationErrors)
      ? payload.ValidationErrors
        .map((item: any) => item?.Error || item?.Name || JSON.stringify(item))
        .filter(Boolean)
      : [];
    const message =
      validationErrors.length > 0
        ? validationErrors.join('; ')
        : payload?.Message || `MyFatoorah request failed with ${response.status}`;
    throw new Error(message);
  }
  return payload;
}

function normalizePaymentResponse(payload: any): any {
  const data = payload?.Data || {};
  const paymentUrl = data.PaymentURL || data.InvoiceURL || data.InvoiceUrl || null;
  return {
    ...payload,
    Data: {
      ...data,
      PaymentURL: paymentUrl,
      InvoiceURL: data.InvoiceURL || paymentUrl,
      PaymentId: data.PaymentId || data.PaymentID || null,
      InvoiceId: data.InvoiceId || data.InvoiceID || null,
    },
  };
}

export async function createPayment(input: Record<string, unknown>): Promise<unknown> {
  const paymentExpiry = input.PaymentExpiry || input.ExpiryDate;
  const expiryDate = normalizeExpiryDateForApi(paymentExpiry);
  const invoiceValue = input.InvoiceValue ?? input.totalAmount;
  const currency = input.DisplayCurrencyIso ?? input.CurrencyIso ?? input.Currency;
  const processingDetails = input.OperationType
    ? { AutoCapture: input.OperationType !== 'AUTHORIZE' }
    : input.ProcessingDetails;

  const body: Record<string, unknown> = {
    CustomerName: input.CustomerName || 'Customer',
    CustomerEmail: input.CustomerEmail,
    NotificationOption: input.NotificationOption || 'LNK',
    InvoiceValue: invoiceValue,
    DisplayCurrencyIso: currency,
    CallBackUrl: input.CallBackUrl,
    ErrorUrl: input.ErrorUrl,
    Language: input.Language || 'en',
    CustomerReference: input.CustomerReference || input.ExternalIdentifier,
    UserDefinedField: input.UserDefinedField || input.ExternalIdentifier,
    ExpiryDate: expiryDate,
    InvoiceItems: input.InvoiceItems,
    ProcessingDetails: processingDetails,
    WebhookUrl: input.WebhookUrl,
  };

  Object.keys(body).forEach((key) => {
    if (body[key] === undefined || body[key] === null || body[key] === '') {
      delete body[key];
    }
  });

  return normalizePaymentResponse(await postToMyFatoorah('/v2/SendPayment', body));
}

export async function getPaymentDetails(
  key: string,
  keyType: MyFatoorahKeyType = 'InvoiceId',
): Promise<unknown> {
  const endpoint = process.env.MYFATOORAH_GET_PAYMENT_STATUS_PATH || '/v2/GetPaymentStatus';
  return postToMyFatoorah(endpoint, { Key: key, KeyType: keyType });
}

export async function updatePayment(
  paymentId: string,
  input: Record<string, unknown>,
): Promise<unknown> {
  const endpoint = process.env.MYFATOORAH_UPDATE_PAYMENT_STATUS_PATH || '/v2/UpdatePaymentStatus';
  /**
   * v2 UpdatePaymentStatus requires `Operation`: "Capture" | "Release" (see MyFatoorah docs).
   * Callers may still pass `OperationType`: "CAPTURE" | "RELEASE" from Nest code — map here.
   */
  const raw = String(input.Operation ?? input.OperationType ?? '').trim();
  const upper = raw.toUpperCase();
  let operation: string | undefined;
  if (upper === 'CAPTURE') operation = 'Capture';
  else if (upper === 'RELEASE') operation = 'Release';
  else if (raw === 'Capture' || raw === 'Release') operation = raw;

  if (!operation) {
    throw new Error('updatePayment requires Operation or OperationType CAPTURE|RELEASE');
  }

  const body: Record<string, unknown> = {
    Key: paymentId,
    KeyType: 'PaymentId',
    Operation: operation,
  };
  if (input.Amount !== undefined && input.Amount !== null && input.Amount !== '') {
    body.Amount = input.Amount;
  }

  Object.keys(body).forEach((key) => {
    if (body[key] === undefined || body[key] === null || body[key] === '') {
      delete body[key];
    }
  });

  return postToMyFatoorah(endpoint, body);
}

function flattenSignatureFields(value: unknown, prefix = ''): Array<[string, string]> {
  if (value === null || value === undefined) {
    return [[prefix, '']];
  }
  if (Array.isArray(value)) {
    return [[prefix, JSON.stringify(value)]];
  }
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) => {
      const nextKey = prefix ? `${prefix}.${key}` : key;
      return flattenSignatureFields(child, nextKey);
    });
  }
  return [[prefix, String(value)]];
}

export function verifyWebhookSignature(payload: unknown, signature: string): boolean {
  const secret = getWebhookSecret();
  if (!secret || !signature) return false;

  const data = flattenSignatureFields(payload)
    .filter(([key]) => key && !key.toLowerCase().includes('signature'))
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([key, value]) => `${key}=${value}`)
    .join(',');

  const digest = crypto.createHmac('sha256', secret).update(data, 'utf8').digest('base64');
  const a = Buffer.from(digest);
  const b = Buffer.from(signature);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function getWebhookSecret(): string {
  return process.env.MYFATOORAH_WEBHOOK_SECRET ?? '';
}
