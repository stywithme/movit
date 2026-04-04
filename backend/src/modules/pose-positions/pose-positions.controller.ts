import { Body, Controller, Get, Param, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { posePositionService } from './pose-positions.service';
import type { UpdatePosePositionInput } from './pose-positions.types';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

@UseGuards(CaslGuard)
@Controller(['pose-positions', 'camera-positions'])
export class PosePositionsController {
  @Get()
  @CheckPermission('read', 'PosePosition')
  async list(@Query('includeInactive') includeInactive?: string) {
    try {
      const include = includeInactive === 'true';
      const positions = await posePositionService.list(include);

      const transformed = positions.map((pp: any) => ({
        id: pp.id,
        code: pp.code,
        name: pp.name,
        imageUrl: pp.imageUrl,
        isActive: pp.isActive,
        sortOrder: pp.sortOrder,
        postures: pp.postures,
        directions: pp.directions,
        regions: pp.regions,
        createdAt: pp.createdAt,
        updatedAt: pp.updatedAt,
      }));

      return { success: true, data: transformed };
    } catch (error) {
      console.error('Error fetching pose positions:', error);
      return { success: false, error: 'Failed to fetch pose positions' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'PosePosition')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const position = await posePositionService.getById(id);
      if (!position) {
        res.status(404);
        return { success: false, error: 'Pose position not found' };
      }

      return {
        success: true,
        data: {
          id: position.id,
          code: position.code,
          name: position.name,
          imageUrl: position.imageUrl,
          isActive: position.isActive,
          sortOrder: position.sortOrder,
          postures: position.postures,
          directions: position.directions,
          regions: position.regions,
          createdAt: position.createdAt,
          updatedAt: position.updatedAt,
        },
      };
    } catch (error) {
      console.error('Error fetching pose position:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch pose position' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'PosePosition')
  async update(
    @Param('id') id: string,
    @Body() body: UpdatePosePositionInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const existing = await posePositionService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Pose position not found' };
      }

      const position = await posePositionService.update(id, body);
      return {
        success: true,
        data: {
          id: position.id,
          code: position.code,
          name: position.name,
          imageUrl: position.imageUrl,
          isActive: position.isActive,
          sortOrder: position.sortOrder,
          createdAt: position.createdAt,
          updatedAt: position.updatedAt,
        },
      };
    } catch (error) {
      console.error('Error updating pose position:', error);
      res.status(500);
      return { success: false, error: 'Failed to update pose position' };
    }
  }
}
