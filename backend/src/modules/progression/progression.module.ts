import { Module } from '@nestjs/common';
import { ProgressionController } from './progression.controller';
import { ProgressionRulesAdminController } from './progression-rules-admin.controller';
import { ExerciseProgressionProfileController } from './exercise-progression-profile.controller';

@Module({
  controllers: [ProgressionController, ProgressionRulesAdminController, ExerciseProgressionProfileController],
})
export class ProgressionModule {}
