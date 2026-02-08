import { Controller, Get, Query, Req, Res } from '@nestjs/common';
import type { Request, Response } from 'express';
import { mobileSyncService } from './mobile-sync.service';
import type { SyncRequestParams } from './mobile-sync.types';

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

      const response = await mobileSyncService.sync(params, baseUrl);
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
