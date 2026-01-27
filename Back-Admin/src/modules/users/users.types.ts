/**
 * Users Types
 * ===========
 * 
 * Type definitions for admin users management.
 */

export interface CreateUserInput {
  name: string;
  email: string;
  password: string;
  avatarUrl?: string | null;
  isPro?: boolean;
  subscriptionExpiry?: Date | null;
}

export interface UpdateUserInput {
  name?: string;
  email?: string;
  avatarUrl?: string | null;
  isPro?: boolean;
  subscriptionExpiry?: Date | null;
  isActive?: boolean;
}
