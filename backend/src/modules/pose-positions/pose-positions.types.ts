import { LocalizedText } from '@/lib/types/localized';

export interface CreatePosePositionInput {
  code: string;
  name: LocalizedText;
  description?: LocalizedText;
  imageUrl?: string;
  postures?: string[];
  directions?: string[];
  regions?: string[];
  jointIds: string[];
}

export interface UpdatePosePositionInput {
  name?: LocalizedText;
  description?: LocalizedText;
  imageUrl?: string;
  isActive?: boolean;
  sortOrder?: number;
  postures?: string[];
  directions?: string[];
  regions?: string[];
  jointIds?: string[];
}

export interface PosePositionWithJoints {
  id: string;
  code: string;
  name: LocalizedText;
  description?: LocalizedText | null;
  imageUrl?: string | null;
  isActive: boolean;
  sortOrder: number;
  postures: string[];
  directions: string[];
  regions: string[];
  createdAt: Date;
  updatedAt: Date;
  joints: {
    id: string;
    code: string;
    name: LocalizedText;
  }[];
}
