import { Module } from '@nestjs/common';
import { CameraPositionsController } from './camera-positions.controller';

@Module({
  controllers: [CameraPositionsController],
})
export class CameraPositionsModule {}
