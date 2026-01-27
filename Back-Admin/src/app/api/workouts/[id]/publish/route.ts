import { NextRequest, NextResponse } from 'next/server';
import { workoutService } from '@/modules/workouts/workouts.service';
import { validateCanPublish } from '@/modules/workouts/workouts.validation';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * POST /api/workouts/[id]/publish
 * Publish a workout
 */
export async function POST(
  request: NextRequest,
  { params }: RouteParams
) {
  try {
    const { id } = await params;

    // Get workout with exercises
    const workout = await workoutService.getById(id);
    if (!workout) {
      return NextResponse.json(
        { success: false, error: 'Workout not found' },
        { status: 404 }
      );
    }

    // Validate can publish
    const errors = validateCanPublish({
      name: workout.name as { ar?: string; en?: string },
      type: workout.type,
      executionMode: workout.executionMode,
      exercises: workout.exercises,
    });

    if (errors.length > 0) {
      return NextResponse.json(
        { success: false, errors },
        { status: 400 }
      );
    }

    await workoutService.publish(id);

    return NextResponse.json({
      success: true,
      message: 'Workout published successfully',
    });
  } catch (error) {
    console.error('Error publishing workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to publish workout' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/workouts/[id]/publish
 * Unpublish a workout (back to draft)
 */
export async function DELETE(
  request: NextRequest,
  { params }: RouteParams
) {
  try {
    const { id } = await params;

    // Check if workout exists
    const workout = await workoutService.getById(id);
    if (!workout) {
      return NextResponse.json(
        { success: false, error: 'Workout not found' },
        { status: 404 }
      );
    }

    await workoutService.unpublish(id);

    return NextResponse.json({
      success: true,
      message: 'Workout unpublished successfully',
    });
  } catch (error) {
    console.error('Error unpublishing workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to unpublish workout' },
      { status: 500 }
    );
  }
}
