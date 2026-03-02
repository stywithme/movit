import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { posePositionService } from './pose-positions.service';
import type { CreatePosePositionInput, UpdatePosePositionInput } from './pose-positions.types';

@Controller('pose-positions')
export class PosePositionsController {
  @Get()
  async list(@Query('includeInactive') includeInactive?: string) {
    try {
      const include = includeInactive === 'true';
      const positions = await posePositionService.list(include);

      const transformed = positions.map((pp: any) => ({
        id: pp.id,
        code: pp.code,
        name: pp.name,
        description: pp.description,
        imageUrl: pp.imageUrl,
        isActive: pp.isActive,
        sortOrder: pp.sortOrder,
        createdAt: pp.createdAt,
        updatedAt: pp.updatedAt,
        joints: pp.joints?.map((j: any) => ({
          id: j.joint.id,
          code: j.joint.code,
          name: j.joint.name,
        })) || [],
      }));

      return { success: true, data: transformed };
    } catch (error) {
      console.error('Error fetching pose positions:', error);
      return { success: false, error: 'Failed to fetch pose positions' };
    }
  }

  @Post()
  async create(@Body() body: CreatePosePositionInput, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.code || !body?.name) {
        res.status(400);
        return { success: false, error: 'Code and name are required' };
      }

      const existing = await posePositionService.getByCode(body.code);
      if (existing) {
        res.status(409);
        return { success: false, error: 'Pose position with this code already exists' };
      }

      const position = await posePositionService.create(body);

      res.status(201);
      return {
        success: true,
        data: {
          id: position.id,
          code: position.code,
          name: position.name,
          description: position.description,
          imageUrl: position.imageUrl,
          isActive: position.isActive,
          sortOrder: position.sortOrder,
          createdAt: position.createdAt,
          updatedAt: position.updatedAt,
          joints: (position as any).joints?.map((j: any) => ({
            id: j.joint.id,
            code: j.joint.code,
            name: j.joint.name,
          })) || [],
        },
      };
    } catch (error) {
      console.error('Error creating pose position:', error);
      res.status(500);
      return { success: false, error: 'Failed to create pose position' };
    }
  }

  @Get(':id')
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
          description: position.description,
          imageUrl: position.imageUrl,
          isActive: position.isActive,
          sortOrder: position.sortOrder,
          createdAt: position.createdAt,
          updatedAt: position.updatedAt,
          joints: position.joints.map((j) => ({
            id: j.joint.id,
            code: j.joint.code,
            name: j.joint.name,
          })),
        },
      };
    } catch (error) {
      console.error('Error fetching pose position:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch pose position' };
    }
  }

  @Put(':id')
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
          description: position.description,
          imageUrl: position.imageUrl,
          isActive: position.isActive,
          sortOrder: position.sortOrder,
          createdAt: position.createdAt,
          updatedAt: position.updatedAt,
          joints: position.joints.map((j) => ({
            id: j.joint.id,
            code: j.joint.code,
            name: j.joint.name,
          })),
        },
      };
    } catch (error) {
      console.error('Error updating pose position:', error);
      res.status(500);
      return { success: false, error: 'Failed to update pose position' };
    }
  }

  @Delete(':id')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const existing = await posePositionService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Pose position not found' };
      }

      await posePositionService.delete(id);
      return { success: true, message: 'Pose position deleted successfully' };
    } catch (error) {
      console.error('Error deleting pose position:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete pose position' };
    }
  }
}
