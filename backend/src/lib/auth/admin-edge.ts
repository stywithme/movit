/**
 * Admin Auth (Edge)
 * =================
 * 
 * Minimal JWT verification for Edge runtime (HS256).
 */

const ADMIN_JWT_SECRET = process.env.ADMIN_JWT_SECRET || 'your-admin-jwt-secret-change-in-production';
export const ADMIN_COOKIE_NAME = 'admin_token';

type AdminJwtPayload = {
  adminId: string;
  email: string;
  roleId: string | null;
  isSuperAdmin: boolean;
  type: 'admin';
  iat?: number;
  exp?: number;
};

function base64UrlToUint8Array(input: string) {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
  const binary = atob(padded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function base64UrlEncode(bytes: Uint8Array) {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

async function verifyHs256(token: string, secret: string) {
  const parts = token.split('.');
  if (parts.length !== 3) return null;

  const [headerB64, payloadB64, signatureB64] = parts;
  const headerJson = JSON.parse(new TextDecoder().decode(base64UrlToUint8Array(headerB64)));
  if (headerJson.alg !== 'HS256') return null;

  const data = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['verify']
  );
  const signature = base64UrlToUint8Array(signatureB64);
  const isValid = await crypto.subtle.verify('HMAC', key, signature, data);
  if (!isValid) return null;

  const payload = JSON.parse(new TextDecoder().decode(base64UrlToUint8Array(payloadB64))) as AdminJwtPayload;
  if (payload.type !== 'admin') return null;
  if (payload.exp && Date.now() >= payload.exp * 1000) return null;

  return payload;
}

export async function verifyAdminTokenEdge(token: string) {
  return verifyHs256(token, ADMIN_JWT_SECRET);
}
