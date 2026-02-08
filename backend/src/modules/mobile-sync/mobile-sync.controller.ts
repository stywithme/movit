import { Controller, Get, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { mobileSyncService } from './mobile-sync.service';
import type { SyncRequestParams } from './mobile-sync.types';
import { verifyMobileToken } from '@/modules/auth/auth.service';

@Controller('mobile/sync')
export class MobileSyncController {
  @Get()
  async sync(
    @Req() req: Request,
    @Res({ passthrough: true }) res: Response,
    @Query('updatedAfter') updatedAfter?: string,
    @Query('forceRefresh') forceRefresh?: string
  ) {
    try {
      const params: SyncRequestParams = {
        updatedAfter: updatedAfter || undefined,
        forceRefresh: forceRefresh === 'true',
      };

      const protocol = (req.headers['x-forwarded-proto'] as string) || req.protocol || 'http';
      const host = req.headers.host || 'localhost:3000';
      const baseUrl = `${protocol}://${host}`;

      let userId: string | null = null;
      const authHeader = req.headers.authorization;
      if (authHeader) {
        const authResult = await verifyMobileToken(req);
        if (!authResult.success || !authResult.userId) {
          res.status(401);
          return { success: false, error: authResult.error || 'Unauthorized', timestamp: new Date().toISOString() };
        }
        userId = authResult.userId;
      }

      const response = await mobileSyncService.sync(params, baseUrl, userId);
      return response;
    } catch (error) {
      console.error('[Mobile Sync] Error:', error);
      res.status(500);
      return {
        success: false,
        error: 'Failed to sync exercises',
        timestamp: new Date().toISOString(),
      };
    }
  }
}
