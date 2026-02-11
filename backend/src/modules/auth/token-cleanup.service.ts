import { Injectable, Logger } from '@nestjs/common';
import { Cron, CronExpression } from '@nestjs/schedule';
import { authService } from './auth.service';

/**
 * Scheduled service to clean up expired refresh tokens.
 * Runs daily at 4 AM to prevent unbounded table growth.
 */
@Injectable()
export class TokenCleanupService {
  private readonly logger = new Logger(TokenCleanupService.name);

  @Cron(CronExpression.EVERY_DAY_AT_4AM)
  async handleTokenCleanup() {
    try {
      const deletedCount = await authService.cleanupExpiredTokens();
      if (deletedCount > 0) {
        this.logger.log(`Cleaned up ${deletedCount} expired refresh tokens`);
      }
    } catch (error) {
      this.logger.error('Failed to cleanup expired tokens', error);
    }
  }
}
