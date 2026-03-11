import { Global, Module } from '@nestjs/common';
import { MobileAuthGuard } from './mobile-auth.guard';
import { UserPermissionGuard } from './user-permission.guard';
import { DoctorGuard } from './doctor.guard';

@Global()
@Module({
    providers: [MobileAuthGuard, UserPermissionGuard, DoctorGuard],
    exports: [MobileAuthGuard, UserPermissionGuard, DoctorGuard],
})
export class GuardsModule { }
