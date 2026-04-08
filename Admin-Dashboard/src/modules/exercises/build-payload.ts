import { useWizardStore } from '@/components/wizard/WizardContext';
import type { TrackedJointData, PositionCheckData } from './exercises.validation';
import { normalizeCameraPositionIds } from '@/lib/utils';

/**
 * Build the API payload from the current wizard store state.
 * Shared between the "new" and "edit" exercise pages.
 */
export function buildExercisePayload() {
  const store = useWizardStore.getState();
  const isHold = store.countingMethod.countingMethodCode === 'hold';

  const allJoints = store.jointConfig.trackedJoints || [];

  const mapJoint = (joint: TrackedJointData) => {
    if (joint.role === 'primary') {
      const base = {
        joint: joint.joint,
        role: 'primary' as const,
        startPose: joint.startPose,
        stateMessages: joint.stateMessages,
        pairedWith: joint.pairedWith,
        invertIndicator: joint.invertIndicator,
      };
      return isHold
        ? { ...base, range: joint.range }
        : { ...base, upRange: joint.upRange, downRange: joint.downRange };
    }
    return {
      joint: joint.joint,
      role: 'secondary' as const,
      startPose: joint.startPose,
      range: joint.range,
      ...(joint.phaseRanges && Object.keys(joint.phaseRanges).length > 0 && { phaseRanges: joint.phaseRanges }),
      stateMessages: joint.stateMessages,
      pairedWith: joint.pairedWith,
    };
  };

  const trackedJointsConfig = allJoints.map(mapJoint);

  const positionChecks = (store.positionChecks.positionChecks || []).map((pc: PositionCheckData, idx: number) => ({
    checkId: pc.checkId,
    type: pc.type,
    landmarks: pc.landmarks,
    condition: pc.condition,
    activePhases: pc.activePhases,
    errorMessage: pc.errorMessage,
    severity: pc.severity,
    cooldownMs: pc.cooldownMs,
    minErrorFrames: pc.minErrorFrames,
    sortOrder: idx + 1,
  }));

  const feedbackAssignments = (store.extras.feedbackAssignments || []).map((a, idx) => ({
    messageId: a.messageId,
    target: 'feedback',
    context: a.context,
    sortOrder: idx + 1,
  }));

  const repCountingConfig = isHold
    ? { duration: store.repConfig.duration || 30, gracePeriodMs: store.repConfig.gracePeriodMs || 2500 }
    : { reps: store.repConfig.reps || 12, minRepIntervalMs: store.repConfig.minRepIntervalMs || 1500, maxRepIntervalMs: store.repConfig.maxRepIntervalMs || 5000 };

  return {
    name: store.basicInfo.name,
    description: store.basicInfo.description,
    instructions: store.basicInfo.instructions,
    categoryId: store.basicInfo.categoryId,
    countingMethodId: store.countingMethod.countingMethodId,
    imageUrl: store.basicInfo.imageUrl || undefined,
    muscles: store.extras.muscles,
    equipment: store.extras.equipment,
    tags: store.extras.tags,
    repCountingConfig,
    poseVariants: normalizeCameraPositionIds(store.cameraPosition.cameraPositionIds).map((posePositionId, index) => {
      const mappedJoints = allJoints.map(mapJoint);
      return {
        name: store.basicInfo.name,
        posePositionId,
        trackedJointsConfig: mappedJoints.length > 0 ? mappedJoints : trackedJointsConfig,
        positionChecks,
        messageAssignments: feedbackAssignments.length > 0 ? feedbackAssignments : undefined,
        sortOrder: index + 1,
      };
    }),
    supportsWeight: store.weightConfig.supportsWeight,
    minWeight: store.weightConfig.minWeight,
    maxWeight: store.weightConfig.maxWeight,
    defaultWeight: store.weightConfig.defaultWeight,
    reportMetrics: {
      primary: store.reportMetrics.primary,
      optional: store.reportMetrics.optional,
      excluded: store.reportMetrics.excluded,
    },
    // null (not undefined) so JSON.stringify keeps the key and the backend clears isBilateral
    bilateralConfig: store.bilateralConfig.enabled
      ? { switchEvery: store.bilateralConfig.switchEvery, startSide: store.bilateralConfig.startSide }
      : null,
  };
}
