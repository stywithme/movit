import { getPrisma } from '@/lib/prisma/client';
import { deleteByUrl } from '@/lib/storage';
import { CreatePosePositionInput, UpdatePosePositionInput } from './pose-positions.types';

export const posePositionService = {
  async list(includeInactive = false) {
    const prisma = await getPrisma();
    const where = includeInactive ? {} : { isActive: true };

    return prisma.posePosition.findMany({
      where,
      orderBy: { sortOrder: 'asc' },
      include: {
        joints: {
          include: { joint: true },
        },
      },
    });
  },

  async getById(id: string) {
    const prisma = await getPrisma();

    return prisma.posePosition.findUnique({
      where: { id },
      include: {
        joints: {
          include: { joint: true },
        },
      },
    });
  },

  async getByCode(code: string) {
    const prisma = await getPrisma();

    return prisma.posePosition.findUnique({
      where: { code },
      include: {
        joints: {
          include: { joint: true },
        },
      },
    });
  },

  async create(data: CreatePosePositionInput) {
    const prisma = await getPrisma();

    const maxSortOrder = await prisma.posePosition.aggregate({
      _max: { sortOrder: true },
    });

    const posePosition = await prisma.posePosition.create({
      data: {
        code: data.code,
        name: data.name,
        description: data.description || undefined,
        imageUrl: data.imageUrl || undefined,
        postures: data.postures ?? ['any'],
        directions: data.directions ?? ['any'],
        regions: data.regions ?? ['any'],
        sortOrder: (maxSortOrder._max.sortOrder || 0) + 1,
        joints: {
          create: data.jointIds.map((jointId) => ({ jointId })),
        },
      },
      include: {
        joints: {
          include: { joint: true },
        },
      },
    });

    return posePosition;
  },

  async update(id: string, data: UpdatePosePositionInput) {
    const prisma = await getPrisma();
    const existing = await prisma.posePosition.findUnique({
      where: { id },
      select: { imageUrl: true },
    });

    const updateData: Record<string, unknown> = { updatedAt: new Date() };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.imageUrl !== undefined) updateData.imageUrl = data.imageUrl;
    if (data.isActive !== undefined) updateData.isActive = data.isActive;
    if (data.sortOrder !== undefined) updateData.sortOrder = data.sortOrder;
    if (data.postures !== undefined) updateData.postures = data.postures;
    if (data.directions !== undefined) updateData.directions = data.directions;
    if (data.regions !== undefined) updateData.regions = data.regions;

    if (data.jointIds !== undefined) {
      await prisma.posePositionJoint.deleteMany({ where: { posePositionId: id } });
      if (data.jointIds.length > 0) {
        await prisma.posePositionJoint.createMany({
          data: data.jointIds.map((jointId) => ({ posePositionId: id, jointId })),
        });
      }
    }

    const posePosition = await prisma.posePosition.update({
      where: { id },
      data: updateData,
      include: {
        joints: {
          include: { joint: true },
        },
      },
    });

    if (data.imageUrl !== undefined && existing?.imageUrl && existing.imageUrl !== data.imageUrl) {
      await deleteByUrl(existing.imageUrl);
    }

    return posePosition;
  },

  async delete(id: string) {
    const prisma = await getPrisma();
    return prisma.posePosition.update({
      where: { id },
      data: { isActive: false },
    });
  },

  async hardDelete(id: string) {
    const prisma = await getPrisma();
    const existing = await prisma.posePosition.findUnique({
      where: { id },
      select: { imageUrl: true },
    });

    await prisma.posePositionJoint.deleteMany({ where: { posePositionId: id } });
    const deleted = await prisma.posePosition.delete({ where: { id } });

    if (existing?.imageUrl) {
      await deleteByUrl(existing.imageUrl);
    }

    return deleted;
  },
};
