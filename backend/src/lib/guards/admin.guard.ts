import {
    CanActivate,
    ExecutionContext,
    ForbiddenException,
    Injectable,
    SetMetadata,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { getAdminFromRequest } from '@/lib/auth/admin';

export const SUPER_ADMIN_ONLY_KEY = 'super_admin_only';

export interface AdminOnlyOptions {
    superAdminOnly?: boolean;
}

/**
 * Optionally restricts a route to super admins only.
 */
export const AdminOnly = (options: AdminOnlyOptions = { superAdminOnly: false }) => {
    return (target: any, key?: string | symbol, descriptor?: PropertyDescriptor) => {
        if (options.superAdminOnly) {
            SetMetadata(SUPER_ADMIN_ONLY_KEY, true)(target, key!, descriptor!);
        }
    };
};

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

        if (!request.admin) {
            request.admin = adminPayload;
        }

        return true;
    }
}
