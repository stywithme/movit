import {
    CanActivate,
    ExecutionContext,
    ForbiddenException,
    Injectable,
    SetMetadata,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { getAdminFromRequest } from '@/lib/auth/admin';

// ────────────────────────────────────────────────────────────────────────────
// Metadata Keys
// ────────────────────────────────────────────────────────────────────────────
export const ADMIN_ONLY_KEY = 'admin_only';
export const SUPER_ADMIN_ONLY_KEY = 'super_admin_only';

// ────────────────────────────────────────────────────────────────────────────
// Decorators
// ────────────────────────────────────────────────────────────────────────────

export interface AdminOnlyOptions {
    /**
     * If true, it explicitly blocks doctors (isDoctor: true) from accessing the route.
     * Default is true.
     */
    blockDoctors?: boolean;
    /**
     * If true, it restricts the route to ONLY Super Admins (isSuperAdmin: true).
     * Default is false.
     */
    superAdminOnly?: boolean;
}

/**
 * Marks a route as restricted to non-doctor Admins.
 * 
 * Examples:
 *   @AdminOnly()                        →  Blocks doctors, allows admins and super admins
 *   @AdminOnly({ superAdminOnly: true }) →  Only allows super admins
 */
export const AdminOnly = (options: AdminOnlyOptions = { blockDoctors: true, superAdminOnly: false }) => {
    return (target: any, key?: string | symbol, descriptor?: PropertyDescriptor) => {
        SetMetadata(ADMIN_ONLY_KEY, options.blockDoctors !== false)(target, key!, descriptor!);
        if (options.superAdminOnly) {
            SetMetadata(SUPER_ADMIN_ONLY_KEY, true)(target, key!, descriptor!);
        }
    };
};

// ────────────────────────────────────────────────────────────────────────────
// Guard
// ────────────────────────────────────────────────────────────────────────────

@Injectable()
export class AdminGuard implements CanActivate {
    constructor(private readonly reflector: Reflector) { }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const request = context.switchToHttp().getRequest();
        const adminPayload = getAdminFromRequest(request);

        if (!adminPayload) {
            throw new ForbiddenException('Not authenticated as admin');
        }

        const isSuperAdminOnly = this.reflector.getAllAndOverride<boolean>(SUPER_ADMIN_ONLY_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);

        if (isSuperAdminOnly && !adminPayload.isSuperAdmin) {
            throw new ForbiddenException('This resource is only available to Super Admins');
        }

        const blockDoctors = this.reflector.getAllAndOverride<boolean>(ADMIN_ONLY_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);

        // If the route explicitly blocks doctors and the user is a doctor (and NOT a super admin)
        if (blockDoctors && adminPayload.isDoctor && !adminPayload.isSuperAdmin) {
            throw new ForbiddenException('Doctors are not permitted to access this resource');
        }

        // Attach admin info to request for convenience
        if (!request.admin) {
            request.admin = adminPayload;
        }

        return true;
    }
}
