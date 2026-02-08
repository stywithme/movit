import { Module } from '@nestjs/common';
import { MobileProgramsController } from './mobile-programs.controller';
import { MobileUserProgramsController } from './mobile-user-programs.controller';
import { ProgramsController } from './programs.controller';

@Module({
  controllers: [ProgramsController, MobileProgramsController, MobileUserProgramsController],
})
export class ProgramsModule {}
