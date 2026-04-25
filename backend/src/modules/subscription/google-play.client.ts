import crypto from 'crypto';
import { readFileSync } from 'fs';

export type GooglePlaySubscriptionVerification = {
    raw: any;
    packageName: string;
    productId: string | null;
    orderId: string | null;
    purchaseToken: string;
    subscriptionState: string;
    startTime: string | null;
    expiryTime: string | null;
    autoRenewing: boolean;
    linkedPurchaseToken: string | null;
    isEntitlementActive: boolean;
};

type ServiceAccount = {
    client_email: string;
    private_key: string;
    token_uri?: string;
};

const ANDROID_PUBLISHER_SCOPE = 'https://www.googleapis.com/auth/androidpublisher';
const TOKEN_AUDIENCE = 'https://oauth2.googleapis.com/token';

let cachedAccessToken: { token: string; expiresAt: number } | null = null;

function base64Url(input: Buffer | string): string {
    return Buffer.from(input)
        .toString('base64')
        .replace(/=/g, '')
        .replace(/\+/g, '-')
        .replace(/\//g, '_');
}

function loadServiceAccount(): ServiceAccount {
    const inline = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON;
    if (inline) return JSON.parse(inline);

    const path = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_FILE || process.env.GOOGLE_APPLICATION_CREDENTIALS;
    if (!path) {
        throw new Error('GOOGLE_PLAY_SERVICE_ACCOUNT_JSON or GOOGLE_PLAY_SERVICE_ACCOUNT_FILE is required');
    }
    return JSON.parse(readFileSync(path, 'utf8'));
}

async function getAccessToken(): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    if (cachedAccessToken && cachedAccessToken.expiresAt > now + 120) {
        return cachedAccessToken.token;
    }

    const account = loadServiceAccount();
    const header = base64Url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
    const claim = base64Url(
        JSON.stringify({
            iss: account.client_email,
            scope: ANDROID_PUBLISHER_SCOPE,
            aud: account.token_uri || TOKEN_AUDIENCE,
            exp: now + 3600,
            iat: now,
        }),
    );
    const unsigned = `${header}.${claim}`;
    const signature = crypto.createSign('RSA-SHA256').update(unsigned).sign(account.private_key);
    const assertion = `${unsigned}.${base64Url(signature)}`;

    const response = await fetch(account.token_uri || TOKEN_AUDIENCE, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
            assertion,
        }),
    });
    const body = await response.json().catch(() => null);
    if (!response.ok || !body?.access_token) {
        throw new Error(body?.error_description || body?.error || 'Google Play token request failed');
    }

    cachedAccessToken = {
        token: body.access_token,
        expiresAt: now + Number(body.expires_in || 3600),
    };
    return cachedAccessToken.token;
}

function getLineItem(raw: any, requestedProductId?: string) {
    const lineItems: any[] = raw?.lineItems || [];
    return (
        lineItems.find((item) => item.productId === requestedProductId) ||
        lineItems[0] ||
        null
    );
}

export function isGooglePlayEntitlementActive(subscriptionState: string): boolean {
    return [
        'SUBSCRIPTION_STATE_ACTIVE',
        'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
    ].includes(subscriptionState);
}

export async function verifyGooglePlaySubscription(input: {
    packageName: string;
    purchaseToken: string;
    productId?: string;
}): Promise<GooglePlaySubscriptionVerification> {
    const token = await getAccessToken();
    const url =
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
        `${encodeURIComponent(input.packageName)}/purchases/subscriptionsv2/tokens/` +
        `${encodeURIComponent(input.purchaseToken)}`;

    const response = await fetch(url, {
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'application/json',
        },
    });
    const raw = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(raw?.error?.message || `Google Play verification failed with ${response.status}`);
    }

    const lineItem = getLineItem(raw, input.productId);
    const subscriptionState = raw?.subscriptionState || 'SUBSCRIPTION_STATE_UNSPECIFIED';
    return {
        raw,
        packageName: input.packageName,
        productId: lineItem?.productId || input.productId || null,
        orderId: raw?.latestOrderId || null,
        purchaseToken: input.purchaseToken,
        subscriptionState,
        startTime: raw?.startTime || null,
        expiryTime: lineItem?.expiryTime || null,
        autoRenewing: Boolean(lineItem?.autoRenewingPlan),
        linkedPurchaseToken: raw?.linkedPurchaseToken || null,
        isEntitlementActive: isGooglePlayEntitlementActive(subscriptionState),
    };
}

export async function cancelGooglePlaySubscription(input: {
    packageName: string;
    productId: string;
    purchaseToken: string;
    cancellationType?: 'USER_REQUESTED_STOP_RENEWALS' | 'DEVELOPER_REQUESTED_STOP_PAYMENTS';
}): Promise<void> {
    const token = await getAccessToken();
    const url =
        `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/` +
        `${encodeURIComponent(input.packageName)}/purchases/subscriptions/` +
        `${encodeURIComponent(input.productId)}/tokens/` +
        `${encodeURIComponent(input.purchaseToken)}:cancel`;

    const response = await fetch(url, {
        method: 'POST',
        headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'application/json',
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(
            input.cancellationType
                ? { cancellationType: input.cancellationType }
                : {},
        ),
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.error?.message || `Google Play cancellation failed with ${response.status}`);
    }
}
