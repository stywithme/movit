/**
 * Admin Auth Service
 * ==================
 * 
 * Authentication and profile management for dashboard admins.
 */

import bcrypt from 'bcryptjs';
import crypto from 'crypto';
import { getPrisma } from '@/lib/prisma/client';
import type {
  AdminLoginInput,
  AdminUpdateProfileInput,
  AdminPublic,
} from './admin-auth.types';

const SALT_ROUNDS = 12;
const RESET_TOKEN_EXPIRY_HOURS = 1;

function toAdminPublic(admin: {
  id: string;
  email: string;
  name: string;
  role: string;
  isActive: boolean;
  createdAt: Date;
}): AdminPublic {
  return {
    id: admin.id,
    email: admin.email,
    name: admin.name,
    role: admin.role,
    isActive: admin.isActive,
    createdAt: admin.createdAt,
  };
}

export const adminAuthService = {
  /**
   * Login with email/password
   */
  async login(data: AdminLoginInput) {
    const prisma = await getPrisma();

    const admin = await prisma.admin.findUnique({
      where: { email: data.email.toLowerCase(), deletedAt: null },
    });

    if (!admin) {
      throw new Error('Invalid email or password');
    }

    if (!admin.isActive) {
      throw new Error('Account is deactivated');
    }

    const isValidPassword = await bcrypt.compare(data.password, admin.password);
    if (!isValidPassword) {
      throw new Error('Invalid email or password');
    }

    return toAdminPublic(admin);
  },

  /**
   * Get admin profile
   */
  async getProfile(adminId: string): Promise<AdminPublic | null> {
    const prisma = await getPrisma();

    const admin = await prisma.admin.findUnique({
      where: { id: adminId, deletedAt: null },
    });

    if (!admin) return null;
    return toAdminPublic(admin);
  },

  /**
   * Update admin profile
   */
  async updateProfile(adminId: string, data: AdminUpdateProfileInput): Promise<AdminPublic> {
    const prisma = await getPrisma();

    const admin = await prisma.admin.update({
      where: { id: adminId },
      data: {
        name: data.name,
        email: data.email ? data.email.toLowerCase() : undefined,
      },
    });

    return toAdminPublic(admin);
  },

  /**
   * Request password reset
   */
  async requestReset(email: string): Promise<void> {
    const prisma = await getPrisma();

    const admin = await prisma.admin.findUnique({
      where: { email: email.toLowerCase(), deletedAt: null },
    });

    if (!admin) {
      return;
    }

    const resetToken = crypto.randomBytes(32).toString('hex');
    const resetTokenExpiry = new Date(Date.now() + RESET_TOKEN_EXPIRY_HOURS * 60 * 60 * 1000);

    await prisma.admin.update({
      where: { id: admin.id },
      data: {
        resetToken,
        resetTokenExpiry,
      },
    });

    // TODO: Send email with reset link
    console.log(`Admin password reset token for ${email}: ${resetToken}`);
  },

  /**
   * Reset password with token
   */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    const prisma = await getPrisma();

    const admin = await prisma.admin.findFirst({
      where: {
        resetToken: token,
        resetTokenExpiry: { gt: new Date() },
        deletedAt: null,
      },
    });

    if (!admin) {
      throw new Error('Invalid or expired reset token');
    }

    const hashedPassword = await bcrypt.hash(newPassword, SALT_ROUNDS);

    await prisma.admin.update({
      where: { id: admin.id },
      data: {
        password: hashedPassword,
        resetToken: null,
        resetTokenExpiry: null,
      },
    });
  },
};
