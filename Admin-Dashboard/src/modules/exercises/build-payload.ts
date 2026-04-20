import { useWizardStore } from '@/components/wizard/WizardContext';
import type { TrackedJointData, PositionCheckData } from './exercises.validation';
import { normalizeCameraPositionIds } from '@/lib/utils';

/** Remove dashboard-only fields from localized text before API */
function sanitizeLocalizedForApi(
  lt: { ar?: string; en?: string; audioAr?: string; audioEn?: string; sourceMessageCode?: string; sourceMessageId?: string } | undefined
): { ar: string; en: string; audioAr?: string; audioEn?: string } | undefined {
  if (!lt) return undefined;
  return {
    ar: lt.ar ?? '',
    en: lt.en ?? '',
    ...(lt.audioAr ? { audioAr: lt.audioAr } : {}),
    ...(lt.audioEn ? { audioEn: lt.audioEn } : {}),
  };
}

function sanitizeStateMessageValue(
  val: unknown
): unknown {
  if (!val || typeof val !== 'object') return val;
  if ('up' in val || 'down' in val) {
    const z = val as { up?: unknown; down?: unknown };
    return {
      ...(z.up ? { up: sanitizeLocalizedForApi(z.up as Parameters<typeof sanitizeLocalizedForApi>[0]) } : {}),
      ...(z.down ? { down: sanitizeLocalizedForApi(z.down as Parameters<typeof sanitizeLocalizedForApi>[0]) } : {}),
    };
  }
  return sanitizeLocalizedForApi(val as Parameters<typeof sanitizeLocalizedForApi>[0]);
}

function sanitizeStateMessages(
  sm: TrackedJointData['stateMessages']
): TrackedJointData['stateMessages'] {
  if (!sm) return sm;
  const out: Record<string, unknown> = {};
  for (const key of ['perfect', 'normal', 'pad', 'warning', 'danger'] as const) {
    const v = sm[key];
    if (v === undefined) continue;
    out[key] = sanitizeStateMessageValue(v);
  }
  return out as TrackedJointData['stateMessages'];
}

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
        stateMessages: sanitizeStateMessages(joint.stateMessages),
        pairedWith: joint.pairedWith,
        invertIndicator: joint.invertIndicator,
      };
      return isHold
        ? { ...base, range: joint.range }
        : { ...base, upRange: joint.upRange, downRange: joint.downRange };
    }

    // Secondary joints only — `phaseStateMessages` is not on primary
    const phaseStateMessages = joint.phaseStateMessages
      ? Object.fromEntries(
          Object.entries(joint.phaseStateMessages).map(([k, v]) => [k, sanitizeStateMessages(v)])
        )
      : undefined;

    const hasPhaseRanges = joint.phaseRanges && Object.keys(joint.phaseRanges).length > 0;
    const hasPhaseMsgs =
      joint.phaseStateMessages && Object.keys(joint.phaseStateMessages).length > 0;
    return {
      joint: joint.joint,
      role: 'secondary' as const,
      startPose: joint.startPose,
      range: joint.range,
      ...(hasPhaseRanges && { phaseRanges: joint.phaseRanges }),
      ...(hasPhaseMsgs && phaseStateMessages && { phaseStateMessages }),
      stateMessages: sanitizeStateMessages(joint.stateMessages),
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
    errorMessage: sanitizeLocalizedForApi(pc.errorMessage as Parameters<typeof sanitizeLocalizedForApi>[0]),
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

  const b = store.blueprintExerciseMeta ?? {
    movementPattern: '',
    loadCapability: '',
    familyKey: '',
    familyOrder: '',
  };
  const familyOrderNum =
    b.familyOrder.trim() === '' ? undefined : Number.parseInt(b.familyOrder, 10);

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
    ...(b.movementPattern ? { movementPattern: b.movementPattern } : {}),
    ...(b.loadCapability ? { loadCapability: b.loadCapability } : {}),
    ...(b.familyKey.trim() ? { familyKey: b.familyKey.trim() } : {}),
    ...(familyOrderNum !== undefined && Number.isFinite(familyOrderNum)
      ? { familyOrder: familyOrderNum }
      : {}),
  };
}
