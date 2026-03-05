import {
    Injectable,
    CanActivate,
    ExecutionContext,
    ForbiddenException,
} from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { CaslAbilityFactory } from './casl-ability.factory';
import { PERMISSION_CHECK_KEY, RequiredPermission } from './check-permission.decorator';
import { getAdminIdFromRequest } from '@/lib/auth/admin';

@Injectable()
export class CaslGuard implements CanActivate {
    constructor(
        private reflector: Reflector,
        private caslAbilityFactory: CaslAbilityFactory,
    ) { }

    async canActivate(context: ExecutionContext): Promise<boolean> {
        const requiredPermission = this.reflector.getAllAndOverride<RequiredPermission>(
            PERMISSION_CHECK_KEY,
            [context.getHandler(), context.getClass()],
        );

        // If no permission defined on the route, it means it's not restricted by CaslGuard
        if (!requiredPermission) {
            return true;
        }

        const request = context.switchToHttp().getRequest();
        const adminId = getAdminIdFromRequest(request);

        if (!adminId) {
            throw new ForbiddenException('Not authenticated as admin');
        }

        const ability = await this.caslAbilityFactory.createForAdmin(adminId);

        const [action, subject] = requiredPermission;

        if (ability.can(action, subject)) {
            return true;
        }

        throw new ForbiddenException(
            `Admin is not allowed to ${action} ${subject}`
        );
    }
}
