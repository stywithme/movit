/**
 * User Permission Guard (Hardcoded)
 * ==================================
 *
 * Guard for Mobile App users with hardcoded permissions.
 * Ensures users can ONLY access their own data (profile, files, etc.).
 *
 * Permissions:
 *   - read   own profile   ✅
 *   - update own profile   ✅
 *   - delete own account   ✅
 *   - read   own files     ✅
 *   - read   other users   ❌
 *   - update other users   ❌
 *
 * Also loads user subscription status (isPro / isFree) for route-level checks
 * from the JWT payload without querying the database.
 *
 * Usage:
 *
 *   @UseGuards(MobileAuthGuard, UserPermissionGuard)
 *   @MobileAuth()
 *   @OwnerOnly({ idParam: 'id' })            // ensures :id param === logged-in userId
 *   @Get(':id')
 *   async getProfile() { ... }
 *
 *   @UseGuards(MobileAuthGuard, UserPermissionGuard)
 *   @MobileAuth()
 *   @OwnerOnly({ idParam: 'userId' })    // ensures :userId param === logged-in userId
 *   @Get(':userId/files')
 *   async getUserFiles() { ... }
 */

import {
    CanActivate,
    ExecutionContext,
    ForbiddenException,
    Injectable,
    SetMetadata,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';

// ────────────────────────────────────────────────────────────────────────────
// Metadata Keys
// ────────────────────────────────────────────────────────────────────────────
export const OWNER_ONLY_KEY = 'owner_only';
export const OWNER_PARAM_KEY = 'owner_param';

// ────────────────────────────────────────────────────────────────────────────
// Decorators
// ────────────────────────────────────────────────────────────────────────────

export interface OwnerOnlyOptions {
    idParam?: string;
}

/**
 * Marks a route as restricted to the resource owner.
 *
 * @param options Object containing the route-param name to match against the logged-in userId.
 *
 * Examples:
 *   @OwnerOnly({ idParam: 'id' })          →  checks  req.params.id     === req.userId
 *   @OwnerOnly({ idParam: 'userId' })      →  checks  req.params.userId === req.userId
 *   @OwnerOnly()                           →  skips owner parameter check
 */
export const OwnerOnly = (options?: OwnerOnlyOptions) => {
    return (target: any, key?: string | symbol, descriptor?: PropertyDescriptor) => {
        SetMetadata(OWNER_ONLY_KEY, true)(target, key!, descriptor!);
        if (options?.idParam) {
            SetMetadata(OWNER_PARAM_KEY, options.idParam)(target, key!, descriptor!);
        }
    };
};

// ────────────────────────────────────────────────────────────────────────────
// Interfaces
// ────────────────────────────────────────────────────────────────────────────

export interface UserPermissionInfo {
    userId: string;
    isPro: boolean;
    isFree: boolean;
    isActive: boolean;
    subscriptionExpiry: Date | null;
}

// ────────────────────────────────────────────────────────────────────────────
// Guard
// ────────────────────────────────────────────────────────────────────────────
@Injectable()
export class UserPermissionGuard implements CanActivate {
    constructor(private readonly reflector: Reflector) { }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const request = context.switchToHttp().getRequest();

        // userId and payload are set by MobileAuthGuard
        const userId: string | undefined = request.userId;
        const payload = request.userPayload;

        if (!userId || !payload) {
            throw new ForbiddenException('User not authenticated');
        }

        if (payload.isActive === false) {
            throw new ForbiddenException('Account is deactivated');
        }

        // Determine if Pro subscription is currently active from JWT payload
        let isProActive = false;
        let subscriptionExpiry: Date | null = null;

        if (payload.subscriptionExpiry) {
            subscriptionExpiry = new Date(payload.subscriptionExpiry);
        }

        if (payload.type === 'premium') {
            isProActive = true;
        } else if (subscriptionExpiry) {
            isProActive = subscriptionExpiry > new Date();
        }

        // Attach permission info to request for downstream use
        const permissionInfo: UserPermissionInfo = {
            userId,
            isPro: isProActive,
            isFree: !isProActive,
            isActive: payload.isActive !== false,
            subscriptionExpiry,
        };
        request.userPermission = permissionInfo;

        // ── Owner-Only Check ────────────────────────────────────────────────
        const requireOwner = this.reflector.getAllAndOverride<boolean>(OWNER_ONLY_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);

        if (requireOwner) {
            const paramName =
                this.reflector.getAllAndOverride<string>(OWNER_PARAM_KEY, [
                    context.getHandler(),
                    context.getClass(),
                ]);

            if (paramName) {
                const resourceOwnerId = request.params?.[paramName];

                if (resourceOwnerId && resourceOwnerId !== userId) {
                    throw new ForbiddenException(
                        'You can only access your own resources',
                    );
                }
            }
        }

        return true;
    }
}
