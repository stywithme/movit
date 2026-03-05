import { Injectable, NotFoundException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';

@Injectable()
export class PermissionsService {
    async getAllPermissions() {
        const prisma = await getPrisma();
        return prisma.permission.findMany({
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

    async getRole(id: string) {
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
        return role;
    }

    async createRole(data: { name: string; displayName: any; description?: any; permissionIds?: string[]; assignedAdminIds?: string[] }) {
        const prisma = await getPrisma();
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

        return this.getRole(createdRole.id);
    }

    async updateRole(id: string, data: { name?: string; displayName?: any; description?: any; permissionIds?: string[]; assignedAdminIds?: string[] }) {
        const prisma = await getPrisma();
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
                await tx.rolePermission.deleteMany({ where: { roleId: id } });
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
                // Remove role from admins currently assigned to this role but not in the new list
                await tx.modelHasRole.deleteMany({
                    where: { roleId: id, modelType: 'Admin', modelId: { notIn: data.assignedAdminIds } },
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

        return this.getRole(id);
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
