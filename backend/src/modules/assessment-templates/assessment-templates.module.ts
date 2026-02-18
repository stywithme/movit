import { Module } from '@nestjs/common';
import { AssessmentTemplatesAdminController } from './assessment-templates-admin.controller';
import { AssessmentTemplatesMobileController } from './assessment-templates-mobile.controller';

@Module({
  controllers: [AssessmentTemplatesAdminController, AssessmentTemplatesMobileController],
})
export class AssessmentTemplatesModule {}
