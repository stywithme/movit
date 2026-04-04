import { LocalizedText } from '@/lib/types/localized';

export interface UpdatePosePositionInput {
  name?: LocalizedText;
  imageUrl?: string;
  isActive?: boolean;
}

export interface PosePositionWithJoints {
  id: string;
  code: string;
  name: LocalizedText;
  imageUrl?: string | null;
  isActive: boolean;
  sortOrder: number;
  postures: string[];
  directions: string[];
  regions: string[];
  createdAt: Date;
  updatedAt: Date;
}
