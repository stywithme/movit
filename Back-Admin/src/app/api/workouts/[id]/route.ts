import { NextRequest, NextResponse } from 'next/server';
import { workoutService } from '@/modules/workouts/workouts.service';
import { validateUpdateWorkout } from '@/modules/workouts/workouts.validation';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/workouts/[id]
 * Get a single workout by ID
 */
export async function GET(
  request: NextRequest,
  { params }: RouteParams
) {
  try {
    const { id } = await params;
    const workout = await workoutService.getById(id);

    if (!workout) {
      return NextResponse.json(
        { success: false, error: 'Workout not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      data: workout,
    });
  } catch (error) {
    console.error('Error fetching workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch workout' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/workouts/[id]
 * Update a workout
 */
export async function PUT(
  request: NextRequest,
  { params }: RouteParams
) {
  try {
    const { id } = await params;
    const body = await request.json();

    // Check if workout exists
    const existing = await workoutService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Workout not found' },
        { status: 404 }
      );
    }

    // Validate input
    const errors = validateUpdateWorkout(body);
    if (errors.length > 0) {
      return NextResponse.json(
        { success: false, errors },
        { status: 400 }
      );
    }

    const workout = await workoutService.update(id, body);

    return NextResponse.json({
      success: true,
      data: workout,
    });
  } catch (error) {
    console.error('Error updating workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update workout' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/workouts/[id]
 * Soft delete a workout
 */
export async function DELETE(
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

    await workoutService.delete(id);

    return NextResponse.json({
      success: true,
      message: 'Workout deleted successfully',
    });
  } catch (error) {
    console.error('Error deleting workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete workout' },
      { status: 500 }
    );
  }
}
