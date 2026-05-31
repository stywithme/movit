/**
 * Joint Templates & Smart Recommendations
 * ========================================
 * 
 * Auto-fills joint configurations based on exercise type.
 * Updated: State-based system (no difficulty levels)
 */

import type { CountingMethodCode, JointStateName } from '@/lib/types/localized';
import type { StateRangesData, TrackedJointData, PrimaryTrackedJointData, SecondaryTrackedJointData } from '@/modules/exercises/exercises.validation';

// ============================================
// STATE RANGE DEFAULTS
// ============================================

interface StateRangesDefaults {
  upRange: StateRangesData;
  downRange: StateRangesData;
  range: StateRangesData; // For secondary/hold joints
}

/**
 * Default state ranges for different joint types
 */
export const JOINT_DEFAULTS: Record<string, StateRangesDefaults> = {
  // Knees - Squat pattern
  left_knee: {
    upRange: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      pad: { min: 140, max: 180 },
    },
    downRange: {
      perfect: { min: 70, max: 100 },
      normal: { min: 60, max: 110 },
      pad: { min: 50, max: 120 },
      warning: { min: 40, max: 50 },
      danger: { min: 0, max: 40 },
    },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      warning: { min: 0, max: 150 },
    },
  },
  right_knee: {
    upRange: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      pad: { min: 140, max: 180 },
    },
    downRange: {
      perfect: { min: 70, max: 100 },
      normal: { min: 60, max: 110 },
      pad: { min: 50, max: 120 },
      warning: { min: 40, max: 50 },
      danger: { min: 0, max: 40 },
    },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      warning: { min: 0, max: 150 },
    },
  },

  // Elbows - Push-up / Curl pattern
  left_elbow: {
    upRange: {
      perfect: { min: 165, max: 180 },
      normal: { min: 155, max: 180 },
      pad: { min: 145, max: 180 },
    },
    downRange: {
      perfect: { min: 60, max: 90 },
      normal: { min: 50, max: 100 },
      pad: { min: 40, max: 110 },
    },
    range: {
      perfect: { min: 50, max: 180 },
      normal: { min: 40, max: 180 },
      warning: { min: 0, max: 40 },
    },
  },
  right_elbow: {
    upRange: {
      perfect: { min: 165, max: 180 },
      normal: { min: 155, max: 180 },
      pad: { min: 145, max: 180 },
    },
    downRange: {
      perfect: { min: 60, max: 90 },
      normal: { min: 50, max: 100 },
      pad: { min: 40, max: 110 },
    },
    range: {
      perfect: { min: 50, max: 180 },
      normal: { min: 40, max: 180 },
      warning: { min: 0, max: 40 },
    },
  },

  // Spine - Posture
  spine: {
    upRange: {
      perfect: { min: 165, max: 180 },
      normal: { min: 160, max: 180 },
      pad: { min: 155, max: 180 },
      warning: { min: 140, max: 155 },
      danger: { min: 0, max: 140 },
    },
    downRange: {
      perfect: { min: 165, max: 180 },
      normal: { min: 160, max: 180 },
      pad: { min: 155, max: 180 },
    },
    range: {
      perfect: { min: 0, max: 15 },
      normal: { min: 0, max: 20 },
      pad: { min: 0, max: 25 },
      warning: { min: 25, max: 35 },
      danger: { min: 35, max: 90 },
    },
  },

  // Neck Left - Head posture via Left Shoulder (Left Shoulder -> Neck -> Nose ≈ 90°)
  // Best for Right Side View camera
  neck_left: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
      warning: { min: 45, max: 60 },
      danger: { min: 0, max: 45 },
    },
    downRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      warning: { min: 50, max: 70 },
    },
  },

  // Neck Right - Head posture via Right Shoulder (Right Shoulder -> Neck -> Nose ≈ 90°)
  // Best for Left Side View camera
  neck_right: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
      warning: { min: 45, max: 60 },
      danger: { min: 0, max: 45 },
    },
    downRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      warning: { min: 50, max: 70 },
    },
  },

  // Neck Spine - Head posture via Spine (Spine -> Neck -> Nose)
  // Best for Front View camera
  neck_spine: {
    upRange: {
      perfect: { min: 155, max: 180 },
      normal: { min: 145, max: 180 },
      pad: { min: 135, max: 180 },
      warning: { min: 120, max: 135 },
      danger: { min: 0, max: 120 },
    },
    downRange: {
      perfect: { min: 155, max: 180 },
      normal: { min: 145, max: 180 },
      pad: { min: 135, max: 180 },
    },
    range: {
      perfect: { min: 155, max: 180 },
      normal: { min: 145, max: 180 },
      warning: { min: 120, max: 145 },
    },
  },

  // Hips
  left_hip: {
    upRange: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      pad: { min: 140, max: 180 },
    },
    downRange: {
      perfect: { min: 70, max: 100 },
      normal: { min: 60, max: 110 },
      pad: { min: 50, max: 120 },
    },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 155, max: 180 },
      warning: { min: 140, max: 155 },
    },
  },
  right_hip: {
    upRange: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      pad: { min: 140, max: 180 },
    },
    downRange: {
      perfect: { min: 70, max: 100 },
      normal: { min: 60, max: 110 },
      pad: { min: 50, max: 120 },
    },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 155, max: 180 },
      warning: { min: 140, max: 155 },
    },
  },

  // Shoulders
  left_shoulder: {
    upRange: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      warning: { min: 30, max: 50 },
    },
    downRange: {
      perfect: { min: 80, max: 110 },
      normal: { min: 70, max: 120 },
      pad: { min: 60, max: 130 },
    },
    range: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      warning: { min: 30, max: 60 },
    },
  },
  right_shoulder: {
    upRange: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      warning: { min: 30, max: 50 },
    },
    downRange: {
      perfect: { min: 80, max: 110 },
      normal: { min: 70, max: 120 },
      pad: { min: 60, max: 130 },
    },
    range: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      warning: { min: 30, max: 60 },
    },
  },

  // Cross Shoulders - arm angle relative to shoulder line (Elbow -> Shoulder -> Opposite Shoulder)
  // Arm at side ≈ 90°, arm forward ≈ small angle, arm raised ≈ 180°
  left_shoulder_cross: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
    },
    downRange: {
      perfect: { min: 20, max: 50 },
      normal: { min: 10, max: 60 },
      pad: { min: 0, max: 70 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      warning: { min: 50, max: 70 },
    },
  },
  right_shoulder_cross: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
    },
    downRange: {
      perfect: { min: 20, max: 50 },
      normal: { min: 10, max: 60 },
      pad: { min: 0, max: 70 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      warning: { min: 50, max: 70 },
    },
  },

  // Cross Hips - leg angle relative to hip line (Knee -> Hip -> Opposite Hip)
  left_hip_cross: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
    },
    downRange: {
      perfect: { min: 20, max: 50 },
      normal: { min: 10, max: 60 },
      pad: { min: 0, max: 70 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      warning: { min: 50, max: 70 },
    },
  },
  right_hip_cross: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      pad: { min: 60, max: 120 },
    },
    downRange: {
      perfect: { min: 20, max: 50 },
      normal: { min: 10, max: 60 },
      pad: { min: 0, max: 70 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 70, max: 110 },
      warning: { min: 50, max: 70 },
    },
  },

  // Ankles
  left_ankle: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 75, max: 105 },
      pad: { min: 70, max: 110 },
    },
    downRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 75, max: 105 },
      pad: { min: 70, max: 110 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 75, max: 105 },
      pad: { min: 70, max: 110 },
    },
  },
  right_ankle: {
    upRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 75, max: 105 },
      pad: { min: 70, max: 110 },
    },
    downRange: {
      perfect: { min: 80, max: 100 },
      normal: { min: 75, max: 105 },
      pad: { min: 70, max: 110 },
    },
    range: {
      perfect: { min: 80, max: 100 },
      normal: { min: 75, max: 105 },
      pad: { min: 70, max: 110 },
    },
  },

  // Wrists
  left_wrist: {
    upRange: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      pad: { min: 140, max: 180 },
    },
    downRange: {
      perfect: { min: 90, max: 120 },
      normal: { min: 80, max: 130 },
      pad: { min: 70, max: 140 },
    },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      warning: { min: 0, max: 150 },
    },
  },
  right_wrist: {
    upRange: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      pad: { min: 140, max: 180 },
    },
    downRange: {
      perfect: { min: 90, max: 120 },
      normal: { min: 80, max: 130 },
      pad: { min: 70, max: 140 },
    },
    range: {
      perfect: { min: 160, max: 180 },
      normal: { min: 150, max: 180 },
      warning: { min: 0, max: 150 },
    },
  },

  // Heels
  left_heel: {
    upRange: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      pad: { min: 0, max: 40 },
    },
    downRange: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      pad: { min: 0, max: 40 },
    },
    range: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      warning: { min: 30, max: 90 },
    },
  },
  right_heel: {
    upRange: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      pad: { min: 0, max: 40 },
    },
    downRange: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      pad: { min: 0, max: 40 },
    },
    range: {
      perfect: { min: 0, max: 20 },
      normal: { min: 0, max: 30 },
      warning: { min: 30, max: 90 },
    },
  },

  // Foot Index
  left_foot_index: {
    upRange: {
      perfect: { min: 0, max: 30 },
      normal: { min: 0, max: 40 },
      pad: { min: 0, max: 50 },
    },
    downRange: {
      perfect: { min: 0, max: 30 },
      normal: { min: 0, max: 40 },
      pad: { min: 0, max: 50 },
    },
    range: {
      perfect: { min: 0, max: 30 },
      normal: { min: 0, max: 40 },
      warning: { min: 40, max: 90 },
    },
  },
  right_foot_index: {
    upRange: {
      perfect: { min: 0, max: 30 },
      normal: { min: 0, max: 40 },
      pad: { min: 0, max: 50 },
    },
    downRange: {
      perfect: { min: 0, max: 30 },
      normal: { min: 0, max: 40 },
      pad: { min: 0, max: 50 },
    },
    range: {
      perfect: { min: 0, max: 30 },
      normal: { min: 0, max: 40 },
      warning: { min: 40, max: 90 },
    },
  },

  // Pinky, Index, Thumb (hands) - Generic defaults
  left_pinky: {
    upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
    downRange: { perfect: { min: 60, max: 90 }, normal: { min: 50, max: 100 } },
    range: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
  },
  right_pinky: {
    upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
    downRange: { perfect: { min: 60, max: 90 }, normal: { min: 50, max: 100 } },
    range: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
  },
  left_index: {
    upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
    downRange: { perfect: { min: 60, max: 90 }, normal: { min: 50, max: 100 } },
    range: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
  },
  right_index: {
    upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
    downRange: { perfect: { min: 60, max: 90 }, normal: { min: 50, max: 100 } },
    range: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
  },
  left_thumb: {
    upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
    downRange: { perfect: { min: 60, max: 90 }, normal: { min: 50, max: 100 } },
    range: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
  },
  right_thumb: {
    upRange: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
    downRange: { perfect: { min: 60, max: 90 }, normal: { min: 50, max: 100 } },
    range: { perfect: { min: 160, max: 180 }, normal: { min: 150, max: 180 } },
  },
};

// Default for unknown joints
const DEFAULT_STATE_RANGES: StateRangesDefaults = {
  upRange: {
    perfect: { min: 150, max: 180 },
    normal: { min: 140, max: 180 },
    pad: { min: 130, max: 180 },
  },
  downRange: {
    perfect: { min: 60, max: 90 },
    normal: { min: 50, max: 100 },
    pad: { min: 40, max: 110 },
  },
  range: {
    perfect: { min: 150, max: 180 },
    normal: { min: 140, max: 180 },
    warning: { min: 0, max: 140 },
  },
};

// ============================================
// JOINT RECOMMENDATIONS
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
// BUILDER FUNCTIONS
// ============================================

/**
 * Build a tracked joint from template with state-based ranges
 * @param jointCode - The joint code (e.g., 'left_elbow')
 * @param role - 'primary' or 'secondary'
 * @param pairedWith - Optional paired joint code
 * @param isHold - If true, primary joints will have 'range' instead of 'upRange'/'downRange'
 */
export function buildTrackedJoint(
  jointCode: string,
  role: 'primary' | 'secondary',
  pairedWith?: string,
  isHold: boolean = false
): TrackedJointData {
  const defaults = JOINT_DEFAULTS[jointCode] || DEFAULT_STATE_RANGES;
  const useZoneMessages = !isHold && role === 'primary';

  // Build state messages based on exercise type
  const stateMessages = useZoneMessages
    ? {
      // Zone-based messages for up_down primary joints
      perfect: {
        up: { ar: 'ممتاز!', en: 'Perfect!' },
        down: { ar: 'ممتاز!', en: 'Perfect!' },
      },
      normal: {
        up: { ar: 'جيد', en: 'Good' },
        down: { ar: 'جيد', en: 'Good' },
      },
      warning: {
        up: { ar: 'تحقق من وضعك', en: 'Check your position' },
        down: { ar: 'تحقق من وضعك', en: 'Check your position' },
      },
      danger: {
        down: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' },
      },
    }
    : {
      // Simple messages for hold or secondary joints
      perfect: { ar: 'ممتاز!', en: 'Perfect!' },
      normal: { ar: 'جيد', en: 'Good' },
      pad: { ar: 'مقبول', en: 'Acceptable' },
      warning: { ar: 'تحقق من وضعك', en: 'Check your position' },
      danger: { ar: 'توقف! وضع خطير', en: 'Stop! Dangerous position' },
    };

  const baseJoint = {
    joint: jointCode,
    startPose: { min: 150, max: 180 },
    pairedWith,
    stateMessages,
    /** Default: require both sides visible. Bilateral pairs can switch to any_side in JointConfigStep. */
    trackingMode: 'two_sides' as const,
  };

  // Adjust startPose based on joint type
  if (jointCode.includes('shoulder_cross') || jointCode.includes('hip_cross')) {
    baseJoint.startPose = { min: 80, max: 100 };
  } else if (jointCode.includes('shoulder')) {
    baseJoint.startPose = { min: 0, max: 30 };
  } else if (jointCode === 'spine') {
    baseJoint.startPose = { min: 0, max: 20 };
  } else if (jointCode === 'neck_left' || jointCode === 'neck_right') {
    baseJoint.startPose = { min: 80, max: 100 };
  } else if (jointCode === 'neck_spine') {
    baseJoint.startPose = { min: 155, max: 180 };
  } else if (jointCode.includes('ankle')) {
    baseJoint.startPose = { min: 75, max: 110 };
  } else if (jointCode.includes('knee') || jointCode.includes('hip')) {
    // For Hold exercises (like wall sit), knees and hips start at ~90 degrees
    if (isHold) {
      baseJoint.startPose = { min: 75, max: 110 };
    }
  }

  if (role === 'primary') {
    if (isHold) {
      // Hold mode: primary has single range (like secondary)
      return {
        ...baseJoint,
        role: 'primary' as const,
        range: defaults.range,
      } as PrimaryTrackedJointData;
    } else {
      // Up/Down or Push/Pull mode: primary has upRange and downRange
      return {
        ...baseJoint,
        role: 'primary' as const,
        upRange: defaults.upRange,
        downRange: defaults.downRange,
      } as PrimaryTrackedJointData;
    }
  } else {
    return {
      ...baseJoint,
      role: 'secondary' as const,
      range: defaults.range,
    } as SecondaryTrackedJointData;
  }
}

/**
 * Get the paired joint code (left <-> right)
 */
export function getPairedJointCode(jointCode: string): string | undefined {
  if (jointCode.startsWith('left_')) {
    return jointCode.replace('left_', 'right_');
  }
  if (jointCode.startsWith('right_')) {
    return jointCode.replace('right_', 'left_');
  }
  return undefined;
}

/**
 * Check if a joint has a mirror pair
 */
export function hasMirrorPair(jointCode: string): boolean {
  return jointCode.startsWith('left_') || jointCode.startsWith('right_');
}

/**
 * Get state colors for visualization
 */
export const STATE_COLORS: Record<JointStateName, { bg: string; border: string; text: string }> = {
  perfect: { bg: 'bg-green-100', border: 'border-green-500', text: 'text-green-700' },
  normal: { bg: 'bg-yellow-100', border: 'border-yellow-500', text: 'text-yellow-700' },
  pad: { bg: 'bg-orange-100', border: 'border-orange-500', text: 'text-orange-700' },
  warning: { bg: 'bg-red-100', border: 'border-red-400', text: 'text-red-600' },
  danger: { bg: 'bg-red-200', border: 'border-red-600', text: 'text-red-800' },
};

/**
 * State labels for display
 */
export const STATE_LABELS: Record<JointStateName, { ar: string; en: string }> = {
  perfect: { ar: 'مثالي', en: 'Perfect' },
  normal: { ar: 'جيد', en: 'Good' },
  pad: { ar: 'مقبول', en: 'Acceptable' },
  warning: { ar: 'تحذير', en: 'Warning' },
  danger: { ar: 'خطر', en: 'Danger' },
};
