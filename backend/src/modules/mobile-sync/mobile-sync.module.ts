import { Module } from '@nestjs/common';
import { MobileSyncController } from './mobile-sync.controller';

@Module({
  controllers: [MobileSyncController],
})
export class MobileSyncModule {}
