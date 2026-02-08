import { Module } from '@nestjs/common';
import { BullModule } from '@nestjs/bullmq';
import { ConfigModule } from '@nestjs/config';
import { ScheduleModule } from '@nestjs/schedule';
import { PrismaModule } from './prisma/prisma.module';
import { AdminAuthModule } from './modules/admin-auth/admin-auth.module';
import { AdminsModule } from './modules/admins/admins.module';
import { UsersModule } from './modules/users/users.module';
import { AttributesModule } from './modules/attributes/attributes.module';
import { CameraPositionsModule } from './modules/camera-positions/camera-positions.module';
import { AuthModule } from './modules/auth/auth.module';
import { ExercisesModule } from './modules/exercises/exercises.module';
import { WorkoutsModule } from './modules/workouts/workouts.module';
import { MessagesModule } from './modules/messages/messages.module';
import { TrainingSessionsModule } from './modules/training-sessions/training-sessions.module';
import { MobileSyncModule } from './modules/mobile-sync/mobile-sync.module';
import { UploadsModule } from './modules/uploads/uploads.module';
import { AiModule } from './modules/ai/ai.module';
import { AnalyticsModule } from './modules/analytics/analytics.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true }),
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
    CameraPositionsModule,
    AuthModule,
    ExercisesModule,
    WorkoutsModule,
    MessagesModule,
    TrainingSessionsModule,
    MobileSyncModule,
    UploadsModule,
    AiModule,
    AnalyticsModule,
  ],
})
export class AppModule {}
