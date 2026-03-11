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
export const DOCTOR_ONLY_KEY = 'doctor_only';
export const DOCTOR_ID_PARAM_KEY = 'doctor_id_param';

// ────────────────────────────────────────────────────────────────────────────
// Decorators
// ────────────────────────────────────────────────────────────────────────────

export interface DoctorOnlyOptions {
    /**
     * The name of the route parameter that contains the doctor (Admin) ID.
     * If provided, the guard will ensure the logged-in doctor is the same as the one in the param.
     */
    idParam?: string;
}

/**
 * Marks a route as restricted to Doctors (Admins with isDoctor = true).
 * Optionally checks if the doctor is the owner of the resource via a route parameter.
 *
 * Examples:
 *   @DoctorOnly()                        →  Checks if isDoctor === true
 *   @DoctorOnly({ idParam: 'adminId' })   →  Checks if isDoctor === true AND req.params.adminId === loggedInAdminId
 */
export const DoctorOnly = (options?: DoctorOnlyOptions) => {
    return (target: any, key?: string | symbol, descriptor?: PropertyDescriptor) => {
        SetMetadata(DOCTOR_ONLY_KEY, true)(target, key!, descriptor!);
        if (options?.idParam) {
            SetMetadata(DOCTOR_ID_PARAM_KEY, options.idParam)(target, key!, descriptor!);
        }
    };
};

// ────────────────────────────────────────────────────────────────────────────
// Guard
// ────────────────────────────────────────────────────────────────────────────

@Injectable()
export class DoctorGuard implements CanActivate {
    constructor(private readonly reflector: Reflector) { }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const request = context.switchToHttp().getRequest();
        const adminPayload = getAdminFromRequest(request);

        if (!adminPayload) {
            throw new ForbiddenException('Not authenticated as admin');
        }

        // 1. Check if the admin is a doctor or a super admin
        // Super admins are allowed to bypass Doctor-only restrictions
        if (!adminPayload.isDoctor && !adminPayload.isSuperAdmin) {
            throw new ForbiddenException('This resource is only available to doctors');
        }

        // 2. Ownership Check (if @DoctorOnly({ idParam: '...' }) is used)
        const requireDoctor = this.reflector.getAllAndOverride<boolean>(DOCTOR_ONLY_KEY, [
            context.getHandler(),
            context.getClass(),
        ]);

        if (requireDoctor && !adminPayload.isSuperAdmin) {
            const paramName = this.reflector.getAllAndOverride<string>(DOCTOR_ID_PARAM_KEY, [
                context.getHandler(),
                context.getClass(),
            ]);

            if (paramName) {
                const resourceAdminId = request.params?.[paramName];

                if (resourceAdminId && resourceAdminId !== adminPayload.adminId) {
                    throw new ForbiddenException(
                        'You can only access your own resources as a doctor',
                    );
                }
            }
        }

        // Attach doctor info to request for convenience
        request.admin = adminPayload;

        return true;
    }
}
