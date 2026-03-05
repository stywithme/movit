/**
 * Mobile Auth Guard
 * =================
 *
 * Guard responsible for authenticating Mobile App users.
 * Works in two modes based on the decorator:
 *
 *   @MobileAuth()         → Requires a valid JWT (any user, free or pro)
 *   @RequireProUser()     → Requires a valid JWT + isPro === true
 *
 * Usage example:
 *
 *   @UseGuards(MobileAuthGuard)
 *   @MobileAuth()
 *   @Get('profile')
 *   async getProfile() { ... }
 *
 *   @UseGuards(MobileAuthGuard)
 *   @RequireProUser()
 *   @Get('premium-content')
 *   async getPremiumContent() { ... }
 */

import {
    CanActivate,
    ExecutionContext,
    ForbiddenException,
    Injectable,
    UnauthorizedException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { authService } from '@/modules/auth/auth.service';
import { getPrisma } from '@/lib/prisma/client';

// ────────────────────────────────────────────────────────────────────────────
// Decorator Keys
// ────────────────────────────────────────────────────────────────────────────
export const MOBILE_AUTH_KEY = 'mobile_auth';
export const REQUIRE_PRO_KEY = 'require_pro';

// ────────────────────────────────────────────────────────────────────────────
// Decorators
// ────────────────────────────────────────────────────────────────────────────
import { SetMetadata } from '@nestjs/common';

/**
 * Marks a route as requiring a logged-in mobile user (free or pro).
 */
export const MobileAuth = () => SetMetadata(MOBILE_AUTH_KEY, true);

/**
 * Marks a route as requiring a logged-in PRO mobile user.
 * Automatically implies @MobileAuth() — no need to stack both.
 */
export const RequireProUser = () => SetMetadata(REQUIRE_PRO_KEY, true);

// ────────────────────────────────────────────────────────────────────────────
// Guard
// ────────────────────────────────────────────────────────────────────────────
@Injectable()
export class MobileAuthGuard implements CanActivate {
    constructor(private readonly reflector: Reflector) { }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const requireAuth = this.reflector.getAllAndOverride<boolean>(MOBILE_AUTH_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);

        const requirePro = this.reflector.getAllAndOverride<boolean>(REQUIRE_PRO_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);

        // If no decorator is set on this route, allow freely (public route)
        if (!requireAuth && !requirePro) {
            return true;
        }

        // ── Extract Bearer Token ──────────────────────────────────────────────
        const request = context.switchToHttp().getRequest();
        const authHeader: string | undefined = request.headers['authorization'];

        if (!authHeader || !authHeader.startsWith('Bearer ')) {
            throw new UnauthorizedException('Missing or invalid Authorization header');
        }

        const token = authHeader.replace('Bearer ', '').trim();
        const payload = authService.verifyTokenFull(token);

        if (!payload || (!payload.sub && !payload.userId)) {
            throw new UnauthorizedException('Invalid or expired token');
        }

        const userId = payload.sub || payload.userId;

        // Attach userId and payload to the request so controllers and other guards can read it
        request.userId = userId;
        request.userPayload = payload;

        // ── Pro Check ─────────────────────────────────────────────────────────
        if (requirePro) {
            // For old tokens, default to checking DB or assuming regular if not present
            // But since this is a new feature we rely on new payload or backwards compatibility
            if (payload.isActive === false) {
                throw new UnauthorizedException('Account is deactivated');
            }

            let isProActive = false;

            if (payload.type === 'premium') {
                isProActive = true;
            } else if (payload.subscriptionExpiry) {
                const expiry = new Date(payload.subscriptionExpiry);
                isProActive = expiry > new Date();
            }

            if (!isProActive) {
                throw new ForbiddenException(
                    'This feature requires an active Pro subscription',
                );
            }
        }

        return true;
    }
}
