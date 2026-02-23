import { Module } from '@nestjs/common';
import { MobileSyncController } from './mobile-sync.controller';
import { MobileExploreController } from './mobile-explore.controller';
import { MobileHomeController } from './mobile-home.controller';

@Module({
  controllers: [MobileSyncController, MobileExploreController, MobileHomeController],
})
export class MobileSyncModule {}
