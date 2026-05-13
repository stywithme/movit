/**
 * Google Meet Types
 * ==================
 * Types, interfaces, and zod schemas for the Google Meet integration module.
 */

import { z } from 'zod';

// ─── OAuth State Payload (signed via JWT) ────────────────────────────────────
export interface OAuthStatePayload {
    adminId: string;
    nonce: string;
    redirectTo?: string;
    iat: number;
    exp: number;
}

// ─── OAuth Callback Query ────────────────────────────────────────────────────
export const callbackQuerySchema = z.object({
    code: z.string().min(1),
    state: z.string().min(1),
    error: z.string().optional(),
});

// ─── Connection Status returned to dashboard ─────────────────────────────────
export interface GoogleMeetConnectionStatus {
    connected: boolean;
    googleEmail?: string;
    /** 'connected' | 'disconnected' | 'reconnect_required' */
    status: 'connected' | 'disconnected' | 'reconnect_required';
    lastError?: string;
    connectedAt?: Date;
}

// ─── Lightweight Meet Space result ───────────────────────────────────────────
export interface MeetSpaceResult {
    spaceName: string;       // e.g. "spaces/abc123"
    meetingUri: string;      // e.g. "https://meet.google.com/abc-defg-hij"
    meetingCode: string;     // e.g. "abc-defg-hij"
}
