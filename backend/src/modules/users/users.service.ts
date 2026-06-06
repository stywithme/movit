/**
 * Users Service
 * =============
 * 
 * Service for admin users management (mobile app users).
 */

import bcrypt from 'bcryptjs';
import { getPrisma } from '@/lib/prisma/client';
import type { CreateUserInput, UpdateUserInput } from './users.types';

const SALT_ROUNDS = 12;

const userSelect = {
  id: true,
  name: true,
  email: true,
  avatarUrl: true,
  isActive: true,
  isPro: true,
  subscriptionExpiry: true,
  totalMinutes: true,
  totalWorkoutExecutions: true,
  createdAt: true,
  updatedAt: true,
};

export const usersService = {
  /**
   * List users with optional filters and pagination
   */
  async list(filters?: {
    isActive?: boolean;
    search?: string;
    page?: number;
    limit?: number;
  }) {
    const prisma = await getPrisma();
    const page = filters?.page || 1;
    const limit = filters?.limit || 20;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {
      deletedAt: null,
    };

    if (filters?.isActive !== undefined) {
      where.isActive = filters.isActive;
    }

    if (filters?.search) {
      where.OR = [
        { name: { contains: filters.search, mode: 'insensitive' } },
        { email: { contains: filters.search, mode: 'insensitive' } },
      ];
    }

    const [users, total] = await Promise.all([
      prisma.user.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        select: userSelect,
      }),
      prisma.user.count({ where }),
    ]);

    return {
      users,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  },

  /**
   * Create a new user with email/password
   */
  async create(data: CreateUserInput) {
    const prisma = await getPrisma();

    const existing = await prisma.user.findUnique({
      where: { email: data.email.toLowerCase() },
      select: { id: true },
    });

    if (existing) {
      throw new Error('Email already registered');
    }

    const hashedPassword = await bcrypt.hash(data.password, SALT_ROUNDS);

    return prisma.user.create({
      data: {
        name: data.name,
        email: data.email.toLowerCase(),
        password: hashedPassword,
        avatarUrl: data.avatarUrl || undefined,
        provider: 'email',
        isPro: data.isPro ?? false,
        subscriptionExpiry: data.subscriptionExpiry || undefined,
      },
      select: userSelect,
    });
  },

  /**
   * Update user details or status
   */
  async update(id: string, data: UpdateUserInput) {
    const prisma = await getPrisma();

    return prisma.user.update({
      where: { id },
      data: {
        name: data.name,
        email: data.email ? data.email.toLowerCase() : undefined,
        avatarUrl: data.avatarUrl === null ? null : data.avatarUrl,
        isPro: data.isPro,
        subscriptionExpiry: data.subscriptionExpiry === null ? null : data.subscriptionExpiry,
        isActive: data.isActive,
      },
      select: userSelect,
    });
  },

  /**
   * Activate or deactivate a user
   */
  async setActive(id: string, isActive: boolean) {
    const prisma = await getPrisma();

    if (!isActive) {
      await prisma.refreshToken.deleteMany({ where: { userId: id } });
    }

    return prisma.user.update({
      where: { id },
      data: { isActive },
      select: userSelect,
    });
  },

  /**
   * Soft delete a user
   */
  async delete(id: string) {
    const prisma = await getPrisma();

    await prisma.refreshToken.deleteMany({ where: { userId: id } });

    return prisma.user.update({
      where: { id },
      data: {
        deletedAt: new Date(),
        isActive: false,
      },
      select: userSelect,
    });
  },
};
