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
  roleId?: string | null;
  isDoctor?: boolean;
}

export interface UpdateAdminInput {
  name?: string;
  email?: string;
  roleId?: string | null;
  isActive?: boolean;
  isDoctor?: boolean;
}
