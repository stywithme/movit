import { Module } from '@nestjs/common';
import { ProgressionController } from './progression.controller';
import { ProgressionRulesAdminController } from './progression-rules-admin.controller';

@Module({
  controllers: [ProgressionController, ProgressionRulesAdminController],
})
export class ProgressionModule {}
