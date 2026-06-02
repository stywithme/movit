/**
 * Admins Service
 * ==============
 * 
 * Service for admin users management.
 */

import bcrypt from 'bcryptjs';
import { ForbiddenException } from '@nestjs/common';
import { getPrisma } from '@/lib/prisma/client';
import type { CreateAdminInput, UpdateAdminInput } from './admins.types';

const SALT_ROUNDS = 12;

const adminSelect = {
  id: true,
  name: true,
  email: true,
  isSuperAdmin: true,
  isDoctor: true,
  isActive: true,
  createdAt: true,
  updatedAt: true,
};

async function withRole(admin: any) {
  if (!admin) return admin;
  const prisma = await getPrisma();
  const mhr = await prisma.modelHasRole.findFirst({
    where: { modelId: admin.id, modelType: 'Admin' },
    include: { role: { select: { id: true, name: true } } }
  });
  return {
    ...admin,
    roleId: mhr?.roleId || null,
    role: mhr?.role || null,
  };
}

async function prepareAdminsList(admins: any[]) {
  if (!admins.length) return admins;
  const prisma = await getPrisma();
  const mhrs = await prisma.modelHasRole.findMany({
    where: { modelId: { in: admins.map(a => a.id) }, modelType: 'Admin' },
    include: { role: { select: { id: true, name: true } } }
  });
  return admins.map(admin => {
    const mhr = mhrs.find(m => m.modelId === admin.id);
    return {
      ...admin,
      roleId: mhr?.roleId || null,
      role: mhr?.role || null,
    };
  });
}

async function assertCanAccessAdmin(id: string, canAccessSuperAdmin: boolean) {
  const prisma = await getPrisma();
  const admin = await prisma.admin.findUnique({
    where: { id, deletedAt: null },
    select: { id: true, isSuperAdmin: true },
  });

  if (admin?.isSuperAdmin && !canAccessSuperAdmin) {
    throw new ForbiddenException('Super admin accounts are only visible and manageable by Super Admins');
  }

  return admin;
}

export const adminsService = {
  /**
   * Get admin by ID
   */
  async getById(id: string, canAccessSuperAdmin = false) {
    try {
      await assertCanAccessAdmin(id, canAccessSuperAdmin);
    } catch (error) {
      if (error instanceof ForbiddenException) return null;
      throw error;
    }

    const prisma = await getPrisma();
    const admin = await prisma.admin.findUnique({
      where: { id, deletedAt: null },
      select: adminSelect,
    });
    return withRole(admin);
  },

  /**
   * List admins with optional filters and pagination
   */
  async list(filters?: {
    isActive?: boolean;
    isDoctor?: boolean;
    search?: string;
    page?: number;
    limit?: number;
    includeSuperAdmins?: boolean;
  }) {
    const prisma = await getPrisma();
    const page = filters?.page || 1;
    const limit = filters?.limit || 20;
    const skip = (page - 1) * limit;

    const where: Record<string, unknown> = {
      deletedAt: null,
    };

    if (!filters?.includeSuperAdmins) {
      where.isSuperAdmin = false;
    }

    if (filters?.isActive !== undefined) {
      where.isActive = filters.isActive;
    }

    if (filters?.isDoctor !== undefined) {
      where.isDoctor = filters.isDoctor;
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

    const adminsWithRoles = await prepareAdminsList(admins);

    return {
      admins: adminsWithRoles,
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

    const admin = await prisma.admin.create({
      data: {
        name: data.name,
        email: data.email.toLowerCase(),
        password: hashedPassword,
        isDoctor: data.isDoctor ?? false,
      },
      select: adminSelect,
    });
    if (data.roleId) {
      await prisma.modelHasRole.create({
        data: { roleId: data.roleId, modelId: admin.id, modelType: 'Admin' }
      });
    }
    return withRole(admin);
  },

  /**
   * Update admin details or status
   */
  async update(id: string, data: UpdateAdminInput, canAccessSuperAdmin = false) {
    await assertCanAccessAdmin(id, canAccessSuperAdmin);

    const prisma = await getPrisma();

    const admin = await prisma.admin.update({
      where: { id },
      data: {
        name: data.name,
        email: data.email ? data.email.toLowerCase() : undefined,
        isActive: data.isActive,
        isDoctor: data.isDoctor,
      },
      select: adminSelect,
    });

    if (data.roleId !== undefined) {
      await prisma.modelHasRole.deleteMany({
        where: { modelId: id, modelType: 'Admin' }
      });
      if (data.roleId) {
        await prisma.modelHasRole.create({
          data: { roleId: data.roleId, modelId: id, modelType: 'Admin' }
        });
      }
    }

    return withRole(admin);
  },

  /**
   * Update admin password
   */
  async updatePassword(id: string, password: string, canAccessSuperAdmin = false) {
    await assertCanAccessAdmin(id, canAccessSuperAdmin);

    const prisma = await getPrisma();
    const hashedPassword = await bcrypt.hash(password, SALT_ROUNDS);

    const admin = await prisma.admin.update({
      where: { id },
      data: { password: hashedPassword },
      select: adminSelect,
    });
    return withRole(admin);
  },

  /**
   * Activate or deactivate an admin
   */
  async setActive(id: string, isActive: boolean, canAccessSuperAdmin = false) {
    await assertCanAccessAdmin(id, canAccessSuperAdmin);

    const prisma = await getPrisma();

    const admin = await prisma.admin.update({
      where: { id },
      data: { isActive },
      select: adminSelect,
    });
    return withRole(admin);
  },

  /**
   * Soft delete an admin
   */
  async delete(id: string, canAccessSuperAdmin = false) {
    await assertCanAccessAdmin(id, canAccessSuperAdmin);

    const prisma = await getPrisma();

    const admin = await prisma.admin.update({
      where: { id },
      data: {
        deletedAt: new Date(),
        isActive: false,
      },
      select: adminSelect,
    });
    return withRole(admin);
  },
};
