import { NextRequest, NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';
import { LocalizedText } from '@/lib/types/localized';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/exercises/:id/config
 * Get full exercise configuration for mobile app
 */
export async function GET(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const exercise = await exerciseService.getById(id);

    if (!exercise) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }

    // Group attributes by type
    const muscles: string[] = [];
    const equipment: string[] = [];
    const tags: string[] = [];

    exercise.attributes.forEach((attr) => {
      const attributeCode = attr.attributeValue.attribute.code;
      if (attributeCode === 'muscle') muscles.push(attr.attributeValue.code);
      if (attributeCode === 'equipment') equipment.push(attr.attributeValue.code);
      if (attributeCode === 'tag') tags.push(attr.attributeValue.code);
    });

    // Transform to mobile API format
    const config = {
      id: exercise.id,
      name: exercise.name as LocalizedText,
      description: exercise.description as LocalizedText | null,
      instructions: exercise.instructions as LocalizedText | null,
      category: {
        code: exercise.category.code,
        name: exercise.category.name as LocalizedText,
      },
      countingMethod: exercise.countingMethod.code,
      primaryImage: exercise.media.find((m) => m.isPrimary)?.url || exercise.media[0]?.url || null,
      muscles,
      equipment,
      tags,
      updatedAt: exercise.updatedAt.toISOString(),
      poseVariants: exercise.poseVariants.map((pv) => ({
        id: pv.id,
        name: pv.name as LocalizedText,
        cameraPosition: pv.cameraPosition.code,
        referenceImage: pv.referenceImageUrl,
        difficultyLevels: pv.difficultyLevels.map((dl) => ({
          id: dl.id,
          level: dl.difficultyType.code,
          name: dl.name as LocalizedText,
          description: dl.description as LocalizedText | null,
          startPoseAngles: dl.startPoseAngles,
          repCountingConfig: dl.repCountingConfig,
          phases: dl.phases.map((phase) => ({
            code: phase.code,
            name: phase.name as LocalizedText,
            rules: phase.angleRules.map((rule) => ({
              joint: rule.joint.code,
              min: rule.minAngle,
              max: rule.maxAngle,
              errorOver: rule.errorMessageOver as LocalizedText | null,
              errorUnder: rule.errorMessageUnder as LocalizedText | null,
              priority: rule.priority,
            })),
          })),
          feedbackMessages: {
            motivational: dl.feedbackMessages
              .filter((fm) => fm.type === 'motivational')
              .map((fm) => fm.message as LocalizedText),
            common_mistake: dl.feedbackMessages
              .filter((fm) => fm.type === 'common_mistake')
              .map((fm) => fm.message as LocalizedText),
            tip: dl.feedbackMessages
              .filter((fm) => fm.type === 'tip')
              .map((fm) => fm.message as LocalizedText),
          },
        })),
      })),
    };

    return NextResponse.json({
      success: true,
      data: config,
    });
  } catch (error) {
    console.error('Error fetching exercise config:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch exercise config' },
      { status: 500 }
    );
  }
}

