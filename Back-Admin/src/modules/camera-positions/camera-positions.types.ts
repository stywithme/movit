import { LocalizedText } from '@/lib/types/localized';

/**
 * Camera Position creation input
 */
export interface CreateCameraPositionInput {
  code: string;
  name: LocalizedText;
  description?: LocalizedText;
  imageUrl?: string;
  jointIds: string[]; // Required joint IDs
}

/**
 * Camera Position update input
 */
export interface UpdateCameraPositionInput {
  name?: LocalizedText;
  description?: LocalizedText;
  imageUrl?: string;
  isActive?: boolean;
  sortOrder?: number;
  jointIds?: string[]; // Required joint IDs
}

/**
 * Camera Position with relations
 */
export interface CameraPositionWithJoints {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  imageUrl?: string | null;
  isActive: boolean;
  sortOrder: number;
  createdAt: Date;
  updatedAt: Date;
  joints: {
    id: string;
    code: string;
    name: LocalizedText;
  }[];
}


