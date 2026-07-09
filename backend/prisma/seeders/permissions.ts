import type { PrismaClient } from '@prisma/client';

const legacyReportSubjectMap: Record<string, string> = {
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
    SafetyAnalytics: 'ReportSafety',
    ContentAnalytics: 'ReportContent',
};

const legacyEditActions = ['publish', 'duplicate'];

const permissions = [
    // Exercise
    { subject: 'Exercise', action: 'read' },
    { subject: 'Exercise', action: 'create' },
    { subject: 'Exercise', action: 'update' },
    { subject: 'Exercise', action: 'delete' },

    // Workout
    { subject: 'WorkoutTemplate', action: 'read' },
    { subject: 'WorkoutTemplate', action: 'create' },
    { subject: 'WorkoutTemplate', action: 'update' },
    { subject: 'WorkoutTemplate', action: 'delete' },

    // Workout Phase
    { subject: 'WorkoutPhase', action: 'read' },
    { subject: 'WorkoutPhase', action: 'create' },
    { subject: 'WorkoutPhase', action: 'update' },
    { subject: 'WorkoutPhase', action: 'delete' },

    // Program
    { subject: 'Program', action: 'read' },
    { subject: 'Program', action: 'create' },
    { subject: 'Program', action: 'update' },
    { subject: 'Program', action: 'delete' },

    // User
    { subject: 'User', action: 'read' },
    { subject: 'User', action: 'create' },
    { subject: 'User', action: 'update' },
    { subject: 'User', action: 'delete' },

    // Admin
    { subject: 'Admin', action: 'read' },
    { subject: 'Admin', action: 'create' },
    { subject: 'Admin', action: 'update' },
    { subject: 'Admin', action: 'delete' },

    // Role
    { subject: 'Role', action: 'read' },
    { subject: 'Role', action: 'create' },
    { subject: 'Role', action: 'update' },
    { subject: 'Role', action: 'delete' },

    // Program & ProgramMap
    { subject: 'ProgramMap', action: 'read' },

    // AssessmentTemplate
    { subject: 'AssessmentTemplate', action: 'read' },
    { subject: 'AssessmentTemplate', action: 'create' },
    { subject: 'AssessmentTemplate', action: 'update' },
    { subject: 'AssessmentTemplate', action: 'delete' },

    // Attribute
    { subject: 'Attribute', action: 'read' },
    { subject: 'Attribute', action: 'create' },
    { subject: 'Attribute', action: 'update' },
    { subject: 'Attribute', action: 'delete' },

    // PosePosition
    { subject: 'PosePosition', action: 'read' },
    { subject: 'PosePosition', action: 'create' },
    { subject: 'PosePosition', action: 'update' },
    { subject: 'PosePosition', action: 'delete' },

    // Level
    { subject: 'Level', action: 'read' },
    { subject: 'Level', action: 'update' },

    // FeedbackMessage // Assuming this is part of messages
    { subject: 'FeedbackMessage', action: 'read' },
    { subject: 'FeedbackMessage', action: 'create' },
    { subject: 'FeedbackMessage', action: 'update' },
    { subject: 'FeedbackMessage', action: 'delete' },

    // ProgressionRule
    { subject: 'ProgressionRule', action: 'read' },
    { subject: 'ProgressionRule', action: 'create' },
    { subject: 'ProgressionRule', action: 'update' },
    { subject: 'ProgressionRule', action: 'delete' },

    // Reports (admin analytics dashboard)
    { subject: 'ReportOverview', action: 'read' },
    { subject: 'ReportUsers', action: 'read' },
    { subject: 'ReportActivation', action: 'read' },
    { subject: 'ReportRetention', action: 'read' },
    { subject: 'ReportTraining', action: 'read' },
    { subject: 'ReportProgram', action: 'read' },
    { subject: 'ReportLevel', action: 'read' },
    { subject: 'ReportAssessment', action: 'read' },
    { subject: 'ReportProgression', action: 'read' },
    { subject: 'ReportRevenue', action: 'read' },
    { subject: 'ReportSafety', action: 'read' },
    { subject: 'ReportContent', action: 'read' },

    // Upload
    { subject: 'Upload', action: 'create' },
    { subject: 'Upload', action: 'delete' },

    // Plan
    { subject: 'Plan', action: 'create' },
    { subject: 'Plan', action: 'update' },
    { subject: 'Plan', action: 'delete' },

    // Subscription
    { subject: 'Subscription', action: 'read' },
    { subject: 'Subscription', action: 'create' },
    { subject: 'Subscription', action: 'update' },
    { subject: 'Subscription', action: 'delete' },

    // System Settings
    { subject: 'System', action: 'read' },
    { subject: 'System', action: 'update' },
];

export async function seedPermissions(prisma: PrismaClient) {
    let count = 0;
    for (const perm of permissions) {
        await prisma.permission.upsert({
            where: {
                subject_action: {
                    subject: perm.subject,
                    action: perm.action,
                },
            },
            update: {},
            create: perm,
        });
        count++;
    }

    for (const [legacySubject, reportSubject] of Object.entries(legacyReportSubjectMap)) {
        const legacyPermission = await prisma.permission.findUnique({
            where: { subject_action: { subject: legacySubject, action: 'read' } },
            include: { roles: true },
        });

        if (!legacyPermission) continue;

        const reportPermission = await prisma.permission.upsert({
            where: { subject_action: { subject: reportSubject, action: 'read' } },
            update: {},
            create: { subject: reportSubject, action: 'read' },
        });

        if (legacyPermission.roles.length > 0) {
            await prisma.rolePermission.createMany({
                data: legacyPermission.roles.map((rolePermission) => ({
                    roleId: rolePermission.roleId,
                    permissionId: reportPermission.id,
                })),
                skipDuplicates: true,
            });
        }

        await prisma.permission.delete({ where: { id: legacyPermission.id } });
    }

    for (const action of legacyEditActions) {
        const legacyPermissions = await prisma.permission.findMany({
            where: { action },
            include: { roles: true },
        });

        for (const legacyPermission of legacyPermissions) {
            const updatePermission = await prisma.permission.upsert({
                where: { subject_action: { subject: legacyPermission.subject, action: 'update' } },
                update: {},
                create: { subject: legacyPermission.subject, action: 'update' },
            });

            if (legacyPermission.roles.length > 0) {
                await prisma.rolePermission.createMany({
                    data: legacyPermission.roles.map((rolePermission) => ({
                        roleId: rolePermission.roleId,
                        permissionId: updatePermission.id,
                    })),
                    skipDuplicates: true,
                });
            }

            await prisma.permission.delete({ where: { id: legacyPermission.id } });
        }
    }

    await mirrorWorkoutPhasePermissions(prisma);

    console.log(`✅ Seeded ${count} permissions.`);
}

/**
 * Grant WorkoutPhase permissions to every role that already has the matching WorkoutTemplate permission.
 */
async function mirrorWorkoutPhasePermissions(prisma: PrismaClient) {
    const templatePermissions = await prisma.permission.findMany({
        where: { subject: 'WorkoutTemplate' },
        include: { roles: true },
    });

    let mirrored = 0;
    for (const templatePermission of templatePermissions) {
        const phasePermission = await prisma.permission.upsert({
            where: {
                subject_action: {
                    subject: 'WorkoutPhase',
                    action: templatePermission.action,
                },
            },
            update: {},
            create: {
                subject: 'WorkoutPhase',
                action: templatePermission.action,
            },
        });

        if (templatePermission.roles.length === 0) continue;

        const result = await prisma.rolePermission.createMany({
            data: templatePermission.roles.map((rolePermission) => ({
                roleId: rolePermission.roleId,
                permissionId: phasePermission.id,
            })),
            skipDuplicates: true,
        });
        mirrored += result.count;
    }

    if (mirrored > 0) {
        console.log(`✅ Mirrored ${mirrored} WorkoutPhase role permission(s) from WorkoutTemplate.`);
    }
}
