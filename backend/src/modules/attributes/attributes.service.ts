import { getPrisma } from '@/lib/prisma/client';
import { Prisma } from '@prisma/client';
import type {
  CreateAttributeInput,
  CreateAttributeValueInput,
  UpdateAttributeInput,
  UpdateAttributeValueInput,
} from './attributes.types';

function toInputJson(value: unknown) {
  return value as Prisma.InputJsonValue;
}

function toInputJsonOrNull(
  value: unknown
): Prisma.InputJsonValue | Prisma.JsonNullValueInput {
  if (value === null || value === undefined) {
    return Prisma.JsonNull;
  }

  return value as Prisma.InputJsonValue;
}

export const attributesService = {
  async list(options?: { includeInactive?: boolean }) {
    const prisma = await getPrisma();
    const includeInactive = options?.includeInactive ?? false;

    return prisma.attribute.findMany({
      orderBy: { sortOrder: 'asc' },
      include: {
        values: includeInactive
          ? {
              orderBy: { sortOrder: 'asc' },
            }
          : {
              where: { isActive: true },
              orderBy: { sortOrder: 'asc' },
            },
      },
    });
  },

  async getAttributeById(id: string, options?: { includeInactive?: boolean }) {
    const prisma = await getPrisma();
    const includeInactive = options?.includeInactive ?? false;

    return prisma.attribute.findUnique({
      where: { id },
      include: {
        values: includeInactive
          ? {
              orderBy: { sortOrder: 'asc' },
            }
          : {
              where: { isActive: true },
              orderBy: { sortOrder: 'asc' },
            },
      },
    });
  },

  async getAttributeByCode(code: string, options?: { includeInactive?: boolean }) {
    const prisma = await getPrisma();
    const includeInactive = options?.includeInactive ?? false;

    return prisma.attribute.findUnique({
      where: { code },
      include: {
        values: includeInactive
          ? {
              orderBy: { sortOrder: 'asc' },
            }
          : {
              where: { isActive: true },
              orderBy: { sortOrder: 'asc' },
            },
      },
    });
  },

  async createAttribute(data: CreateAttributeInput) {
    const prisma = await getPrisma();
    const maxSortOrder = await prisma.attribute.aggregate({
      _max: { sortOrder: true },
    });

    return prisma.attribute.create({
      data: {
        code: data.code.trim(),
        name: toInputJson(data.name),
        description: data.description?.trim() || null,
        sortOrder: (maxSortOrder._max.sortOrder ?? 0) + 1,
        isSystem: false,
      },
    });
  },

  async updateAttribute(id: string, data: UpdateAttributeInput) {
    const prisma = await getPrisma();
    const attribute = await prisma.attribute.findUnique({
      where: { id },
    });

    if (!attribute) {
      throw new Error('Attribute not found');
    }

    if (attribute.isSystem) {
      throw new Error('Cannot modify system attribute');
    }

    const updateData: Record<string, unknown> = {};

    if (data.code !== undefined) {
      updateData.code = data.code.trim();
    }

    if (data.name !== undefined) {
      updateData.name = toInputJson(data.name);
    }

    if (data.description !== undefined) {
      updateData.description = data.description?.trim() || null;
    }

    return prisma.attribute.update({
      where: { id },
      data: updateData,
    });
  },

  async deleteAttribute(id: string) {
    const prisma = await getPrisma();
    const attribute = await prisma.attribute.findUnique({
      where: { id },
    });

    if (!attribute) {
      throw new Error('Attribute not found');
    }

    if (attribute.isSystem) {
      throw new Error('Cannot delete system attribute');
    }

    return prisma.attribute.delete({
      where: { id },
    });
  },

  async getAttributeValueById(id: string) {
    const prisma = await getPrisma();
    return prisma.attributeValue.findUnique({
      where: { id },
      include: {
        attribute: true,
      },
    });
  },

  async getAttributeValueByCode(code: string) {
    const prisma = await getPrisma();
    return prisma.attributeValue.findUnique({
      where: { code },
      include: {
        attribute: true,
      },
    });
  },

  async createValue(attributeCode: string, data: CreateAttributeValueInput) {
    const prisma = await getPrisma();
    const attribute = await prisma.attribute.findUnique({
      where: { code: attributeCode },
    });

    if (!attribute) {
      throw new Error('Attribute not found');
    }

    const maxSortOrder = await prisma.attributeValue.aggregate({
      where: { attributeId: attribute.id },
      _max: { sortOrder: true },
    });

    return prisma.attributeValue.create({
      data: {
        attributeId: attribute.id,
        code: data.code.trim(),
        name: toInputJson(data.name),
        description: toInputJsonOrNull(data.description),
        icon: data.icon?.trim() || null,
        color: data.color?.trim() || null,
        sortOrder: (maxSortOrder._max.sortOrder ?? 0) + 1,
        isActive: data.isActive ?? true,
      },
    });
  },

  async updateValue(id: string, data: UpdateAttributeValueInput) {
    const prisma = await getPrisma();
    const value = await prisma.attributeValue.findUnique({
      where: { id },
    });

    if (!value) {
      throw new Error('Attribute value not found');
    }

    const updateData: Record<string, unknown> = {};

    if (data.code !== undefined) {
      updateData.code = data.code.trim();
    }

    if (data.name !== undefined) {
      updateData.name = toInputJson(data.name);
    }

    if (data.description !== undefined) {
      updateData.description = toInputJsonOrNull(data.description);
    }

    if (data.icon !== undefined) {
      updateData.icon = data.icon?.trim() || null;
    }

    if (data.color !== undefined) {
      updateData.color = data.color?.trim() || null;
    }

    if (data.isActive !== undefined) {
      updateData.isActive = data.isActive;
    }

    return prisma.attributeValue.update({
      where: { id },
      data: updateData,
    });
  },

  async deleteValue(id: string) {
    const prisma = await getPrisma();
    const value = await prisma.attributeValue.findUnique({
      where: { id },
    });

    if (!value) {
      throw new Error('Attribute value not found');
    }

    return prisma.attributeValue.delete({
      where: { id },
    });
  },
};
