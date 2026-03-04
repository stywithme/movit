/**
 * Assessment Service - Body Scan
 * ==============================
 *
 * Service for Body Scan assessment CRUD and progress tracking.
 */

import { getPrisma } from '@/lib/prisma/client';
import { levelProfileService } from '@/modules/level-profile/level-profile.service';
import { reassessmentService } from '@/modules/reassessment/reassessment.service';
import { prescriptionService } from '@/modules/prescription/prescription.service';
import { activePlanService } from '@/modules/active-plan/active-plan.service';
import type { BodyScanResultCreate, BodyScanProgress, DomainScores } from './assessment.types';

// Minimum Detectable Change threshold (points) for "real" improvement
const MDC_THRESHOLD = 5;

// ============================================
// SERVICE
// ============================================

export const assessmentService = {
  /**
   * Create a new Body Scan assessment result
   */
  async create(data: BodyScanResultCreate) {
    const prisma = await getPrisma();

    const result = await prisma.bodyScanResult.create({
      data: {
        userId: data.userId,
        type: data.type,
        bodyScore: data.bodyScore,
        mobilityScore: data.mobilityScore,
        controlScore: data.controlScore,
        symmetryScore: data.symmetryScore ?? null,
        safetyScore: data.safetyScore,
        fitnessLevel: data.fitnessLevel,
        regions: data.regions as object,
        symmetryData: data.symmetryData ? (data.symmetryData as object) : undefined,
        hypotheses: data.hypotheses ? (data.hypotheses as object) : undefined,
        safetyGates: data.safetyGates ? (data.safetyGates as object) : undefined,
        painFlags: data.painFlags ? (data.painFlags as object) : undefined,
        recommendations: data.recommendations ? (data.recommendations as object) : undefined,
        parqPassed: data.parqPassed,
        parqFlags: data.parqFlags ? (data.parqFlags as object) : undefined,
        rawReportIds: data.rawReportIds ? (data.rawReportIds as object) : undefined,
        previousId: data.previousId ?? null,
        durationMs: data.durationMs ?? null,
        movementCount: data.movementCount,
      },
    });

    // Phase 1: Auto-calculate level profile from the new assessment
    try {
      await levelProfileService.calculateFromAssessment(result.id);
      console.log(`[Assessment] Level profile calculated for assessment ${result.id}`);
    } catch (error) {
      console.warn('[Assessment] Failed to calculate level profile:', error);
    }

    // Phase 3: Mark any pending reassessment as completed
    try {
      await reassessmentService.markCompleted(data.userId, result.id);
    } catch (error) {
      console.warn('[Assessment] Failed to mark reassessment:', error);
    }

    // Phase 4: Auto-prescription — if user has no active program, recommend and auto-enroll
    let autoPrescription: { programId: string; programName: Record<string, string>; enrolled: boolean } | null = null;
    try {
      const prisma = await getPrisma();

      // Check if user already has an active plan with a program
      const existingPlan = await prisma.activePlan.findUnique({
        where: { userId: data.userId },
        include: {
          programs: { where: { status: 'active' } },
        },
      });

      const hasActiveProgram = existingPlan?.programs.length ? existingPlan.programs.length > 0 : false;

      if (!hasActiveProgram) {
        const prescription = await prescriptionService.recommend(data.userId);

        if (prescription.recommendedProgram) {
          await activePlanService.enrollProgram(data.userId, prescription.recommendedProgram.id);
          autoPrescription = {
            programId: prescription.recommendedProgram.id,
            programName: prescription.recommendedProgram.name,
            enrolled: true,
          };
          console.log(`[Assessment] Auto-enrolled user ${data.userId} in program ${prescription.recommendedProgram.id}`);
        }
      }
    } catch (error) {
      console.warn('[Assessment] Auto-prescription failed (non-fatal):', error);
    }

    return { ...result, autoPrescription };
  },

  /**
   * Get the latest assessment for a user
   */
  async getLatest(userId: string) {
    const prisma = await getPrisma();

    return prisma.bodyScanResult.findFirst({
      where: { userId },
      orderBy: { completedAt: 'desc' },
    });
  },

  /**
   * Get a single assessment by ID
   */
  async getById(id: string) {
    const prisma = await getPrisma();

    return prisma.bodyScanResult.findUnique({
      where: { id },
    });
  },

  /**
   * Get all assessments for a user, ordered by date descending
   */
  async getHistory(userId: string) {
    const prisma = await getPrisma();

    return prisma.bodyScanResult.findMany({
      where: { userId },
      orderBy: { completedAt: 'desc' },
    });
  },

  /**
   * Compare the latest assessment with the previous one
   */
  async getProgress(userId: string): Promise<BodyScanProgress | null> {
    const prisma = await getPrisma();

    const assessments = await prisma.bodyScanResult.findMany({
      where: { userId },
      orderBy: { completedAt: 'desc' },
      take: 2,
    });

    if (assessments.length === 0) return null;

    const current = assessments[0];
    const currentDomain: DomainScores = {
      mobility: current.mobilityScore,
      control: current.controlScore,
      symmetry: current.symmetryScore,
      safety: current.safetyScore,
    };

    const progress: BodyScanProgress = {
      current: {
        bodyScore: current.bodyScore,
        domainScores: currentDomain,
        completedAt: current.completedAt.toISOString(),
      },
    };

    if (assessments.length === 2) {
      const previous = assessments[1];
      const previousDomain: DomainScores = {
        mobility: previous.mobilityScore,
        control: previous.controlScore,
        symmetry: previous.symmetryScore,
        safety: previous.safetyScore,
      };

      const bodyScoreDelta = current.bodyScore - previous.bodyScore;
      const mobilityDelta = current.mobilityScore - previous.mobilityScore;
      const controlDelta = current.controlScore - previous.controlScore;
      const safetyDelta = current.safetyScore - previous.safetyScore;

      const symmetryDelta =
        current.symmetryScore != null && previous.symmetryScore != null
          ? current.symmetryScore - previous.symmetryScore
          : null;

      progress.previous = {
        bodyScore: previous.bodyScore,
        domainScores: previousDomain,
        completedAt: previous.completedAt.toISOString(),
      };

      progress.changes = {
        bodyScoreDelta,
        mobilityDelta,
        controlDelta,
        symmetryDelta,
        safetyDelta,
        isRealImprovement: bodyScoreDelta > MDC_THRESHOLD,
      };
    }

    return progress;
  },

  /**
   * Soft delete an assessment (sets completedAt to epoch as a soft-delete marker)
   * Note: The schema doesn't have a deletedAt field, so we remove via hard delete.
   */
  async delete(id: string) {
    const prisma = await getPrisma();

    return prisma.bodyScanResult.delete({
      where: { id },
    });
  },
};
