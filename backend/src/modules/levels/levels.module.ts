import { Module } from '@nestjs/common';
import { LevelsAdminController } from './levels-admin.controller';

@Module({
  controllers: [LevelsAdminController],
})
export class LevelsModule {}
