/**
 * Joint Templates & Smart Recommendations
 * ========================================
 * 
 * Auto-fills joint configurations based on exercise type.
 */

import type { CountingMethodCode } from '@/lib/types/localized';

// ============================================
// RECOMMENDATIONS BY EXERCISE TYPE
// ============================================

interface JointRecommendation {
  primary: string[];
  secondary: string[];
}

/**
 * Smart recommendations based on counting method and category
 */
export const JOINT_RECOMMENDATIONS: Record<CountingMethodCode, Record<string, JointRecommendation>> = {
  up_down: {
    legs: {
      primary: ['left_knee', 'right_knee'],
      secondary: ['spine', 'left_hip', 'right_hip'],
    },
    arms: {
      primary: ['left_elbow', 'right_elbow'],
      secondary: ['left_shoulder', 'right_shoulder'],
    },
    back: {
      primary: ['left_hip', 'right_hip'],
      secondary: ['spine', 'left_knee', 'right_knee'],
    },
    default: {
      primary: ['left_knee', 'right_knee'],
      secondary: ['spine'],
    },
  },
  push_pull: {
    chest: {
      primary: ['left_elbow', 'right_elbow'],
      secondary: ['left_shoulder', 'right_shoulder', 'spine'],
    },
    back: {
      primary: ['left_elbow', 'right_elbow'],
      secondary: ['left_shoulder', 'right_shoulder'],
    },
    shoulders: {
      primary: ['left_shoulder', 'right_shoulder'],
      secondary: ['left_elbow', 'right_elbow'],
    },
    default: {
      primary: ['left_elbow', 'right_elbow'],
      secondary: ['spine'],
    },
  },
  hold: {
    abs: {
      primary: ['left_hip', 'right_hip'],
      secondary: ['spine', 'left_shoulder', 'right_shoulder'],
    },
    full_body: {
      primary: ['left_elbow', 'right_elbow'],
      secondary: ['spine', 'left_hip', 'right_hip'],
    },
    default: {
      primary: ['spine'],
      secondary: ['left_hip', 'right_hip'],
    },
  },
};

/**
 * Get recommendations for a specific exercise type
 */
export function getJointRecommendations(
  countingMethod: CountingMethodCode,
  category?: string
): JointRecommendation {
  const methodRecs = JOINT_RECOMMENDATIONS[countingMethod] || JOINT_RECOMMENDATIONS.up_down;
  return methodRecs[category || 'default'] || methodRecs.default;
}

// ============================================
// DEFAULT ANGLE RANGES
// ============================================

interface AngleRange {
  min: number;
  max: number;
}

interface DifficultyRanges {
  beginner: AngleRange;
  normal: AngleRange;
  advanced: AngleRange;
}

interface JointDefaults {
  startPose: AngleRange;
  upRange: DifficultyRanges;
  downRange: DifficultyRanges;
  Range: DifficultyRanges; // For secondary joints
}

/**
 * Default ranges for different joint types
 */
export const JOINT_DEFAULTS: Record<string, JointDefaults> = {
  // Knees - Squat pattern
  left_knee: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 150, max: 180 },
      normal: { min: 155, max: 180 },
      advanced: { min: 160, max: 180 },
    },
    downRange: {
      beginner: { min: 80, max: 120 },
      normal: { min: 70, max: 110 },
      advanced: { min: 60, max: 100 },
    },
    Range: {
      beginner: { min: 60, max: 180 },
      normal: { min: 60, max: 180 },
      advanced: { min: 60, max: 180 },
    },
  },
  right_knee: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 150, max: 180 },
      normal: { min: 155, max: 180 },
      advanced: { min: 160, max: 180 },
    },
    downRange: {
      beginner: { min: 80, max: 120 },
      normal: { min: 70, max: 110 },
      advanced: { min: 60, max: 100 },
    },
    Range: {
      beginner: { min: 60, max: 180 },
      normal: { min: 60, max: 180 },
      advanced: { min: 60, max: 180 },
    },
  },
  
  // Elbows - Push-up pattern
  left_elbow: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 150, max: 180 },
      normal: { min: 160, max: 180 },
      advanced: { min: 165, max: 180 },
    },
    downRange: {
      beginner: { min: 70, max: 110 },
      normal: { min: 60, max: 100 },
      advanced: { min: 50, max: 90 },
    },
    Range: {
      beginner: { min: 50, max: 180 },
      normal: { min: 50, max: 180 },
      advanced: { min: 50, max: 180 },
    },
  },
  right_elbow: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 150, max: 180 },
      normal: { min: 160, max: 180 },
      advanced: { min: 165, max: 180 },
    },
    downRange: {
      beginner: { min: 70, max: 110 },
      normal: { min: 60, max: 100 },
      advanced: { min: 50, max: 90 },
    },
    Range: {
      beginner: { min: 50, max: 180 },
      normal: { min: 50, max: 180 },
      advanced: { min: 50, max: 180 },
    },
  },
  
  // Spine - Posture
  spine: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 155, max: 180 },
      normal: { min: 160, max: 180 },
      advanced: { min: 165, max: 180 },
    },
    downRange: {
      beginner: { min: 155, max: 180 },
      normal: { min: 160, max: 180 },
      advanced: { min: 165, max: 180 },
    },
    Range: {
      beginner: { min: 150, max: 180 },
      normal: { min: 155, max: 180 },
      advanced: { min: 160, max: 180 },
    },
  },
  
  // Hips
  left_hip: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 150, max: 180 },
      normal: { min: 155, max: 180 },
      advanced: { min: 160, max: 180 },
    },
    downRange: {
      beginner: { min: 70, max: 110 },
      normal: { min: 60, max: 100 },
      advanced: { min: 50, max: 90 },
    },
    Range: {
      beginner: { min: 50, max: 180 },
      normal: { min: 50, max: 180 },
      advanced: { min: 50, max: 180 },
    },
  },
  right_hip: {
    startPose: { min: 160, max: 180 },
    upRange: {
      beginner: { min: 150, max: 180 },
      normal: { min: 155, max: 180 },
      advanced: { min: 160, max: 180 },
    },
    downRange: {
      beginner: { min: 70, max: 110 },
      normal: { min: 60, max: 100 },
      advanced: { min: 50, max: 90 },
    },
    Range: {
      beginner: { min: 50, max: 180 },
      normal: { min: 50, max: 180 },
      advanced: { min: 50, max: 180 },
    },
  },
  
  // Shoulders
  left_shoulder: {
    startPose: { min: 0, max: 30 },
    upRange: {
      beginner: { min: 0, max: 30 },
      normal: { min: 0, max: 25 },
      advanced: { min: 0, max: 20 },
    },
    downRange: {
      beginner: { min: 70, max: 100 },
      normal: { min: 75, max: 105 },
      advanced: { min: 80, max: 110 },
    },
    Range: {
      beginner: { min: 0, max: 110 },
      normal: { min: 0, max: 110 },
      advanced: { min: 0, max: 110 },
    },
  },
  right_shoulder: {
    startPose: { min: 0, max: 30 },
    upRange: {
      beginner: { min: 0, max: 30 },
      normal: { min: 0, max: 25 },
      advanced: { min: 0, max: 20 },
    },
    downRange: {
      beginner: { min: 70, max: 100 },
      normal: { min: 75, max: 105 },
      advanced: { min: 80, max: 110 },
    },
    Range: {
      beginner: { min: 0, max: 110 },
      normal: { min: 0, max: 110 },
      advanced: { min: 0, max: 110 },
    },
  },
  
  // Ankles
  left_ankle: {
    startPose: { min: 80, max: 100 },
    upRange: {
      beginner: { min: 80, max: 100 },
      normal: { min: 85, max: 95 },
      advanced: { min: 88, max: 92 },
    },
    downRange: {
      beginner: { min: 60, max: 80 },
      normal: { min: 55, max: 75 },
      advanced: { min: 50, max: 70 },
    },
    Range: {
      beginner: { min: 50, max: 100 },
      normal: { min: 50, max: 100 },
      advanced: { min: 50, max: 100 },
    },
  },
  right_ankle: {
    startPose: { min: 80, max: 100 },
    upRange: {
      beginner: { min: 80, max: 100 },
      normal: { min: 85, max: 95 },
      advanced: { min: 88, max: 92 },
    },
    downRange: {
      beginner: { min: 60, max: 80 },
      normal: { min: 55, max: 75 },
      advanced: { min: 50, max: 70 },
    },
    Range: {
      beginner: { min: 50, max: 100 },
      normal: { min: 50, max: 100 },
      advanced: { min: 50, max: 100 },
    },
  },
};

/**
 * Get default ranges for a joint
 */
export function getJointDefaults(jointCode: string): JointDefaults {
  return JOINT_DEFAULTS[jointCode] || JOINT_DEFAULTS.spine;
}

/**
 * Build a complete TrackedJoint object with defaults
 */
export function buildTrackedJoint(
  jointCode: string,
  role: 'primary' | 'secondary',
  pairedWith?: string
) {
  const defaults = getJointDefaults(jointCode);
  const jointName = jointCode.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase());
  
  const base = {
    joint: jointCode,
    role,
    startPose: defaults.startPose,
    errorMessages: {
      tooLow: { ar: 'انزل أكثر', en: 'Go lower' },
      tooHigh: { ar: 'لا تنزل كثيراً', en: 'Don\'t go too low' },
    },
    ...(pairedWith && { pairedWith }),
  };
  
  if (role === 'primary') {
    return {
      ...base,
      upRange: defaults.upRange,
      downRange: defaults.downRange,
    };
  } else {
    return {
      ...base,
      Range: defaults.Range,
    };
  }
}
