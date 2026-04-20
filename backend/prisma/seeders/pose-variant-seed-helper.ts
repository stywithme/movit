import type { PrismaClient } from '@prisma/client';
import type { EnsureMessageTemplate } from './messages';

/** Stable template `code` for exercise-linked feedback (survives re-seed; merges audio from DB). */
export function stableExerciseMessageCode(slug: string, parts: (string | number)[]): string {
  const tail = parts
    .map((p) => String(p))
    .join('_')
    .replace(/[^a-zA-Z0-9_]/g, '_')
    .replace(/_+/g, '_')
    .replace(/^_|_$/g, '');
  const raw = `exmsg_${slug}_${tail}`;
  return raw.slice(0, 190);
}

/** Legacy JSON `cameraPosition` → canonical `pose_positions.code` */
export const LEGACY_POSE_POSITION_MAP: Record<string, string> = {
  side_view: 'standing_side',
  front_view: 'standing_front',
  back_view: 'standing_back',
  side_view_left: 'standing_side_left',
  side_view_right: 'standing_side_right',
  diagonal_view: 'standing_diagonal',
  front: 'standing_front',
  back: 'standing_back',
  side: 'standing_side',
  side_left: 'standing_side_left',
  side_right: 'standing_side_right',
  diagonal: 'standing_diagonal',
};

export function resolvePosePositionCode(
  variant: { posePosition?: string; cameraPosition?: string },
  positionByCode: Map<string, string>,
): string {
  const raw = (variant.posePosition || variant.cameraPosition || 'standing_side').trim();
  if (positionByCode.has(raw)) return raw;
  const normalized = raw.toLowerCase().replace(/-/g, '_');
  if (positionByCode.has(normalized)) return normalized;
  const mapped = LEGACY_POSE_POSITION_MAP[normalized];
  if (mapped && positionByCode.has(mapped)) return mapped;
  return normalized;
}

export type SeedPoseVariantJson = {
  name: { ar: string; en: string };
  cameraPosition?: string;
  posePosition?: string;
  trackedJoints?: unknown[];
  positionChecks?: Array<{
    id: string;
    type: string;
    landmarks: Record<string, unknown>;
    condition: Record<string, unknown>;
    activePhases?: string[];
    errorMessage: { ar: string; en: string };
    severity?: string;
    cooldownMs?: number;
    minErrorFrames?: number;
  }>;
  feedbackMessages?: {
    motivational?: Array<{ ar: string; en: string }>;
    tips?: Array<{ ar: string; en: string }>;
  };
};

/**
 * Creates pose variants, position checks, joint-state message templates + assignments,
 * and feedback (motivational/tip) — same behaviour as JSON import in `exercises-workouts`.
 */
export async function applyPoseVariantsForExercise(
  prisma: PrismaClient,
  params: {
    exerciseId: string;
    slug: string;
    poseVariants: SeedPoseVariantJson[];
    positionByCode: Map<string, string>;
    ensureMessageTemplate: EnsureMessageTemplate;
  },
): Promise<void> {
  const { exerciseId, slug, poseVariants, positionByCode, ensureMessageTemplate } = params;

  await prisma.poseVariant.deleteMany({ where: { exerciseId } });

  for (let pvIndex = 0; pvIndex < poseVariants.length; pvIndex++) {
    const variant = poseVariants[pvIndex];
    const posCode = resolvePosePositionCode(variant, positionByCode);
    const posePositionId = positionByCode.get(posCode);
    if (!posePositionId) {
      throw new Error(`Pose position not found for code "${posCode}" (variant: ${JSON.stringify(variant.name)})`);
    }

    const poseVariant = await prisma.poseVariant.create({
      data: {
        exerciseId,
        posePositionId,
        name: variant.name,
        trackedJointsConfig: (variant.trackedJoints as object) || undefined,
        sortOrder: pvIndex + 1,
      },
    });

    const assignments: Array<{
      poseVariantId: string;
      messageId: string;
      target: string;
      context?: string | null;
      jointCode?: string | null;
      zone?: string | null;
      checkId?: string | null;
      sortOrder: number;
    }> = [];
    let assignmentOrder = 1;
    const addAssignment = (data: Omit<(typeof assignments)[number], 'sortOrder'>) => {
      assignments.push({ ...data, sortOrder: assignmentOrder++ });
    };

    if (variant.positionChecks && variant.positionChecks.length > 0) {
      await prisma.positionCheck.createMany({
        data: variant.positionChecks.map((check, index) => ({
          poseVariantId: poseVariant.id,
          checkId: check.id,
          type: check.type,
          landmarks: check.landmarks as object,
          condition: check.condition as object,
          activePhases: check.activePhases || [],
          errorMessage: check.errorMessage,
          severity: check.severity || 'warning',
          cooldownMs: check.cooldownMs ?? 2000,
          minErrorFrames: check.minErrorFrames ?? 3,
          sortOrder: index + 1,
        })),
      });

      for (const check of variant.positionChecks) {
        if (!check.errorMessage) continue;
        const messageId = await ensureMessageTemplate({
          code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'pos', check.id]),
          category: 'position',
          context: 'error',
          content: check.errorMessage,
          tags: ['position', 'error'],
        });
        addAssignment({
          poseVariantId: poseVariant.id,
          messageId,
          target: 'position',
          context: 'error',
          checkId: check.id,
        });
      }
    }

    const trackedJoints = Array.isArray(variant.trackedJoints)
      ? (variant.trackedJoints as Array<Record<string, unknown>>)
      : [];
    for (const joint of trackedJoints) {
      const jointData = joint as {
        joint?: string;
        code?: string;
        stateMessages?: Record<string, unknown>;
        phaseStateMessages?: Record<string, Record<string, unknown>>;
      };
      const jointCode = jointData.joint || jointData.code;
      if (!jointCode) continue;

      if (jointData.stateMessages) {
        const stateMessages = jointData.stateMessages as Record<string, unknown>;
        for (const [state, value] of Object.entries(stateMessages)) {
          if (!value) continue;
          if (typeof value === 'object' && ('up' in value || 'down' in value)) {
            const zoneValue = value as Record<string, { ar?: string; en?: string; audioAr?: string; audioEn?: string } | undefined>;
            for (const zone of ['up', 'down'] as const) {
              const msg = zoneValue[zone];
              if (!msg || (!msg.ar && !msg.en)) continue;
              const messageId = await ensureMessageTemplate({
                code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'j', jointCode, state, zone]),
                category: 'state',
                context: state,
                content: msg,
                tags: ['state', state],
              });
              addAssignment({
                poseVariantId: poseVariant.id,
                messageId,
                target: 'joint_state',
                context: state,
                jointCode,
                zone,
              });
            }
          } else {
            const msg = value as { ar?: string; en?: string; audioAr?: string; audioEn?: string };
            if (!msg || (!msg.ar && !msg.en)) continue;
            const messageId = await ensureMessageTemplate({
              code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'j', jointCode, state]),
              category: 'state',
              context: state,
              content: msg,
              tags: ['state', state],
            });
            addAssignment({
              poseVariantId: poseVariant.id,
              messageId,
              target: 'joint_state',
              context: state,
              jointCode,
            });
          }
        }
      }

      /** Per-phase secondary joint messages — `context` is `phase:state` for json-builder / Android merge */
      if (jointData.phaseStateMessages) {
        const phaseMap = jointData.phaseStateMessages;
        for (const [phase, phaseMsgs] of Object.entries(phaseMap)) {
          if (!phaseMsgs || typeof phaseMsgs !== 'object') continue;
          for (const [state, value] of Object.entries(phaseMsgs)) {
            if (!value) continue;
            if (typeof value === 'object' && ('up' in value || 'down' in value)) {
              const zoneValue = value as Record<string, { ar?: string; en?: string; audioAr?: string; audioEn?: string } | undefined>;
              for (const zone of ['up', 'down'] as const) {
                const msg = zoneValue[zone];
                if (!msg || (!msg.ar && !msg.en)) continue;
                const messageId = await ensureMessageTemplate({
                  code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'j', jointCode, 'ph', phase, state, zone]),
                  category: 'state',
                  context: `${phase}:${state}`,
                  content: msg,
                  tags: ['state', 'phase', phase, state],
                });
                addAssignment({
                  poseVariantId: poseVariant.id,
                  messageId,
                  target: 'joint_state_phase',
                  context: `${phase}:${state}`,
                  jointCode,
                  zone,
                });
              }
            } else {
              const msg = value as { ar?: string; en?: string; audioAr?: string; audioEn?: string };
              if (!msg || (!msg.ar && !msg.en)) continue;
              const messageId = await ensureMessageTemplate({
                code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'j', jointCode, 'ph', phase, state]),
                category: 'state',
                context: `${phase}:${state}`,
                content: msg,
                tags: ['state', 'phase', phase, state],
              });
              addAssignment({
                poseVariantId: poseVariant.id,
                messageId,
                target: 'joint_state_phase',
                context: `${phase}:${state}`,
                jointCode,
              });
            }
          }
        }
      }
    }

    const feedbackMessages = variant.feedbackMessages || {};
    const motivational = feedbackMessages.motivational || [];
    const tips = feedbackMessages.tips || [];

    let motIndex = 0;
    for (const msg of motivational) {
      const messageId = await ensureMessageTemplate({
        code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'mot', motIndex++]),
        category: 'motivational',
        context: 'motivational',
        content: msg,
        tags: ['motivational'],
      });
      addAssignment({
        poseVariantId: poseVariant.id,
        messageId,
        target: 'feedback',
        context: 'motivational',
      });
    }

    let tipIndex = 0;
    for (const msg of tips) {
      const messageId = await ensureMessageTemplate({
        code: stableExerciseMessageCode(slug, ['pv', pvIndex + 1, 'tip', tipIndex++]),
        category: 'tip',
        context: 'tip',
        content: msg,
        tags: ['tip'],
      });
      addAssignment({
        poseVariantId: poseVariant.id,
        messageId,
        target: 'feedback',
        context: 'tip',
      });
    }

    if (assignments.length > 0) {
      await prisma.feedbackMessageAssignment.createMany({ data: assignments });
    }
  }
}
