/**
 * Auth Service
 * =============
 * 
 * Service for user authentication and profile management.
 * Handles: Registration, Login, Profile, Settings, Password Reset
 */

import type { Request } from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import crypto from 'crypto';
import { OAuth2Client } from 'google-auth-library';
import { getPrisma } from '@/lib/prisma/client';
import type {
  RegisterInput,
  LoginInput,
  UpdateProfileInput,
  UpdateSettingsInput,
  ChangePasswordInput,
  UserPublic,
  AuthTokens,
  AuthResponse,
  JwtPayload,
} from './auth.types';

// ============================================
// CONSTANTS
// ============================================

const SALT_ROUNDS = 12;
const ACCESS_TOKEN_EXPIRY = '24h'; // 24 hours
const REFRESH_TOKEN_EXPIRY = '30d'; // 30 days
const RESET_TOKEN_EXPIRY_HOURS = 1;

const JWT_SECRET = process.env.JWT_SECRET || 'your-super-secret-jwt-key-change-in-production';
const JWT_REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || 'your-super-secret-refresh-key-change-in-production';

// Google OAuth Client ID (Web Client ID from Google Cloud Console)
const GOOGLE_CLIENT_ID = process.env.GOOGLE_CLIENT_ID || '';
const googleClient = new OAuth2Client(GOOGLE_CLIENT_ID);

// ============================================
// GOOGLE TOKEN VERIFICATION
// ============================================

interface GoogleTokenPayload {
  sub: string; // Google user ID
  email: string;
  name: string;
  picture?: string;
  email_verified: boolean;
}

/**
 * Verify Google ID token and extract user data
 */
async function verifyGoogleToken(idToken: string): Promise<GoogleTokenPayload | null> {
  // Skip verification if no client ID configured (development mode)
  if (!GOOGLE_CLIENT_ID) {
    console.warn('GOOGLE_CLIENT_ID not configured - skipping token verification');
    return null;
  }

  try {
    const ticket = await googleClient.verifyIdToken({
      idToken,
      audience: GOOGLE_CLIENT_ID,
    });
    
    const payload = ticket.getPayload();
    if (!payload) return null;

    return {
      sub: payload.sub,
      email: payload.email || '',
      name: payload.name || 'User',
      picture: payload.picture,
      email_verified: payload.email_verified || false,
    };
  } catch (error) {
    console.error('Google token verification failed:', error);
    return null;
  }
}

// ============================================
// HELPERS
// ============================================

function toUserPublic(user: {
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
}): UserPublic {
  return {
    id: user.id,
    email: user.email,
    name: user.name,
    avatarUrl: user.avatarUrl,
    provider: user.provider,
    preferredLanguage: user.preferredLanguage,
    voiceFeedback: user.voiceFeedback,
    notifications: user.notifications,
    isPro: user.isPro,
    subscriptionExpiry: user.subscriptionExpiry,
    totalWorkouts: user.totalWorkouts,
    totalMinutes: user.totalMinutes,
    emailVerified: user.emailVerified,
    createdAt: user.createdAt,
  };
}

function generateTokens(userId: string, email: string): AuthTokens {
  const accessToken = jwt.sign(
    { userId, email, type: 'access' } as JwtPayload,
    JWT_SECRET,
    { expiresIn: ACCESS_TOKEN_EXPIRY }
  );

  const refreshToken = jwt.sign(
    { userId, email, type: 'refresh' } as JwtPayload,
    JWT_REFRESH_SECRET,
    { expiresIn: REFRESH_TOKEN_EXPIRY }
  );

  return {
    accessToken,
    refreshToken,
    expiresIn: 24 * 60 * 60, // 24 hours in seconds
  };
}

function verifyAccessToken(token: string): JwtPayload | null {
  try {
    const payload = jwt.verify(token, JWT_SECRET) as JwtPayload;
    if (payload.type !== 'access') return null;
    return payload;
  } catch {
    return null;
  }
}

function verifyRefreshToken(token: string): JwtPayload | null {
  try {
    const payload = jwt.verify(token, JWT_REFRESH_SECRET) as JwtPayload;
    if (payload.type !== 'refresh') return null;
    return payload;
  } catch {
    return null;
  }
}

// ============================================
// SERVICE
// ============================================

export const authService = {
  /**
   * Register a new user with email/password
   */
  async register(data: RegisterInput, deviceInfo?: string): Promise<AuthResponse> {
    const prisma = await getPrisma();

    // Check if email already exists
    const existingUser = await prisma.user.findUnique({
      where: { email: data.email.toLowerCase() },
    });

    if (existingUser) {
      throw new Error('Email already registered');
    }

    // Hash password
    const hashedPassword = await bcrypt.hash(data.password, SALT_ROUNDS);

    // Create user
    const user = await prisma.user.create({
      data: {
        email: data.email.toLowerCase(),
        password: hashedPassword,
        name: data.name,
        provider: 'email',
      },
    });

    // Generate tokens
    const tokens = generateTokens(user.id, user.email);

    // Store refresh token
    await prisma.refreshToken.create({
      data: {
        userId: user.id,
        token: tokens.refreshToken,
        deviceInfo,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), // 30 days
      },
    });

    return {
      user: toUserPublic(user),
      tokens,
    };
  },

  /**
   * Login with email/password
   */
  async login(data: LoginInput, deviceInfo?: string): Promise<AuthResponse> {
    const prisma = await getPrisma();

    // Find user by email
    const user = await prisma.user.findUnique({
      where: { email: data.email.toLowerCase(), deletedAt: null },
    });

    if (!user || !user.password) {
      throw new Error('Invalid email or password');
    }

    if (!user.isActive) {
      throw new Error('Account is deactivated');
    }

    // Verify password
    const isValidPassword = await bcrypt.compare(data.password, user.password);
    if (!isValidPassword) {
      throw new Error('Invalid email or password');
    }

    // Generate tokens
    const tokens = generateTokens(user.id, user.email);

    // Store refresh token
    await prisma.refreshToken.create({
      data: {
        userId: user.id,
        token: tokens.refreshToken,
        deviceInfo,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), // 30 days
      },
    });

    return {
      user: toUserPublic(user),
      tokens,
    };
  },

  /**
   * Google OAuth login/register
   * 
   * @param idToken - Google ID token from client (optional if googleId/email/name provided)
   * @param googleId - Google user ID (sub claim)
   * @param email - User email
   * @param name - User display name
   * @param avatarUrl - Profile picture URL
   * @param deviceInfo - Device info for session tracking
   */
  async googleAuth(
    idToken: string | undefined,
    googleId: string,
    email: string,
    name: string,
    avatarUrl?: string,
    deviceInfo?: string
  ): Promise<AuthResponse> {
    const prisma = await getPrisma();

    // If idToken provided and GOOGLE_CLIENT_ID configured, verify the token
    if (idToken && GOOGLE_CLIENT_ID) {
      const verified = await verifyGoogleToken(idToken);
      if (verified) {
        // Use verified data instead of client-provided data
        googleId = verified.sub;
        email = verified.email;
        name = verified.name;
        avatarUrl = verified.picture || avatarUrl;
      } else {
        throw new Error('Invalid Google token');
      }
    }

    // Find or create user
    let user = await prisma.user.findFirst({
      where: {
        OR: [
          { googleId },
          { email: email.toLowerCase() },
        ],
        deletedAt: null,
      },
    });

    if (user) {
      // Update Google ID if not set
      if (!user.googleId) {
        user = await prisma.user.update({
          where: { id: user.id },
          data: { 
            googleId, 
            emailVerified: true,
            avatarUrl: avatarUrl || user.avatarUrl,
          },
        });
      }
    } else {
      // Create new user
      user = await prisma.user.create({
        data: {
          email: email.toLowerCase(),
          name,
          googleId,
          avatarUrl,
          provider: 'google',
          emailVerified: true,
        },
      });
    }

    if (!user.isActive) {
      throw new Error('Account is deactivated');
    }

    // Generate tokens
    const tokens = generateTokens(user.id, user.email);

    // Store refresh token
    await prisma.refreshToken.create({
      data: {
        userId: user.id,
        token: tokens.refreshToken,
        deviceInfo,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });

    return {
      user: toUserPublic(user),
      tokens,
    };
  },

  /**
   * Refresh access token
   */
  async refreshAccessToken(refreshToken: string): Promise<AuthTokens> {
    const prisma = await getPrisma();

    // Verify refresh token
    const payload = verifyRefreshToken(refreshToken);
    if (!payload) {
      throw new Error('Invalid refresh token');
    }

    // Check if token exists in database
    const storedToken = await prisma.refreshToken.findUnique({
      where: { token: refreshToken },
      include: { user: true },
    });

    if (!storedToken || storedToken.expiresAt < new Date()) {
      throw new Error('Refresh token expired or invalid');
    }

    if (!storedToken.user.isActive) {
      throw new Error('Account is deactivated');
    }

    // Generate new tokens
    const tokens = generateTokens(storedToken.userId, storedToken.user.email);

    // Delete old refresh token and create new one
    await prisma.refreshToken.delete({ where: { id: storedToken.id } });
    await prisma.refreshToken.create({
      data: {
        userId: storedToken.userId,
        token: tokens.refreshToken,
        deviceInfo: storedToken.deviceInfo,
        expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      },
    });

    return tokens;
  },

  /**
   * Logout - Invalidate refresh token
   */
  async logout(refreshToken: string): Promise<void> {
    const prisma = await getPrisma();
    
    await prisma.refreshToken.deleteMany({
      where: { token: refreshToken },
    });
  },

  /**
   * Logout from all devices
   */
  async logoutAll(userId: string): Promise<void> {
    const prisma = await getPrisma();
    
    await prisma.refreshToken.deleteMany({
      where: { userId },
    });
  },

  /**
   * Get user profile by ID
   */
  async getProfile(userId: string): Promise<UserPublic | null> {
    const prisma = await getPrisma();
    
    const user = await prisma.user.findUnique({
      where: { id: userId, deletedAt: null },
    });

    if (!user) return null;
    return toUserPublic(user);
  },

  /**
   * Update user profile
   */
  async updateProfile(userId: string, data: UpdateProfileInput): Promise<UserPublic> {
    const prisma = await getPrisma();
    
    const user = await prisma.user.update({
      where: { id: userId },
      data: {
        name: data.name,
        avatarUrl: data.avatarUrl,
      },
    });

    return toUserPublic(user);
  },

  /**
   * Update user settings
   */
  async updateSettings(userId: string, data: UpdateSettingsInput): Promise<UserPublic> {
    const prisma = await getPrisma();
    
    const user = await prisma.user.update({
      where: { id: userId },
      data: {
        preferredLanguage: data.preferredLanguage,
        voiceFeedback: data.voiceFeedback,
        notifications: data.notifications,
      },
    });

    return toUserPublic(user);
  },

  /**
   * Change password
   */
  async changePassword(userId: string, data: ChangePasswordInput): Promise<void> {
    const prisma = await getPrisma();
    
    const user = await prisma.user.findUnique({
      where: { id: userId },
    });

    if (!user || !user.password) {
      throw new Error('Cannot change password for OAuth accounts');
    }

    // Verify current password
    const isValidPassword = await bcrypt.compare(data.currentPassword, user.password);
    if (!isValidPassword) {
      throw new Error('Current password is incorrect');
    }

    // Hash new password
    const hashedPassword = await bcrypt.hash(data.newPassword, SALT_ROUNDS);

    await prisma.user.update({
      where: { id: userId },
      data: { password: hashedPassword },
    });

    // Logout from all devices for security
    await this.logoutAll(userId);
  },

  /**
   * Request password reset
   */
  async forgotPassword(email: string): Promise<void> {
    const prisma = await getPrisma();
    
    const user = await prisma.user.findUnique({
      where: { email: email.toLowerCase(), deletedAt: null },
    });

    if (!user) {
      // Don't reveal if email exists
      return;
    }

    if (user.provider !== 'email') {
      // Don't reveal OAuth accounts
      return;
    }

    // Generate reset token
    const resetToken = crypto.randomBytes(32).toString('hex');
    const resetTokenExpiry = new Date(Date.now() + RESET_TOKEN_EXPIRY_HOURS * 60 * 60 * 1000);

    await prisma.user.update({
      where: { id: user.id },
      data: {
        resetToken,
        resetTokenExpiry,
      },
    });

    // TODO: Send email with reset link
    // In production, integrate with email service
    console.log(`Password reset token for ${email}: ${resetToken}`);
  },

  /**
   * Reset password with token
   */
  async resetPassword(token: string, newPassword: string): Promise<void> {
    const prisma = await getPrisma();
    
    const user = await prisma.user.findFirst({
      where: {
        resetToken: token,
        resetTokenExpiry: { gt: new Date() },
        deletedAt: null,
      },
    });

    if (!user) {
      throw new Error('Invalid or expired reset token');
    }

    // Hash new password
    const hashedPassword = await bcrypt.hash(newPassword, SALT_ROUNDS);

    await prisma.user.update({
      where: { id: user.id },
      data: {
        password: hashedPassword,
        resetToken: null,
        resetTokenExpiry: null,
      },
    });

    // Logout from all devices
    await this.logoutAll(user.id);
  },

  /**
   * Verify access token and get user ID
   */
  verifyToken(token: string): string | null {
    const payload = verifyAccessToken(token);
    return payload?.userId || null;
  },

  /**
   * Update user stats
   */
  async updateStats(userId: string, workouts: number, minutes: number): Promise<void> {
    const prisma = await getPrisma();
    
    await prisma.user.update({
      where: { id: userId },
      data: {
        totalWorkouts: { increment: workouts },
        totalMinutes: { increment: minutes },
      },
    });
  },

  /**
   * Delete account (soft delete)
   */
  async deleteAccount(userId: string): Promise<void> {
    const prisma = await getPrisma();
    
    // Delete all refresh tokens
    await prisma.refreshToken.deleteMany({ where: { userId } });

    // Soft delete user
    await prisma.user.update({
      where: { id: userId },
      data: {
        deletedAt: new Date(),
        isActive: false,
      },
    });
  },
};

// ============================================
// HELPER FUNCTIONS FOR API ROUTES
// ============================================

export interface MobileTokenResult {
  success: boolean;
  userId?: string;
  error?: string;
}

/**
 * Verify mobile token from Authorization header
 * 
 * Usage in API routes:
 * ```
 * const result = await verifyMobileToken(request);
 * if (!result.success) {
 *   return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
 * }
 * const userId = result.userId;
 * ```
 */
export async function verifyMobileToken(request: Request): Promise<MobileTokenResult> {
  try {
    const authHeader = request.headers.authorization;
    
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return { success: false, error: 'Missing or invalid Authorization header' };
    }
    
    const token = authHeader.replace('Bearer ', '');
    const userId = authService.verifyToken(token);
    
    if (!userId) {
      return { success: false, error: 'Invalid or expired token' };
    }
    
    return { success: true, userId };
  } catch (error) {
    return { 
      success: false, 
      error: error instanceof Error ? error.message : 'Token verification failed' 
    };
  }
}
