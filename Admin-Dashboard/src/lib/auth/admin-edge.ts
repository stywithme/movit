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
  role: string;
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

export async function signAdminTokenEdge(payload: Omit<AdminJwtPayload, 'type' | 'iat' | 'exp'>) {
  const iat = Math.floor(Date.now() / 1000);
  const exp = iat + 7 * 24 * 60 * 60; // 7 days

  const header = { alg: 'HS256', typ: 'JWT' };
  const fullPayload = { ...payload, type: 'admin', iat, exp };

  const headerB64 = btoa(JSON.stringify(header)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  const payloadB64 = btoa(JSON.stringify(fullPayload)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');

  const data = new TextEncoder().encode(`${headerB64}.${payloadB64}`);
  const key = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(ADMIN_JWT_SECRET),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign('HMAC', key, data);
  const signatureB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/=/g, '')
    .replace(/\+/g, '-')
    .replace(/\//g, '_');

  return `${headerB64}.${payloadB64}.${signatureB64}`;
}

export async function verifyAdminTokenEdge(token: string) {
  try {
    const payload = await verifyHs256(token, ADMIN_JWT_SECRET);
    return payload;
  } catch (e) {
    return null;
  }
}
