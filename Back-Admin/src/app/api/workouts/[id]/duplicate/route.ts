import { NextRequest, NextResponse } from 'next/server';
import { workoutService } from '@/modules/workouts/workouts.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * POST /api/workouts/[id]/duplicate
 * Duplicate a workout
 */
export async function POST(
  request: NextRequest,
  { params }: RouteParams
) {
  try {
    const { id } = await params;

    // Check if workout exists
    const existing = await workoutService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Workout not found' },
        { status: 404 }
      );
    }

    const duplicate = await workoutService.duplicate(id);

    return NextResponse.json({
      success: true,
      data: duplicate,
      message: 'Workout duplicated successfully',
    }, { status: 201 });
  } catch (error) {
    console.error('Error duplicating workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to duplicate workout' },
      { status: 500 }
    );
  }
}
