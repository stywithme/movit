/**
 * Admin Auth Types
 * =================
 * 
 * Type definitions for admin authentication.
 */

import { z } from 'zod';

export const adminLoginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});

export const adminUpdateProfileSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters').optional(),
  email: z.string().email('Invalid email address').optional(),
});

export const adminRequestResetSchema = z.object({
  email: z.string().email('Invalid email address'),
});

export const adminResetPasswordSchema = z.object({
  token: z.string().min(1, 'Reset token is required'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

export type AdminLoginInput = z.infer<typeof adminLoginSchema>;
export type AdminUpdateProfileInput = z.infer<typeof adminUpdateProfileSchema>;
export type AdminRequestResetInput = z.infer<typeof adminRequestResetSchema>;
export type AdminResetPasswordInput = z.infer<typeof adminResetPasswordSchema>;

export interface AdminPublic {
  id: string;
  email: string;
  name: string;
  roleId: string | null;
  isSuperAdmin: boolean;
  isActive: boolean;
  createdAt: Date;
  permissions: { action: string; subject: string }[];
}
