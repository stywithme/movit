import { Module } from '@nestjs/common';
import { MobileTrainingProfileController } from './mobile-training-profile.controller';

@Module({
  controllers: [MobileTrainingProfileController],
})
export class TrainingProfileModule {}
