import { NextRequest, NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * Helper function to publish an exercise
 */
async function publishExercise(id: string) {
  // Check if exercise exists
  const existing = await exerciseService.getById(id);
  if (!existing) {
    return NextResponse.json(
      { success: false, error: 'Exercise not found' },
      { status: 404 }
    );
  }

  // TODO: Validate exercise has all required data before publishing
  // - At least one pose variant
  // - At least one difficulty level per pose variant
  // - Phases and angle rules configured

  const exercise = await exerciseService.publish(id);

  return NextResponse.json({
    success: true,
    data: exercise,
    message: 'Exercise published successfully',
  });
}

/**
 * POST /api/exercises/:id/publish
 * Publish an exercise
 */
export async function POST(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    return await publishExercise(id);
  } catch (error) {
    console.error('Error publishing exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to publish exercise' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/exercises/:id/publish
 * Publish an exercise (alternative method)
 */
export async function PUT(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    return await publishExercise(id);
  } catch (error) {
    console.error('Error publishing exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to publish exercise' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/exercises/:id/publish
 * Unpublish an exercise (back to draft)
 */
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;

    // Check if exercise exists
    const existing = await exerciseService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }

    const exercise = await exerciseService.unpublish(id);

    return NextResponse.json({
      success: true,
      data: exercise,
      message: 'Exercise unpublished successfully',
    });
  } catch (error) {
    console.error('Error unpublishing exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to unpublish exercise' },
      { status: 500 }
    );
  }
}

