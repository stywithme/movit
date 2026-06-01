import type { PrismaClient } from '@prisma/client';

const permissions = [
    // Exercise
    { subject: 'Exercise', action: 'read' },
    { subject: 'Exercise', action: 'create' },
    { subject: 'Exercise', action: 'update' },
    { subject: 'Exercise', action: 'delete' },

    // Workout
    { subject: 'Workout', action: 'read' },
    { subject: 'Workout', action: 'create' },
    { subject: 'Workout', action: 'update' },
    { subject: 'Workout', action: 'delete' },

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
    { subject: 'ReportBooking', action: 'read' },
    { subject: 'ReportSafety', action: 'read' },
    { subject: 'ReportContent', action: 'read' },

    // Upload
    { subject: 'Upload', action: 'create' },
    { subject: 'Upload', action: 'delete' },

    // DoctorWorkTime
    { subject: 'DoctorWorkTime', action: 'read' },
    { subject: 'DoctorWorkTime', action: 'create' },
    { subject: 'DoctorWorkTime', action: 'update' },
    { subject: 'DoctorWorkTime', action: 'delete' },

    // CloseTime
    { subject: 'CloseTime', action: 'read' },
    { subject: 'CloseTime', action: 'create' },
    { subject: 'CloseTime', action: 'update' },
    { subject: 'CloseTime', action: 'delete' },

    // Booking
    { subject: 'Booking', action: 'read' },
    { subject: 'Booking', action: 'create' },
    { subject: 'Booking', action: 'update' },
    { subject: 'Booking', action: 'delete' },

    // BookingReport
    { subject: 'BookingReport', action: 'read' },
    { subject: 'BookingReport', action: 'create' },
    { subject: 'BookingReport', action: 'update' },
    { subject: 'BookingReport', action: 'delete' },

    // Plan
    { subject: 'Plan', action: 'read' },
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
    console.log(`✅ Seeded ${count} permissions.`);
}
