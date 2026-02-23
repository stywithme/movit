import { Controller, Get, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { mobileSyncService } from './mobile-sync.service';
import type { ExploreRequestParams } from './mobile-sync.types';

@Controller('mobile/explore')
export class MobileExploreController {
  @Get()
  async getExplore(
    @Res({ passthrough: true }) res: Response,
    @Query('updatedAfter') updatedAfter?: string,
    @Query('limit') limit?: string
  ) {
    try {
      const params: ExploreRequestParams = {
        updatedAfter: updatedAfter || undefined,
        limit: limit ? Number.parseInt(limit, 10) : undefined,
      };

      const response = await mobileSyncService.getExplore(params);
      return response;
    } catch (error) {
      console.error('[Mobile Explore] Error:', error);
      res.status(500);
      return {
        success: false,
        error: 'Failed to fetch explore data',
        timestamp: new Date().toISOString(),
      };
    }
  }
}
