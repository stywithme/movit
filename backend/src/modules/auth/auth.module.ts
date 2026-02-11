import { Module } from '@nestjs/common';
import { MobileAuthController } from './mobile-auth.controller';
import { TokenCleanupService } from './token-cleanup.service';

@Module({
  controllers: [MobileAuthController],
  providers: [TokenCleanupService],
})
export class AuthModule {}
