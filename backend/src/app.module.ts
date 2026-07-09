import { Module } from '@nestjs/common';
import { ThrottlerModule } from '@nestjs/throttler';
import { BullModule } from '@nestjs/bullmq';
import { ConfigModule } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';
import { PrismaModule } from './prisma/prisma.module';
import { AdminAuthModule } from './modules/admin-auth/admin-auth.module';
import { AdminsModule } from './modules/admins/admins.module';
import { UsersModule } from './modules/users/users.module';
import { AttributesModule } from './modules/attributes/attributes.module';
import { PosePositionsModule } from './modules/pose-positions/pose-positions.module';
import { AuthModule } from './modules/auth/auth.module';
import { ExercisesModule } from './modules/exercises/exercises.module';
import { WorkoutPhasesModule } from './modules/workout-phases/workout-phases.module';
import { WorkoutTemplatesModule } from './modules/workout-templates/workout-templates.module';
import { ProgramsModule } from './modules/programs/programs.module';
import { MessagesModule } from './modules/messages/messages.module';
import { WorkoutExecutionsModule } from './modules/workout-executions/workout-executions.module';
import { MobileSyncModule } from './modules/mobile-sync/mobile-sync.module';
import { UploadsModule } from './modules/uploads/uploads.module';
import { AiModule } from './modules/ai/ai.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';
import { ReportsModule } from './modules/reports/reports.module';
import { AssessmentModule } from './modules/assessment/assessment.module';
import { LevelProfileModule } from './modules/level-profile/level-profile.module';
import { PrescriptionModule } from './modules/prescription/prescription.module';
import { ActivePlanModule } from './modules/active-plan/active-plan.module';
import { ProgressionModule } from './modules/progression/progression.module';
import { ReassessmentModule } from './modules/reassessment/reassessment.module';
import { LevelsModule } from './modules/levels';
import { AssessmentTemplatesModule } from './modules/assessment-templates';
import { CaslModule } from './lib/casl/casl.module';
import { PermissionsModule } from './modules/permissions/permissions.module';
import { GuardsModule } from './lib/guards/guards.module';
import { SystemModule } from './modules/system/system.module';
import { PlanModule } from './modules/plan/plan.module';
import { SubscriptionModule } from './modules/subscription/subscription.module';
import { UserExercisePreferencesModule } from './modules/user-exercise-preferences/user-exercise-preferences.module';
import { TrainingProfileModule } from './modules/training-profile/training-profile.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
    ThrottlerModule.forRoot([
      {
        ttl: 60_000,
        limit: 30,
      },
    ]),
    ScheduleModule.forRoot(),
    BullModule.forRoot({
      connection: {
        host: process.env.REDIS_HOST || 'localhost',
        port: Number.parseInt(process.env.REDIS_PORT || '6379', 10),
        password: process.env.REDIS_PASSWORD || undefined,
      },
    }),
    PrismaModule,
    AdminAuthModule,
    AdminsModule,
    UsersModule,
    AttributesModule,
    PosePositionsModule,
    AuthModule,
    ExercisesModule,
    WorkoutPhasesModule,
    WorkoutTemplatesModule,
    ProgramsModule,
    MessagesModule,
    WorkoutExecutionsModule,
    MobileSyncModule,
    UploadsModule,
    AiModule,
    AnalyticsModule,
    ReportsModule,
    AssessmentModule,
    LevelProfileModule,
    PrescriptionModule,
    ActivePlanModule,
    ProgressionModule,
    ReassessmentModule,
    LevelsModule,
    AssessmentTemplatesModule,
    CaslModule,
    PermissionsModule,
    GuardsModule,
    SystemModule,
    PlanModule,
    SubscriptionModule,
    UserExercisePreferencesModule,
    TrainingProfileModule,
  ],
})
export class AppModule { }
