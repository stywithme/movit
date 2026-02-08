import { Module } from '@nestjs/common';
import { MobileSessionsController } from './mobile-sessions.controller';

@Module({
  controllers: [MobileSessionsController],
})
export class TrainingSessionsModule {}
