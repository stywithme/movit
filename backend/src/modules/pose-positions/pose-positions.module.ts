import { Module } from '@nestjs/common';
import { PosePositionsController } from './pose-positions.controller';

@Module({
  controllers: [PosePositionsController],
})
export class PosePositionsModule {}
