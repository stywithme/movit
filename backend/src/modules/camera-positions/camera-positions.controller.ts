import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { cameraPositionService } from './camera-positions.service';
import type {
  CreateCameraPositionInput,
  UpdateCameraPositionInput,
} from './camera-positions.types';

@Controller('camera-positions')
export class CameraPositionsController {
  @Get()
  async list(@Query('includeInactive') includeInactive?: string) {
    try {
      const include = includeInactive === 'true';
      const cameraPositions = await cameraPositionService.list(include);

      const transformed = cameraPositions.map((cp: any) => ({
        id: cp.id,
        code: cp.code,
        name: cp.name,
        description: cp.description,
        imageUrl: cp.imageUrl,
        isActive: cp.isActive,
        sortOrder: cp.sortOrder,
        createdAt: cp.createdAt,
        updatedAt: cp.updatedAt,
        joints:
          cp.joints?.map((j: any) => ({
            id: j.joint.id,
            code: j.joint.code,
            name: j.joint.name,
          })) || [],
      }));

      return { success: true, data: transformed };
    } catch (error) {
      console.error('Error fetching camera positions:', error);
      return { success: false, error: 'Failed to fetch camera positions' };
    }
  }

  @Post()
  async create(@Body() body: CreateCameraPositionInput, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.code || !body?.name) {
        res.status(400);
        return { success: false, error: 'Code and name are required' };
      }

      if (!body.name.en && !body.name.ar) {
        res.status(400);
        return { success: false, error: 'Name must have at least English or Arabic value' };
      }

      const existing = await cameraPositionService.getByCode(body.code);
      if (existing) {
        res.status(409);
        return { success: false, error: 'Camera position with this code already exists' };
      }

      const cameraPosition = await cameraPositionService.create(body);

      const transformed = {
        id: cameraPosition.id,
        code: cameraPosition.code,
        name: cameraPosition.name,
        description: cameraPosition.description,
        imageUrl: cameraPosition.imageUrl,
        isActive: cameraPosition.isActive,
        sortOrder: cameraPosition.sortOrder,
        createdAt: cameraPosition.createdAt,
        updatedAt: cameraPosition.updatedAt,
        joints:
          (cameraPosition as any).joints?.map((j: any) => ({
            id: j.joint.id,
            code: j.joint.code,
            name: j.joint.name,
          })) || [],
      };

      res.status(201);
      return { success: true, data: transformed };
    } catch (error) {
      console.error('Error creating camera position:', error);
      res.status(500);
      return { success: false, error: 'Failed to create camera position' };
    }
  }

  @Get(':id')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const cameraPosition = await cameraPositionService.getById(id);

      if (!cameraPosition) {
        res.status(404);
        return { success: false, error: 'Camera position not found' };
      }

      const transformed = {
        id: cameraPosition.id,
        code: cameraPosition.code,
        name: cameraPosition.name,
        description: cameraPosition.description,
        imageUrl: cameraPosition.imageUrl,
        isActive: cameraPosition.isActive,
        sortOrder: cameraPosition.sortOrder,
        createdAt: cameraPosition.createdAt,
        updatedAt: cameraPosition.updatedAt,
        joints: cameraPosition.joints.map(j => ({
          id: j.joint.id,
          code: j.joint.code,
          name: j.joint.name,
        })),
      };

      return { success: true, data: transformed };
    } catch (error) {
      console.error('Error fetching camera position:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch camera position' };
    }
  }

  @Put(':id')
  async update(
    @Param('id') id: string,
    @Body() body: UpdateCameraPositionInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const existing = await cameraPositionService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Camera position not found' };
      }

      const cameraPosition = await cameraPositionService.update(id, body);
      const transformed = {
        id: cameraPosition.id,
        code: cameraPosition.code,
        name: cameraPosition.name,
        description: cameraPosition.description,
        imageUrl: cameraPosition.imageUrl,
        isActive: cameraPosition.isActive,
        sortOrder: cameraPosition.sortOrder,
        createdAt: cameraPosition.createdAt,
        updatedAt: cameraPosition.updatedAt,
        joints: cameraPosition.joints.map(j => ({
          id: j.joint.id,
          code: j.joint.code,
          name: j.joint.name,
        })),
      };

      return { success: true, data: transformed };
    } catch (error) {
      console.error('Error updating camera position:', error);
      res.status(500);
      return { success: false, error: 'Failed to update camera position' };
    }
  }

  @Delete(':id')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const existing = await cameraPositionService.getById(id);
      if (!existing) {
        res.status(404);
        return { success: false, error: 'Camera position not found' };
      }

      await cameraPositionService.delete(id);
      return { success: true, message: 'Camera position deleted successfully' };
    } catch (error) {
      console.error('Error deleting camera position:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete camera position' };
    }
  }
}
