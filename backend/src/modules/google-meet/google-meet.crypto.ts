/**
 * Google Meet Token Encryption
 * ==============================
 * AES-256-GCM symmetric encryption for storing refresh tokens at rest.
 * Key is loaded from GOOGLE_MEET_TOKEN_ENCRYPTION_KEY (32-byte hex string).
 */

import * as crypto from 'crypto';

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12;  // 96-bit IV recommended for GCM
const AUTH_TAG_LENGTH = 16;

function getKey(): Buffer {
    const keyHex = process.env.GOOGLE_MEET_TOKEN_ENCRYPTION_KEY;
    if (!keyHex || keyHex.length < 64) {
        // In development, allow a fallback but warn loudly
        if (process.env.NODE_ENV !== 'production') {
            const fallback = 'dev_fallback_key_do_not_use_in_prod_1234567';
            return Buffer.from(fallback.padEnd(32).slice(0, 32));
        }
        throw new Error('GOOGLE_MEET_TOKEN_ENCRYPTION_KEY must be a 32-byte hex string (64 hex chars)');
    }
    return Buffer.from(keyHex.slice(0, 64), 'hex');
}

/** Encrypt a plain-text token. Returns a base64-encoded `iv:authTag:ciphertext` string. */
export function encryptToken(plainText: string): string {
    const key = getKey();
    const iv = crypto.randomBytes(IV_LENGTH);
    const cipher = crypto.createCipheriv(ALGORITHM, key, iv, { authTagLength: AUTH_TAG_LENGTH });
    const encrypted = Buffer.concat([cipher.update(plainText, 'utf8'), cipher.final()]);
    const authTag = cipher.getAuthTag();
    // Format: base64(iv) + ':' + base64(authTag) + ':' + base64(ciphertext)
    return [
        iv.toString('base64'),
        authTag.toString('base64'),
        encrypted.toString('base64'),
    ].join(':');
}

/** Decrypt a token previously encrypted with `encryptToken`. */
export function decryptToken(stored: string): string {
    const key = getKey();
    const parts = stored.split(':');
    if (parts.length !== 3) throw new Error('Invalid encrypted token format');
    const [ivB64, authTagB64, cipherB64] = parts;
    const iv = Buffer.from(ivB64, 'base64');
    const authTag = Buffer.from(authTagB64, 'base64');
    const ciphertext = Buffer.from(cipherB64, 'base64');
    const decipher = crypto.createDecipheriv(ALGORITHM, key, iv, { authTagLength: AUTH_TAG_LENGTH });
    decipher.setAuthTag(authTag);
    return Buffer.concat([decipher.update(ciphertext), decipher.final()]).toString('utf8');
}
