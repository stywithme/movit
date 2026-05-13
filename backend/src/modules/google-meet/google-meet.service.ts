/**
 * Google Meet Service
 * ====================
 * Handles:
 *  - OAuth 2.0 authorization URL generation
 *  - OAuth callback (code exchange → token storage)
 *  - Access-token refresh with encrypted refresh-token storage
 *  - Meet spaces.create API call
 *  - Connection status retrieval and disconnect
 */

import { Injectable, Logger } from '@nestjs/common';
import { google } from 'googleapis';
import * as crypto from 'crypto';
import * as jwt from 'jsonwebtoken';
import { getPrisma } from '@/lib/prisma/client';
import { encryptToken, decryptToken } from './google-meet.crypto';
import type {
    GoogleMeetConnectionStatus,
    MeetSpaceResult,
    OAuthStatePayload,
} from './google-meet.types';

// ─── Google OAuth scopes required for Meet spaces ─────────────────────────────
const MEET_SCOPES = [
    'openid',
    'email',
    'profile',
    'https://www.googleapis.com/auth/meetings.space.created',
];

const STATE_SECRET = () =>
    process.env.GOOGLE_MEET_TOKEN_ENCRYPTION_KEY?.slice(0, 32) ||
    'dev_state_secret_change_in_prod!!';

// ─── State JWT helpers ────────────────────────────────────────────────────────
function signState(adminId: string, redirectTo?: string): string {
    const nonce = crypto.randomBytes(16).toString('hex');
    return jwt.sign(
        { adminId, nonce, redirectTo } satisfies Omit<OAuthStatePayload, 'iat' | 'exp'>,
        STATE_SECRET(),
        { expiresIn: '10m' },
    );
}

function verifyState(state: string): OAuthStatePayload | null {
    try {
        return jwt.verify(state, STATE_SECRET()) as OAuthStatePayload;
    } catch {
        return null;
    }
}

// ─── OAuth2 client factory ────────────────────────────────────────────────────
function createOAuth2Client() {
    return new google.auth.OAuth2(
        process.env.GOOGLE_MEET_CLIENT_ID,
        process.env.GOOGLE_MEET_CLIENT_SECRET,
        process.env.GOOGLE_MEET_REDIRECT_URI,
    );
}

@Injectable()
export class GoogleMeetService {
    private readonly logger = new Logger(GoogleMeetService.name);

    // ── 1. Build authorization URL ─────────────────────────────────────────────
    buildAuthUrl(adminId: string, redirectTo?: string): string {
        const oauth2Client = createOAuth2Client();
        const state = signState(adminId, redirectTo);
        return oauth2Client.generateAuthUrl({
            access_type: 'offline',
            prompt: 'consent',
            scope: MEET_SCOPES,
            state,
        });
    }

    // ── 2. Handle OAuth callback ───────────────────────────────────────────────
    async handleCallback(code: string, state: string): Promise<{
        adminId: string;
        redirectTo?: string;
    }> {
        const payload = verifyState(state);
        if (!payload) {
            throw new Error('Invalid or expired OAuth state');
        }

        const oauth2Client = createOAuth2Client();
        const { tokens } = await oauth2Client.getToken(code);

        if (!tokens.refresh_token) {
            throw new Error(
                'No refresh token returned. The user may have already connected. ' +
                'Disconnect and reconnect to re-issue a refresh token.',
            );
        }

        // Fetch Google user info to get sub + email
        oauth2Client.setCredentials(tokens);
        const oauth2 = google.oauth2({ version: 'v2', auth: oauth2Client });
        const userInfo = await oauth2.userinfo.get();
        const googleSub = userInfo.data.id!;
        const googleEmail = userInfo.data.email!;

        const encryptedRefresh = encryptToken(tokens.refresh_token);
        const accessToken = tokens.access_token ?? undefined;
        const accessTokenExpiresAt = tokens.expiry_date
            ? new Date(tokens.expiry_date)
            : undefined;
        const scope = tokens.scope ?? MEET_SCOPES.join(' ');

        const prisma = await getPrisma();
        await prisma.adminGoogleMeetConnection.upsert({
            where: { adminId: payload.adminId },
            update: {
                googleSub,
                googleEmail,
                encryptedRefreshToken: encryptedRefresh,
                accessToken: accessToken ?? null,
                accessTokenExpiresAt: accessTokenExpiresAt ?? null,
                scope,
                lastError: null,
                connectedAt: new Date(),
                disconnectedAt: null,
            },
            create: {
                adminId: payload.adminId,
                googleSub,
                googleEmail,
                encryptedRefreshToken: encryptedRefresh,
                accessToken: accessToken ?? null,
                accessTokenExpiresAt: accessTokenExpiresAt ?? null,
                scope,
            },
        });

        this.logger.log(`Google Meet connected for admin ${payload.adminId} (${googleEmail})`);
        return { adminId: payload.adminId, redirectTo: payload.redirectTo };
    }

    // ── 3. Get connection status ───────────────────────────────────────────────
    async getStatus(adminId: string): Promise<GoogleMeetConnectionStatus> {
        const prisma = await getPrisma();
        const conn = await prisma.adminGoogleMeetConnection.findUnique({
            where: { adminId },
        });

        if (!conn || conn.disconnectedAt) {
            return { connected: false, status: 'disconnected' };
        }

        if (conn.lastError) {
            return {
                connected: false,
                status: 'reconnect_required',
                googleEmail: conn.googleEmail,
                lastError: conn.lastError,
                connectedAt: conn.connectedAt,
            };
        }

        return {
            connected: true,
            status: 'connected',
            googleEmail: conn.googleEmail,
            connectedAt: conn.connectedAt,
        };
    }

    // ── 4. Disconnect ──────────────────────────────────────────────────────────
    async disconnect(adminId: string): Promise<void> {
        const prisma = await getPrisma();
        const conn = await prisma.adminGoogleMeetConnection.findUnique({
            where: { adminId },
        });
        if (!conn) return;

        // Attempt to revoke access on Google's side (best-effort)
        try {
            const oauth2Client = createOAuth2Client();
            const refreshToken = decryptToken(conn.encryptedRefreshToken);

            if (conn.accessToken) {
                await oauth2Client.revokeToken(conn.accessToken);
            }

            await oauth2Client.revokeToken(refreshToken);
        } catch (e) {
            this.logger.warn(`Google revoke failed for admin ${adminId}: ${e}`);
        }

        await prisma.adminGoogleMeetConnection.delete({ where: { adminId } });

        this.logger.log(`Google Meet disconnected for admin ${adminId}`);
    }

    // ── 5. Create a Meet space for a booking ──────────────────────────────────
    async createSpace(doctorAdminId: string): Promise<MeetSpaceResult> {
        const accessToken = await this.getValidAccessToken(doctorAdminId);
        if (!accessToken) {
            throw new Error(
                `Doctor ${doctorAdminId} has no active Google Meet connection. ` +
                'They must connect their Google account before bookings can be confirmed.',
            );
        }

        const oauth2Client = createOAuth2Client();
        oauth2Client.setCredentials({ access_token: accessToken });

        // Use Meet REST API via googleapis
        const meet = google.meet({ version: 'v2', auth: oauth2Client });
        const response = await meet.spaces.create({ requestBody: {} });

        const spaceName = response.data.name!;
        const meetingUri = response.data.meetingUri!;
        const meetingCode = response.data.meetingCode!;

        return { spaceName, meetingUri, meetingCode };
    }

    // ── Internal: get a valid (fresh) access token ────────────────────────────
    async getValidAccessToken(adminId: string): Promise<string | null> {
        const prisma = await getPrisma();
        const conn = await prisma.adminGoogleMeetConnection.findUnique({
            where: { adminId },
        });

        if (!conn || conn.disconnectedAt) return null;

        const bufferMs = 5 * 60 * 1000; // refresh 5 min before expiry
        const now = Date.now();
        const expiresAt = conn.accessTokenExpiresAt?.getTime() ?? 0;

        if (conn.accessToken && expiresAt > now + bufferMs) {
            return conn.accessToken;
        }

        // Refresh
        try {
            const oauth2Client = createOAuth2Client();
            const refreshToken = decryptToken(conn.encryptedRefreshToken);
            oauth2Client.setCredentials({ refresh_token: refreshToken });
            const { credentials } = await oauth2Client.refreshAccessToken();

            await prisma.adminGoogleMeetConnection.update({
                where: { adminId },
                data: {
                    accessToken: credentials.access_token ?? null,
                    accessTokenExpiresAt: credentials.expiry_date
                        ? new Date(credentials.expiry_date)
                        : null,
                    lastError: null,
                },
            });

            return credentials.access_token ?? null;
        } catch (err: any) {
            const msg = err?.message ?? 'Token refresh failed';
            this.logger.error(`Token refresh failed for admin ${adminId}: ${msg}`);

            // Mark connection as unhealthy so the doctor is prompted to reconnect
            await prisma.adminGoogleMeetConnection.update({
                where: { adminId },
                data: {
                    lastError: msg,
                    accessToken: null,
                    accessTokenExpiresAt: null,
                },
            });

            return null;
        }
    }

    // ── Super admin: get status for a specific doctor ─────────────────────────
    async getStatusForDoctor(doctorAdminId: string): Promise<GoogleMeetConnectionStatus> {
        return this.getStatus(doctorAdminId);
    }
}
