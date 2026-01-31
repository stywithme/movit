import { getPrisma } from '@/lib/prisma/client';
import { deleteByUrl } from '@/lib/storage';
import { CreateCameraPositionInput, UpdateCameraPositionInput } from './camera-positions.types';

/**
 * Camera Position Service - handles all camera position-related database operations
 */
export const cameraPositionService = {
  /**
   * List all camera positions
   */
  async list(includeInactive = false) {
    const prisma = await getPrisma();
    
    const where = includeInactive ? {} : { isActive: true };
    
    return prisma.cameraPosition.findMany({
      where,
      orderBy: { sortOrder: 'asc' },
      include: {
        joints: {
          include: {
            joint: true,
          },
        },
      },
    });
  },

  /**
   * Get a single camera position by ID
   */
  async getById(id: string) {
    const prisma = await getPrisma();
    
    return prisma.cameraPosition.findUnique({
      where: { id },
      include: {
        joints: {
          include: {
            joint: true,
          },
        },
      },
    });
  },

  /**
   * Get a single camera position by code
   */
  async getByCode(code: string) {
    const prisma = await getPrisma();
    
    return prisma.cameraPosition.findUnique({
      where: { code },
      include: {
        joints: {
          include: {
            joint: true,
          },
        },
      },
    });
  },

  /**
   * Create a new camera position
   */
  async create(data: CreateCameraPositionInput) {
    const prisma = await getPrisma();
    
    // Get max sort order
    const maxSortOrder = await prisma.cameraPosition.aggregate({
      _max: { sortOrder: true },
    });
    
    const cameraPosition = await prisma.cameraPosition.create({
      data: {
        code: data.code,
        name: data.name,
        description: data.description || undefined,
        imageUrl: data.imageUrl || undefined,
        sortOrder: (maxSortOrder._max.sortOrder || 0) + 1,
        joints: {
          create: data.jointIds.map((jointId) => ({
            jointId,
          })),
        },
      },
      include: {
        joints: {
          include: {
            joint: true,
          },
        },
      },
    });
    
    return cameraPosition;
  },

  /**
   * Update a camera position
   */
  async update(id: string, data: UpdateCameraPositionInput) {
    const prisma = await getPrisma();
    const existing = await prisma.cameraPosition.findUnique({
      where: { id },
      select: { imageUrl: true },
    });
    
    const updateData: Record<string, unknown> = {
      updatedAt: new Date(),
    };
    
    if (data.name !== undefined) updateData.name = data.name;
    if (data.description !== undefined) updateData.description = data.description;
    if (data.imageUrl !== undefined) updateData.imageUrl = data.imageUrl;
    if (data.isActive !== undefined) updateData.isActive = data.isActive;
    if (data.sortOrder !== undefined) updateData.sortOrder = data.sortOrder;
    
    // Update joints if provided
    if (data.jointIds !== undefined) {
      // Delete existing joints
      await prisma.cameraPositionJoint.deleteMany({
        where: { cameraPositionId: id },
      });
      
      // Add new joints
      if (data.jointIds.length > 0) {
        await prisma.cameraPositionJoint.createMany({
          data: data.jointIds.map((jointId) => ({
            cameraPositionId: id,
            jointId,
          })),
        });
      }
    }
    
    const cameraPosition = await prisma.cameraPosition.update({
      where: { id },
      data: updateData,
      include: {
        joints: {
          include: {
            joint: true,
          },
        },
      },
    });

    if (data.imageUrl !== undefined && existing?.imageUrl && existing.imageUrl !== data.imageUrl) {
      await deleteByUrl(existing.imageUrl);
    }
    
    return cameraPosition;
  },

  /**
   * Delete a camera position (soft delete by setting isActive = false)
   */
  async delete(id: string) {
    const prisma = await getPrisma();
    
    return prisma.cameraPosition.update({
      where: { id },
      data: {
        isActive: false,
      },
    });
  },

  /**
   * Hard delete a camera position
   */
  async hardDelete(id: string) {
    const prisma = await getPrisma();
    const existing = await prisma.cameraPosition.findUnique({
      where: { id },
      select: { imageUrl: true },
    });
    
    // Delete joints first
    await prisma.cameraPositionJoint.deleteMany({
      where: { cameraPositionId: id },
    });
    
    const deleted = await prisma.cameraPosition.delete({
      where: { id },
    });
    
    if (existing?.imageUrl) {
      await deleteByUrl(existing.imageUrl);
    }

    return deleted;
  },
};


