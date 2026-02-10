/**
 * Admins Types
 * ============
 * 
 * Type definitions for admin management.
 */

export interface CreateAdminInput {
  name: string;
  email: string;
  password: string;
  role?: string;
}

export interface UpdateAdminInput {
  name?: string;
  email?: string;
  role?: string;
  isActive?: boolean;
}
