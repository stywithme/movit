/**
 * Auth Types
 * ===========
 * 
 * Type definitions for authentication module.
 */

import { z } from 'zod';

// ============================================
// VALIDATION SCHEMAS
// ============================================

export const registerSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters'),
  email: z.string().email('Invalid email address'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

export const loginSchema = z.object({
  email: z.string().email('Invalid email address'),
  password: z.string().min(1, 'Password is required'),
});

export const googleAuthSchema = z.object({
  idToken: z.string().min(1, 'Google ID token is required'),
});

export const forgotPasswordSchema = z.object({
  email: z.string().email('Invalid email address'),
});

export const resetPasswordSchema = z.object({
  token: z.string().min(1, 'Reset token is required'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

export const updateProfileSchema = z.object({
  name: z.string().min(2, 'Name must be at least 2 characters').optional(),
  avatarUrl: z.string().url('Invalid avatar URL').optional().nullable(),
});

export const updateSettingsSchema = z.object({
  preferredLanguage: z.enum(['en', 'ar']).optional(),
  voiceFeedback: z.boolean().optional(),
  notifications: z.boolean().optional(),
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, 'Current password is required'),
  newPassword: z.string().min(6, 'New password must be at least 6 characters'),
});

export const refreshTokenSchema = z.object({
  refreshToken: z.string().min(1, 'Refresh token is required'),
});

// ============================================
// TYPE DEFINITIONS
// ============================================

export type RegisterInput = z.infer<typeof registerSchema>;
export type LoginInput = z.infer<typeof loginSchema>;
export type GoogleAuthInput = z.infer<typeof googleAuthSchema>;
export type ForgotPasswordInput = z.infer<typeof forgotPasswordSchema>;
export type ResetPasswordInput = z.infer<typeof resetPasswordSchema>;
export type UpdateProfileInput = z.infer<typeof updateProfileSchema>;
export type UpdateSettingsInput = z.infer<typeof updateSettingsSchema>;
export type ChangePasswordInput = z.infer<typeof changePasswordSchema>;
export type RefreshTokenInput = z.infer<typeof refreshTokenSchema>;

// User without sensitive data
export interface UserPublic {
  id: string;
  email: string;
  name: string;
  avatarUrl: string | null;
  provider: string;
  preferredLanguage: string;
  voiceFeedback: boolean;
  notifications: boolean;
  isPro: boolean;
  subscriptionExpiry: Date | null;
  totalWorkouts: number;
  totalMinutes: number;
  emailVerified: boolean;
  createdAt: Date;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  expiresIn: number; // seconds
}

export interface AuthResponse {
  user: UserPublic;
  tokens: AuthTokens;
}

export interface JwtPayload {
  sub: string;
  userId: string;
  email: string;
  type: string; // 'regular' or 'premium' (or 'access'/'refresh' for old tokens)
  tokenType?: 'access' | 'refresh'; // New tokens use this
  isActive?: boolean;
  subscriptionExpiry?: string | null;
  iat?: number;
  exp?: number;
}
