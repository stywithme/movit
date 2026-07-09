import { ForbiddenException, Injectable, NotFoundException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';

const legacyPermissionSubjects = [
    'Analytics',
    'OverviewAnalytics',
    'UserAnalytics',
    'ActivationAnalytics',
    'EngagementAnalytics',
    'TrainingAnalytics',
    'ProgramAnalytics',
    'LevelAnalytics',
    'AssessmentAnalytics',
    'ProgressionAnalytics',
    'RevenueAnalytics',
    'SafetyAnalytics',
    'ContentAnalytics',
];

const legacyPermissionActions = ['publish', 'duplicate'];

const superAdminOnlyPermissionSubjects = [
    'Program',
    'ProgramMap',
    'ReportProgram',
    'ReportActivation',
    'ReportRetention',
    'ReportAssessment',
    'ReportLevel',
    'Level',
    'AssessmentTemplate',
    'ProgressionRule',
    'ReportProgression',
];

function hiddenSubjectsFor(isSuperAdmin: boolean) {
    return isSuperAdmin
        ? legacyPermissionSubjects
        : [...legacyPermissionSubjects, ...superAdminOnlyPermissionSubjects];
}

@Injectable()
export class PermissionsService {
    async getAllPermissions(isSuperAdmin = false) {
        const prisma = await getPrisma();
        return prisma.permission.findMany({
            where: {
                subject: { notIn: hiddenSubjectsFor(isSuperAdmin) },
                action: { notIn: legacyPermissionActions },
            },
            orderBy: [{ subject: 'asc' }, { action: 'asc' }],
        });
    }

    async getRoles() {
        const prisma = await getPrisma();
        const roles = await prisma.role.findMany({
            include: {
                _count: {
                    select: { modelHasRoles: true, permissions: true },
                },
            },
            orderBy: { createdAt: 'desc' },
        });

        // Map modelHasRoles count to admins count for dashboard compatibility
        return roles.map(role => ({
            ...role,
            _count: {
                ...role._count,
                admins: role._count.modelHasRoles
            }
        }));
    }

    async getRole(id: string, isSuperAdmin = false) {
        const prisma = await getPrisma();
        const role = await prisma.role.findUnique({
            where: { id },
            include: {
                permissions: {
                    include: { permission: true },
                },
            },
        });

        if (!role) throw new NotFoundException('Role not found');
        return {
            ...role,
            permissions: role.permissions.filter((rolePermission) => (
                !hiddenSubjectsFor(isSuperAdmin).includes(rolePermission.permission.subject)
                && !legacyPermissionActions.includes(rolePermission.permission.action)
            )),
        };
    }

    private async assertAssignablePermissions(
        prisma: Awaited<ReturnType<typeof getPrisma>>,
        permissionIds: string[] | undefined,
        isSuperAdmin: boolean,
    ) {
        if (!permissionIds?.length) return;

        const disallowedPermissions = await prisma.permission.findMany({
            where: {
                id: { in: permissionIds },
                OR: [
                    { subject: { in: hiddenSubjectsFor(isSuperAdmin) } },
                    { action: { in: legacyPermissionActions } },
                ],
            },
            select: { subject: true, action: true },
        });

        if (disallowedPermissions.length > 0) {
            const names = disallowedPermissions.map((permission) => `${permission.subject}:${permission.action}`).join(', ');
            throw new ForbiddenException(`Cannot assign restricted permissions: ${names}`);
        }
    }

    private async assertAssignableAdmins(
        prisma: Awaited<ReturnType<typeof getPrisma>>,
        adminIds: string[] | undefined,
        isSuperAdmin: boolean,
    ) {
        if (isSuperAdmin || !adminIds?.length) return;

        const superAdmins = await prisma.admin.findMany({
            where: { id: { in: adminIds }, isSuperAdmin: true, deletedAt: null },
            select: { email: true },
        });

        if (superAdmins.length > 0) {
            throw new ForbiddenException('Cannot assign roles to Super Admin accounts');
        }
    }

    async createRole(data: { name: string; displayName: any; description?: any; permissionIds?: string[]; assignedAdminIds?: string[] }, isSuperAdmin = false) {
        const prisma = await getPrisma();
        await this.assertAssignablePermissions(prisma, data.permissionIds, isSuperAdmin);
        await this.assertAssignableAdmins(prisma, data.assignedAdminIds, isSuperAdmin);

        const createdRole = await prisma.$transaction(async (tx) => {
            const role = await tx.role.create({
                data: {
                    name: data.name,
                    displayName: data.displayName,
                    description: data.description,
                },
            });

            if (data.permissionIds && data.permissionIds.length > 0) {
                await tx.rolePermission.createMany({
                    data: data.permissionIds.map((permId) => ({
                        roleId: role.id,
                        permissionId: permId,
                    })),
                });
            }

            // Assign admins to this role
            if (data.assignedAdminIds && data.assignedAdminIds.length > 0) {
                await tx.modelHasRole.createMany({
                    data: data.assignedAdminIds.map(adminId => ({
                        roleId: role.id,
                        modelId: adminId,
                        modelType: 'Admin',
                    })),
                });
            }

            return role;
        });

        return this.getRole(createdRole.id, isSuperAdmin);
    }

    async updateRole(id: string, data: { name?: string; displayName?: any; description?: any; permissionIds?: string[]; assignedAdminIds?: string[] }, isSuperAdmin = false) {
        const prisma = await getPrisma();
        await this.assertAssignablePermissions(prisma, data.permissionIds, isSuperAdmin);
        await this.assertAssignableAdmins(prisma, data.assignedAdminIds, isSuperAdmin);

        await prisma.$transaction(async (tx) => {
            const role = await tx.role.findUnique({ where: { id } });
            if (!role) throw new NotFoundException('Role not found');

            await tx.role.update({
                where: { id },
                data: {
                    name: data.name,
                    displayName: data.displayName,
                    description: data.description,
                },
            });

            if (data.permissionIds !== undefined) {
                if (isSuperAdmin) {
                    await tx.rolePermission.deleteMany({ where: { roleId: id } });
                } else {
                    const visiblePermissionIds = await tx.permission.findMany({
                        where: {
                            subject: { notIn: hiddenSubjectsFor(false) },
                            action: { notIn: legacyPermissionActions },
                        },
                        select: { id: true },
                    });

                    await tx.rolePermission.deleteMany({
                        where: {
                            roleId: id,
                            permissionId: { in: visiblePermissionIds.map((permission) => permission.id) },
                        },
                    });
                }

                if (data.permissionIds.length > 0) {
                    await tx.rolePermission.createMany({
                        data: data.permissionIds.map((permId) => ({
                            roleId: id,
                            permissionId: permId,
                        })),
                    });
                }
            }

            // Sync assigned admins: assign selected, unassign others that had this role
            if (data.assignedAdminIds !== undefined) {
                const protectedAdminIds = isSuperAdmin
                    ? []
                    : (await tx.admin.findMany({
                        where: { isSuperAdmin: true, deletedAt: null },
                        select: { id: true },
                    })).map((admin) => admin.id);

                // Remove role from admins currently assigned to this role but not in the new list
                await tx.modelHasRole.deleteMany({
                    where: {
                        roleId: id,
                        modelType: 'Admin',
                        modelId: { notIn: [...data.assignedAdminIds, ...protectedAdminIds] },
                    },
                });
                // Assign role to newly selected admins
                if (data.assignedAdminIds.length > 0) {
                    await tx.modelHasRole.deleteMany({
                        where: { roleId: id, modelType: 'Admin', modelId: { in: data.assignedAdminIds } },
                    });
                    await tx.modelHasRole.createMany({
                        data: data.assignedAdminIds.map(adminId => ({
                            roleId: id,
                            modelId: adminId,
                            modelType: 'Admin',
                        })),
                    });
                }
            }
        });

        return this.getRole(id, isSuperAdmin);
    }

    async deleteRole(id: string) {
        const prisma = await getPrisma();
        const role = await prisma.role.findUnique({ where: { id } });

        if (!role) throw new NotFoundException('Role not found');
        if (role.isSystem) throw new Error('Cannot delete system role');

        await prisma.role.delete({ where: { id } });
        return { success: true };
    }
}
