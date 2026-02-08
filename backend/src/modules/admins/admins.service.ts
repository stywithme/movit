/**
 * Admins Service
 * ==============
 * 
 * Service for admin users management.
 */

import bcrypt from 'bcryptjs';
import { getPrisma } from '@/lib/prisma/client';
import type { CreateAdminInput, UpdateAdminInput } from './admins.types';

const SALT_ROUNDS = 12;

const adminSelect = {
  id: true,
  name: true,
  email: true,
  role: true,
  isActive: true,
  createdAt: true,
  updatedAt: true,
};

export const adminsService = {
  /**
   * Get admin by ID
   */
  async getById(id: string) {
    const prisma = await getPrisma();
    return prisma.admin.findUnique({
      where: { id, deletedAt: null },
      select: adminSelect,
    });
  },

  /**
   * List admins with optional filters and pagination
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

    const [admins, total] = await Promise.all([
      prisma.admin.findMany({
        where,
        skip,
        take: limit,
        orderBy: { createdAt: 'desc' },
        select: adminSelect,
      }),
      prisma.admin.count({ where }),
    ]);

    return {
      admins,
      pagination: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  },

  /**
   * Create a new admin
   */
  async create(data: CreateAdminInput) {
    const prisma = await getPrisma();

    const existing = await prisma.admin.findUnique({
      where: { email: data.email.toLowerCase() },
      select: { id: true },
    });

    if (existing) {
      throw new Error('Email already registered');
    }

    const hashedPassword = await bcrypt.hash(data.password, SALT_ROUNDS);

    return prisma.admin.create({
      data: {
        name: data.name,
        email: data.email.toLowerCase(),
        password: hashedPassword,
        role: data.role || 'admin',
      },
      select: adminSelect,
    });
  },

  /**
   * Update admin details or status
   */
  async update(id: string, data: UpdateAdminInput) {
    const prisma = await getPrisma();

    return prisma.admin.update({
      where: { id },
      data: {
        name: data.name,
        email: data.email ? data.email.toLowerCase() : undefined,
        role: data.role,
        isActive: data.isActive,
      },
      select: adminSelect,
    });
  },

  /**
   * Update admin password
   */
  async updatePassword(id: string, password: string) {
    const prisma = await getPrisma();
    const hashedPassword = await bcrypt.hash(password, SALT_ROUNDS);

    return prisma.admin.update({
      where: { id },
      data: { password: hashedPassword },
      select: adminSelect,
    });
  },

  /**
   * Activate or deactivate an admin
   */
  async setActive(id: string, isActive: boolean) {
    const prisma = await getPrisma();

    return prisma.admin.update({
      where: { id },
      data: { isActive },
      select: adminSelect,
    });
  },

  /**
   * Soft delete an admin
   */
  async delete(id: string) {
    const prisma = await getPrisma();

    return prisma.admin.update({
      where: { id },
      data: {
        deletedAt: new Date(),
        isActive: false,
      },
      select: adminSelect,
    });
  },
};
