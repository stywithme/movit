import {
    Environment,
    SignedDataVerifier,
    VerificationException,
} from '@apple/app-store-server-library';
import {
    assertAppStorePayloadMatchesRequest,
    assertValidAppStoreJwsStructure,
    decodeAppStoreSignedTransaction,
    isAppStoreEntitlementActive,
    resetAppStoreVerifierCacheForTests,
    verifyAppStoreSignedTransaction,
} from './app-store.client';

jest.mock('@apple/app-store-server-library', () => {
    const actual = jest.requireActual('@apple/app-store-server-library');
    return {
        ...actual,
        SignedDataVerifier: jest.fn(),
    };
});

const MockSignedDataVerifier = SignedDataVerifier as unknown as jest.Mock;

function buildJws(payload: Record<string, unknown>): string {
    const header = Buffer.from(JSON.stringify({ alg: 'ES256', typ: 'JWT' })).toString('base64url');
    const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
    const signature = Buffer.from('sig').toString('base64url');
    return `${header}.${body}.${signature}`;
}

describe('app-store.client', () => {
    const originalNodeEnv = process.env.NODE_ENV;
    const originalBundleId = process.env.APP_STORE_BUNDLE_ID;

    beforeEach(() => {
        jest.clearAllMocks();
        resetAppStoreVerifierCacheForTests();
        process.env.APP_STORE_BUNDLE_ID = 'com.example.ios';
        process.env.NODE_ENV = 'test';
        MockSignedDataVerifier.mockImplementation(() => ({
            verifyAndDecodeTransaction: jest.fn(),
        }));
    });

    afterAll(() => {
        process.env.NODE_ENV = originalNodeEnv;
        if (originalBundleId === undefined) {
            delete process.env.APP_STORE_BUNDLE_ID;
        } else {
            process.env.APP_STORE_BUNDLE_ID = originalBundleId;
        }
        resetAppStoreVerifierCacheForTests();
    });

    it('decodes a well-formed JWS payload (structure-only helper)', () => {
        const jws = buildJws({
            transactionId: 'tx-1',
            originalTransactionId: 'orig-1',
            productId: 'pro.monthly',
            bundleId: 'com.example.ios',
            expiresDate: Date.now() + 60_000,
        });

        const payload = decodeAppStoreSignedTransaction(jws);
        expect(payload.transactionId).toBe('tx-1');
        expect(payload.originalTransactionId).toBe('orig-1');
        expect(isAppStoreEntitlementActive(payload)).toBe(true);
    });

    it('rejects malformed JWS structure', () => {
        expect(() => assertValidAppStoreJwsStructure('only.two')).toThrow(/expected header/);
        expect(() => decodeAppStoreSignedTransaction('a.b')).toThrow();
    });

    it('assertAppStorePayloadMatchesRequest enforces identifiers and bundle id', () => {
        const payload = {
            transactionId: 'tx-1',
            originalTransactionId: 'orig-1',
            productId: 'pro.monthly',
            bundleId: 'com.example.ios',
            expiresDate: Date.now() + 60_000,
        };

        expect(() =>
            assertAppStorePayloadMatchesRequest(payload, {
                transactionId: 'tx-1',
                originalTransactionId: 'orig-1',
                productId: 'pro.monthly',
            }),
        ).not.toThrow();

        expect(() =>
            assertAppStorePayloadMatchesRequest(payload, {
                transactionId: 'tx-1',
                originalTransactionId: 'orig-mismatch',
                productId: 'pro.monthly',
            }),
        ).toThrow(/original transaction id/);

        expect(() =>
            assertAppStorePayloadMatchesRequest(
                { ...payload, bundleId: 'com.other.app' },
                {
                    transactionId: 'tx-1',
                    originalTransactionId: 'orig-1',
                    productId: 'pro.monthly',
                },
            ),
        ).toThrow(/bundle id/);
    });

    it('isAppStoreEntitlementActive fails closed in production when expiresDate is missing', () => {
        process.env.NODE_ENV = 'production';
        expect(isAppStoreEntitlementActive({})).toBe(false);
    });

    it('verifyAppStoreSignedTransaction rejects forged JWS signatures', async () => {
        const forgedJws = buildJws({
            transactionId: 'tx-forged',
            originalTransactionId: 'orig-forged',
            productId: 'pro.monthly',
            bundleId: 'com.example.ios',
            expiresDate: Date.now() + 60_000,
        });

        MockSignedDataVerifier.mockImplementation(() => ({
            verifyAndDecodeTransaction: jest.fn().mockRejectedValue(
                new VerificationException(1, 'Invalid JWS signature'),
            ),
        }));

        await expect(verifyAppStoreSignedTransaction(forgedJws)).rejects.toThrow(
            /App Store JWS verification failed/,
        );
        expect(MockSignedDataVerifier).toHaveBeenCalledWith(
            expect.any(Array),
            expect.any(Boolean),
            Environment.SANDBOX,
            'com.example.ios',
            undefined,
        );
    });

    it('verifyAppStoreSignedTransaction returns cryptographically verified payload', async () => {
        const verifiedPayload = {
            transactionId: 'tx-1',
            originalTransactionId: 'orig-1',
            productId: 'pro.monthly',
            bundleId: 'com.example.ios',
            expiresDate: Date.now() + 60_000,
            environment: Environment.SANDBOX,
        };

        MockSignedDataVerifier.mockImplementation(() => ({
            verifyAndDecodeTransaction: jest.fn().mockResolvedValue(verifiedPayload),
        }));

        const jws = buildJws(verifiedPayload);
        const payload = await verifyAppStoreSignedTransaction(jws);
        expect(payload.transactionId).toBe('tx-1');
        expect(payload.productId).toBe('pro.monthly');
    });
});
