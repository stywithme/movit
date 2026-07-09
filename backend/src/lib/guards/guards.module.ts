import { Global, Module } from '@nestjs/common';
import { MobileAuthGuard } from './mobile-auth.guard';
import { UserPermissionGuard } from './user-permission.guard';

@Global()
@Module({
    providers: [MobileAuthGuard, UserPermissionGuard],
    exports: [MobileAuthGuard, UserPermissionGuard],
})
export class GuardsModule { }
