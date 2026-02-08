import { Module } from '@nestjs/common';
import { MobileAuthController } from './mobile-auth.controller';

@Module({
  controllers: [MobileAuthController],
})
export class AuthModule {}
