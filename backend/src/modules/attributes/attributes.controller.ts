import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { getPrisma } from '@/lib/prisma/client';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { attributesService } from './attributes.service';
import type {
  CreateAttributeInput,
  CreateAttributeValueInput,
  UpdateAttributeInput,
  UpdateAttributeValueInput,
} from './attributes.types';

@UseGuards(CaslGuard)
@Controller('attributes')
export class AttributesController {
  @Get()
  @CheckPermission('read', 'Attribute')
  async list(@Query('includeInactive') includeInactive?: string) {
    try {
      const attributes = await attributesService.list({
        includeInactive: includeInactive === 'true',
      });

      return { success: true, data: attributes };
    } catch (error) {
      console.error('Error fetching attributes:', error);
      return { success: false, error: 'Failed to fetch attributes' };
    }
  }

  @Get('lookup')
  @CheckPermission('read', 'Attribute')
  async lookup() {
    try {
      const attributes = await attributesService.list();
      const prisma = await getPrisma();

      const posePositions = await prisma.posePosition.findMany({
        where: { isActive: true },
        orderBy: { sortOrder: 'asc' },
      });

      const getValuesByCode = (code: string) => {
        const attr = attributes.find(a => a.code === code);
        return (attr?.values || []).map(v => ({
          id: v.id,
          code: v.code,
          name: v.name as { ar: string; en: string },
          description: v.description as { ar: string; en: string } | undefined,
          icon: v.icon,
          color: v.color,
        }));
      };

      const data = {
        categories: getValuesByCode('category'),
        countingMethods: getValuesByCode('counting_method'),
        joints: getValuesByCode('joint'),
        muscles: getValuesByCode('muscle'),
        equipment: getValuesByCode('equipment'),
        tags: getValuesByCode('tag'),
        posePositions: posePositions.map(pp => ({
          id: pp.id,
          code: pp.code,
          name: pp.name as { ar: string; en: string },
          description: pp.description as { ar: string; en: string } | undefined,
          imageUrl: pp.imageUrl,
          postures: (pp.postures as string[] | null) ?? [],
          directions: (pp.directions as string[] | null) ?? [],
          regions: (pp.regions as string[] | null) ?? [],
        })),
      };

      return { success: true, data };
    } catch (error) {
      console.error('Error fetching lookup data:', error);
      return { success: false, error: 'Failed to fetch lookup data' };
    }
  }

  @Post()
  @CheckPermission('create', 'Attribute')
  async create(
    @Body() body: CreateAttributeInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      if (!body?.code || !body?.name) {
        res.status(400);
        return { success: false, error: 'Code and name are required' };
      }

      const existing = await attributesService.getAttributeByCode(body.code, {
        includeInactive: true,
      });

      if (existing) {
        res.status(409);
        return { success: false, error: 'Attribute code already exists' };
      }

      const attribute = await attributesService.createAttribute(body);
      res.status(201);
      return { success: true, data: attribute };
    } catch (error) {
      console.error('Error creating attribute:', error);
      res.status(500);
      return { success: false, error: 'Failed to create attribute' };
    }
  }

  @Get(':code/values')
  @CheckPermission('read', 'Attribute')
  async getValues(
    @Param('code') code: string,
    @Res({ passthrough: true }) res: Response,
    @Query('includeInactive') includeInactive?: string
  ) {
    try {
      const attribute = await attributesService.getAttributeByCode(code, {
        includeInactive: includeInactive === 'true',
      });

      if (!attribute) {
        res.status(404);
        return { success: false, error: 'Attribute not found' };
      }

      return {
        success: true,
        data: {
          attribute: {
            id: attribute.id,
            code: attribute.code,
            name: attribute.name,
          },
          values: attribute.values,
        },
      };
    } catch (error) {
      console.error('Error fetching attribute values:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch attribute values' };
    }
  }

  @Post(':code/values')
  @CheckPermission('update', 'Attribute')
  async createValue(
    @Param('code') code: string,
    @Body() body: CreateAttributeValueInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      if (!body?.code || !body?.name) {
        res.status(400);
        return { success: false, error: 'Code and name are required' };
      }

      const existingValue = await attributesService.getAttributeValueByCode(body.code);

      if (existingValue) {
        res.status(409);
        return { success: false, error: 'Value code already exists' };
      }

      const newValue = await attributesService.createValue(code, body);

      res.status(201);
      return { success: true, data: newValue };
    } catch (error) {
      console.error('Error creating attribute value:', error);
      const message = getErrorMessage(error);
      if (message === 'Attribute not found') {
        res.status(404);
      } else {
        res.status(500);
      }
      return { success: false, error: message === 'Attribute not found' ? message : 'Failed to create attribute value' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'Attribute')
  async getById(
    @Param('id') id: string,
    @Res({ passthrough: true }) res: Response,
    @Query('includeInactive') includeInactive?: string
  ) {
    try {
      const attribute = await attributesService.getAttributeById(id, {
        includeInactive: includeInactive === 'true',
      });

      if (!attribute) {
        res.status(404);
        return { success: false, error: 'Attribute not found' };
      }

      return { success: true, data: attribute };
    } catch (error) {
      console.error('Error fetching attribute:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch attribute' };
    }
  }

  @Put('values/:id')
  @CheckPermission('update', 'Attribute')
  async updateValue(
    @Param('id') id: string,
    @Body() body: UpdateAttributeValueInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      if (body?.code) {
        const existing = await attributesService.getAttributeValueByCode(body.code);
        if (existing && existing.id !== id) {
          res.status(409);
          return { success: false, error: 'Value code already exists' };
        }
      }

      const value = await attributesService.updateValue(id, body);
      return { success: true, data: value };
    } catch (error) {
      console.error('Error updating attribute value:', error);
      const message = getErrorMessage(error);
      if (message === 'Attribute value not found') {
        res.status(404);
      } else {
        res.status(500);
      }
      return { success: false, error: message === 'Attribute value not found' ? message : 'Failed to update attribute value' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'Attribute')
  async update(
    @Param('id') id: string,
    @Body() body: UpdateAttributeInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      if (body?.code) {
        const existing = await attributesService.getAttributeByCode(body.code, {
          includeInactive: true,
        });

        if (existing && existing.id !== id) {
          res.status(409);
          return { success: false, error: 'Attribute code already exists' };
        }
      }

      const attribute = await attributesService.updateAttribute(id, body);
      return { success: true, data: attribute };
    } catch (error) {
      console.error('Error updating attribute:', error);
      const message = getErrorMessage(error);
      if (message === 'Attribute not found') {
        res.status(404);
      } else if (message === 'Cannot modify system attribute') {
        res.status(400);
      } else {
        res.status(500);
      }
      return { success: false, error: isKnownAttributeError(message) ? message : 'Failed to update attribute' };
    }
  }

  @Delete('values/:id')
  @CheckPermission('delete', 'Attribute')
  async deleteValue(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await attributesService.deleteValue(id);
      return { success: true, data: { deleted: true } };
    } catch (error) {
      console.error('Error deleting attribute value:', error);
      const message = getErrorMessage(error);
      if (message === 'Attribute value not found') {
        res.status(404);
      } else if (isPrismaForeignKeyError(error)) {
        res.status(400);
      } else {
        res.status(500);
      }
      return {
        success: false,
        error: message === 'Attribute value not found'
          ? message
          : isPrismaForeignKeyError(error)
            ? 'Cannot delete attribute value because it is in use'
            : 'Failed to delete attribute value',
      };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'Attribute')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await attributesService.deleteAttribute(id);
      return { success: true, data: { deleted: true } };
    } catch (error) {
      console.error('Error deleting attribute:', error);
      const message = getErrorMessage(error);
      if (message === 'Attribute not found') {
        res.status(404);
      } else if (
        message === 'Cannot delete system attribute' ||
        isPrismaForeignKeyError(error)
      ) {
        res.status(400);
      } else {
        res.status(500);
      }
      return {
        success: false,
        error: message === 'Attribute not found'
          ? message
          : message === 'Cannot delete system attribute'
            ? message
            : isPrismaForeignKeyError(error)
              ? 'Cannot delete attribute because some values are still in use'
              : 'Failed to delete attribute',
      };
    }
  }
}

function getErrorMessage(error: unknown) {
  if (error instanceof Error) {
    return error.message;
  }
  return 'Unknown error';
}

function isKnownAttributeError(message: string) {
  return [
    'Attribute not found',
    'Cannot modify system attribute',
  ].includes(message);
}

function isPrismaForeignKeyError(error: unknown) {
  return typeof error === 'object' && error !== null && 'code' in error && error.code === 'P2003';
}
