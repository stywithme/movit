import { getPrisma } from '@/lib/prisma/client';
import type { CreateMessageInput, UpdateMessageInput } from './messages.types';

export const messagesService = {
  async list(options?: { includeInactive?: boolean; category?: string }) {
    const prisma = await getPrisma();
    const where: Record<string, unknown> = {};
    if (!options?.includeInactive) {
      where.isActive = true;
    }
    if (options?.category) {
      where.category = options.category;
    }
    return prisma.feedbackMessageTemplate.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });
  },

  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.findUnique({
      where: { id },
    });
  },

  async getByCode(code: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.findUnique({
      where: { code },
    });
  },

  async create(data: CreateMessageInput) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.create({
      data: {
        code: data.code,
        category: data.category,
        context: data.context || null,
        content: data.content as object,
        tags: data.tags || [],
        isSystem: data.isSystem ?? false,
        isActive: data.isActive ?? true,
      },
    });
  },

  async update(id: string, data: UpdateMessageInput) {
    const prisma = await getPrisma();
    const updateData: Record<string, unknown> = {
      updatedAt: new Date(),
    };

    if (data.code !== undefined) updateData.code = data.code;
    if (data.category !== undefined) updateData.category = data.category;
    if (data.context !== undefined) updateData.context = data.context || null;
    if (data.content !== undefined) updateData.content = data.content as object;
    if (data.tags !== undefined) updateData.tags = data.tags;
    if (data.isSystem !== undefined) updateData.isSystem = data.isSystem;
    if (data.isActive !== undefined) updateData.isActive = data.isActive;

    return prisma.feedbackMessageTemplate.update({
      where: { id },
      data: updateData,
    });
  },

  async delete(id: string) {
    const prisma = await getPrisma();
    return prisma.feedbackMessageTemplate.update({
      where: { id },
      data: { isActive: false },
    });
  },
};
