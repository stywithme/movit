import { NextRequest, NextResponse } from 'next/server';
import { getPrisma } from '@/lib/prisma/client';
import { buildExerciseConfig, exerciseFullInclude } from '@/modules/exercises/json-builder';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/exercises/:id/config
 * Get full exercise configuration for mobile app (Android JSON Schema format)
 */
export async function GET(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const prisma = await getPrisma();
    
    const exercise = await prisma.exercise.findUnique({
      where: { id, deletedAt: null },
      include: exerciseFullInclude,
    });

    if (!exercise) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }

    // Transform to Android JSON Schema format using json-builder
    const config = buildExerciseConfig(exercise as Parameters<typeof buildExerciseConfig>[0]);

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
