import { NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';

/**
 * GET /api/exercises/published
 * Get all published exercises (for mobile API)
 */
export async function GET() {
  try {
    const exercises = await exerciseService.getPublished();

    // Transform for mobile API response
    const data = exercises.map((exercise) => ({
      id: exercise.id,
      name: exercise.name,
      description: exercise.description,
      category: {
        code: exercise.category.code,
        name: exercise.category.name,
      },
      countingMethod: exercise.countingMethod.code,
      primaryImage: exercise.media[0]?.url || null,
      updatedAt: exercise.updatedAt.toISOString(),
    }));

    return NextResponse.json({
      success: true,
      data,
    });
  } catch (error) {
    console.error('Error fetching published exercises:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch published exercises' },
      { status: 500 }
    );
  }
}

