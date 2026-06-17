import {
    Environment,
    SignedDataVerifier,
    VerificationException,
    type JWSTransactionDecodedPayload,
} from '@apple/app-store-server-library';

export type AppStoreJwsPayload = {
    transactionId?: string;
    originalTransactionId?: string;
    productId?: string;
    bundleId?: string;
    expiresDate?: number;
    environment?: string;
};

export type AppStorePurchaseRequestFields = {
    productId: string;
    transactionId: string;
    originalTransactionId: string;
};

const BASE64URL_SEGMENT = /^[A-Za-z0-9_-]+$/;

const APPLE_ROOT_CA_URLS = [
    'https://www.apple.com/appleca/AppleIncRootCertificate.cer',
    'https://www.apple.com/certificateauthority/AppleComputerRootCertificate.cer',
    'https://www.apple.com/certificateauthority/AppleRootCA-G2.cer',
    'https://www.apple.com/certificateauthority/AppleRootCA-G3.cer',
];

let cachedRootCas: Buffer[] | null = null;
let cachedVerifier: SignedDataVerifier | null = null;

/**
 * Validates compact JWS layout (header.payload[.signature]) and base64url segments.
 */
export function assertValidAppStoreJwsStructure(jws: string): void {
    const parts = jws.split('.');
    if (parts.length !== 3) {
        throw new Error('Invalid App Store JWS: expected header.payload.signature');
    }
    for (const part of parts) {
        if (!part || !BASE64URL_SEGMENT.test(part)) {
            throw new Error('Invalid App Store JWS: malformed base64url segment');
        }
    }
    try {
        decodeJwsSegment(parts[0]);
        decodeJwsSegment(parts[1]);
    } catch {
        throw new Error('Invalid App Store JWS: segment is not valid base64url JSON');
    }
}

function decodeJwsSegment(segment: string): unknown {
    const normalized = segment.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized + '='.repeat((4 - (normalized.length % 4)) % 4);
    const json = Buffer.from(padded, 'base64').toString('utf8');
    return JSON.parse(json);
}

export function getExpectedAppStoreBundleId(): string {
    const bundleId = process.env.APP_STORE_BUNDLE_ID || process.env.IOS_BUNDLE_ID;
    if (!bundleId) {
        if (process.env.NODE_ENV === 'production') {
            throw new Error('APP_STORE_BUNDLE_ID is required in production');
        }
        return 'com.example.ios';
    }
    return bundleId;
}

function resolveAppStoreEnvironment(): Environment {
    const configured = (process.env.APP_STORE_ENV || '').trim().toLowerCase();
    if (configured === 'production') return Environment.PRODUCTION;
    if (configured === 'sandbox') return Environment.SANDBOX;
    return process.env.NODE_ENV === 'production' ? Environment.PRODUCTION : Environment.SANDBOX;
}

async function loadAppleRootCertificates(): Promise<Buffer[]> {
    if (cachedRootCas) return cachedRootCas;

    const certificates = await Promise.all(
        APPLE_ROOT_CA_URLS.map(async (url) => {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`Failed to download Apple root CA from ${url}: HTTP ${response.status}`);
            }
            const bytes = await response.arrayBuffer();
            return Buffer.from(bytes);
        }),
    );

    cachedRootCas = certificates;
    return certificates;
}

async function getSignedDataVerifier(): Promise<SignedDataVerifier> {
    if (cachedVerifier) return cachedVerifier;

    const bundleId = getExpectedAppStoreBundleId();
    const environment = resolveAppStoreEnvironment();
    const appAppleIdRaw = process.env.APP_STORE_APPLE_ID?.trim();
    const appAppleId = appAppleIdRaw ? Number.parseInt(appAppleIdRaw, 10) : undefined;
    if (environment === Environment.PRODUCTION && !appAppleId) {
        throw new Error('APP_STORE_APPLE_ID is required when APP_STORE_ENV=Production');
    }

    const rootCertificates = await loadAppleRootCertificates();
    const enableOnlineChecks = process.env.NODE_ENV === 'production';

    cachedVerifier = new SignedDataVerifier(
        rootCertificates,
        enableOnlineChecks,
        environment,
        bundleId,
        appAppleId,
    );
    return cachedVerifier;
}

function mapDecodedPayload(decoded: JWSTransactionDecodedPayload): AppStoreJwsPayload {
    return {
        transactionId: decoded.transactionId,
        originalTransactionId: decoded.originalTransactionId,
        productId: decoded.productId,
        bundleId: decoded.bundleId,
        expiresDate: decoded.expiresDate,
        environment: decoded.environment,
    };
}

/**
 * Cryptographically verifies an App Store signedTransaction JWS and returns the decoded payload.
 */
export async function verifyAppStoreSignedTransaction(jws: string): Promise<AppStoreJwsPayload> {
    assertValidAppStoreJwsStructure(jws);

    try {
        const verifier = await getSignedDataVerifier();
        const decoded = await verifier.verifyAndDecodeTransaction(jws);
        return mapDecodedPayload(decoded);
    } catch (error) {
        if (error instanceof VerificationException) {
            throw new Error(`App Store JWS verification failed: ${error.message}`);
        }
        throw error;
    }
}

/**
 * @deprecated Use verifyAppStoreSignedTransaction for production trust.
 */
export function decodeAppStoreSignedTransaction(jws: string): AppStoreJwsPayload {
    assertValidAppStoreJwsStructure(jws);
    const payload = decodeJwsSegment(jws.split('.')[1]);
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
        throw new Error('Invalid App Store JWS payload');
    }
    return payload as AppStoreJwsPayload;
}

/**
 * Ensures decoded JWS payload identifiers match the client verify request and expected bundle id.
 */
export function assertAppStorePayloadMatchesRequest(
    payload: AppStoreJwsPayload,
    request: AppStorePurchaseRequestFields,
    expectedBundleId: string = getExpectedAppStoreBundleId(),
): void {
    if (!payload.bundleId) {
        throw new Error('App Store JWS payload missing bundleId');
    }
    if (payload.bundleId !== expectedBundleId) {
        throw new Error('App Store bundle id does not match this app');
    }
    if (!payload.productId) {
        throw new Error('App Store JWS payload missing productId');
    }
    if (payload.productId !== request.productId) {
        throw new Error('Verified product does not match purchase request');
    }
    if (!payload.originalTransactionId) {
        throw new Error('App Store JWS payload missing originalTransactionId');
    }
    if (String(payload.originalTransactionId) !== request.originalTransactionId) {
        throw new Error('Verified original transaction id does not match purchase request');
    }
    if (!payload.transactionId) {
        throw new Error('App Store JWS payload missing transactionId');
    }
    if (String(payload.transactionId) !== request.transactionId) {
        throw new Error('Verified transaction id does not match purchase request');
    }
    if (payload.expiresDate == null) {
        if (process.env.NODE_ENV === 'production') {
            throw new Error('App Store JWS payload missing expiresDate');
        }
    }
}

export function isAppStoreEntitlementActive(payload: AppStoreJwsPayload): boolean {
    if (payload.expiresDate == null) {
        return process.env.NODE_ENV !== 'production';
    }
    return payload.expiresDate > Date.now();
}

/** Test helper to reset cached Apple verifier state between Jest cases. */
export function resetAppStoreVerifierCacheForTests(): void {
    cachedRootCas = null;
    cachedVerifier = null;
}
