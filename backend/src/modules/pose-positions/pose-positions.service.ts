import { getPrisma } from '@/lib/prisma/client';
import { deleteByUrl } from '@/lib/storage';
import { UpdatePosePositionInput } from './pose-positions.types';

export const posePositionService = {
  async list(includeInactive = false) {
    const prisma = await getPrisma();
    const where = includeInactive ? {} : { isActive: true };

    return prisma.posePosition.findMany({
      where,
      orderBy: { sortOrder: 'asc' },
    });
  },

  async getById(id: string) {
    const prisma = await getPrisma();

    return prisma.posePosition.findUnique({
      where: { id },
    });
  },

  async update(id: string, data: UpdatePosePositionInput) {
    const prisma = await getPrisma();
    const existing = await prisma.posePosition.findUnique({
      where: { id },
      select: { imageUrl: true },
    });

    const updateData: Record<string, unknown> = { updatedAt: new Date() };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.imageUrl !== undefined) updateData.imageUrl = data.imageUrl;
    if (data.isActive !== undefined) updateData.isActive = data.isActive;

    const posePosition = await prisma.posePosition.update({
      where: { id },
      data: updateData,
    });

    // When image changes, touch all exercises that use this position
    // so mobile sync picks up the new image on next incremental sync
    if (data.imageUrl !== undefined && data.imageUrl !== existing?.imageUrl) {
      await prisma.exercise.updateMany({
        where: {
          poseVariants: { some: { posePositionId: id } },
          deletedAt: null,
        },
        data: { updatedAt: new Date() },
      });
    }

    if (data.imageUrl !== undefined && existing?.imageUrl && existing.imageUrl !== data.imageUrl) {
      await deleteByUrl(existing.imageUrl);
    }

    return posePosition;
  },
};
