import { Body, Controller, Get, Param, Post, Res } from '@nestjs/common';
import type { Response } from 'express';
import { getPrisma } from '@/lib/prisma/client';

@Controller('attributes')
export class AttributesController {
  @Get()
  async list() {
    try {
      const prisma = await getPrisma();
      const attributes = await prisma.attribute.findMany({
        orderBy: { sortOrder: 'asc' },
        include: {
          values: {
            where: { isActive: true },
            orderBy: { sortOrder: 'asc' },
          },
        },
      });

      return { success: true, data: attributes };
    } catch (error) {
      console.error('Error fetching attributes:', error);
      return { success: false, error: 'Failed to fetch attributes' };
    }
  }

  @Get('lookup')
  async lookup() {
    try {
      const prisma = await getPrisma();
      const attributes = await prisma.attribute.findMany({
        include: {
          values: {
            where: { isActive: true },
            orderBy: { sortOrder: 'asc' },
          },
        },
      });

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

  @Get(':code/values')
  async getValues(@Param('code') code: string, @Res({ passthrough: true }) res: Response) {
    try {
      const prisma = await getPrisma();
      const attribute = await prisma.attribute.findUnique({
        where: { code },
        include: {
          values: {
            where: { isActive: true },
            orderBy: { sortOrder: 'asc' },
          },
        },
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
  async createValue(
    @Param('code') code: string,
    @Body() body: any,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      const prisma = await getPrisma();
      const attribute = await prisma.attribute.findUnique({ where: { code } });

      if (!attribute) {
        res.status(404);
        return { success: false, error: 'Attribute not found' };
      }

      if (!body?.code || !body?.name) {
        res.status(400);
        return { success: false, error: 'Code and name are required' };
      }

      const existingValue = await prisma.attributeValue.findUnique({
        where: { code: body.code },
      });

      if (existingValue) {
        res.status(409);
        return { success: false, error: 'Value code already exists' };
      }

      const maxSortOrder = await prisma.attributeValue.aggregate({
        where: { attributeId: attribute.id },
        _max: { sortOrder: true },
      });

      const newValue = await prisma.attributeValue.create({
        data: {
          attributeId: attribute.id,
          code: body.code,
          name: body.name,
          description: body.description || null,
          icon: body.icon || null,
          color: body.color || null,
          sortOrder: (maxSortOrder._max.sortOrder || 0) + 1,
          isActive: body.isActive ?? true,
        },
      });

      res.status(201);
      return { success: true, data: newValue };
    } catch (error) {
      console.error('Error creating attribute value:', error);
      res.status(500);
      return { success: false, error: 'Failed to create attribute value' };
    }
  }
}
