import { Module } from '@nestjs/common';
import { LevelProfileController } from './level-profile.controller';

@Module({
  controllers: [LevelProfileController],
})
export class LevelProfileModule {}
