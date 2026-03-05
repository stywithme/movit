import { Injectable } from '@nestjs/common';
import { AbilityBuilder, PureAbility, createMongoAbility } from '@casl/ability';
import { PrismaService } from '@/prisma/prisma.service';
import { Action, Subject } from './casl.types';

export type AppAbility = PureAbility<[Action, Subject]>;

@Injectable()
export class CaslAbilityFactory {
    constructor(private prisma: PrismaService) { }

    async createForAdmin(adminId: string): Promise<AppAbility> {
        const { can, build } = new AbilityBuilder<AppAbility>(createMongoAbility);

        const admin = await this.prisma.admin.findUnique({
            where: { id: adminId },
        });

        if (!admin) {
            return build();
        }

        // ★ GOD MODE — Before Hook ★
        if (admin.isSuperAdmin) {
            can('manage', 'all');
            return build();
        }

        const mhr = await this.prisma.modelHasRole.findFirst({
            where: { modelId: admin.id, modelType: 'Admin' },
            include: {
                role: {
                    include: {
                        permissions: {
                            include: {
                                permission: true,
                            },
                        },
                    },
                },
            },
        });

        const role = mhr?.role;

        // Regular admin: build abilities from the database role
        if (role && role.permissions) {
            for (const rp of role.permissions) {
                can(rp.permission.action as Action, rp.permission.subject as Subject);
            }
        }

        return build();
    }
}
