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

        // Hardcoded Doctor Permissions
        if (admin.isDoctor) {
            can('read', 'Booking');
            can('read', 'BookingReport');
            can('create', 'BookingReport');
            can('update', 'BookingReport');
            can('read', 'DoctorWorkTime');
            can('manage', 'DoctorWorkTime');
            can('manage', 'CloseTime');
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
            const knownActions: Action[] = ['manage', 'read', 'create', 'update', 'delete'];
            const legacyReportSubjects: Record<string, Subject> = {
                Analytics: 'ReportOverview',
                OverviewAnalytics: 'ReportOverview',
                UserAnalytics: 'ReportUsers',
                ActivationAnalytics: 'ReportActivation',
                EngagementAnalytics: 'ReportRetention',
                TrainingAnalytics: 'ReportTraining',
                ProgramAnalytics: 'ReportProgram',
                LevelAnalytics: 'ReportLevel',
                AssessmentAnalytics: 'ReportAssessment',
                ProgressionAnalytics: 'ReportProgression',
                RevenueAnalytics: 'ReportRevenue',
                BookingAnalytics: 'ReportBooking',
                SafetyAnalytics: 'ReportSafety',
                ContentAnalytics: 'ReportContent',
            };

            for (const rp of role.permissions) {
                const { action, subject } = rp.permission;
                const sub = subject as Subject;

                // Legacy DB rows: publish/duplicate are treated as update (edit)
                if (action === 'publish' || action === 'duplicate') {
                    can('update', sub);
                    continue;
                }

                if (knownActions.includes(action as Action)) {
                    can(action as Action, sub);
                }

                const migratedReport = legacyReportSubjects[subject];
                if (migratedReport && action === 'read') {
                    can('read', migratedReport);
                }
            }
        }

        return build();
    }
}
