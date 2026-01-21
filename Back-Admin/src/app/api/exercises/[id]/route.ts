import { NextRequest, NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/exercises/:id
 * Get a single exercise with all details
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

    return NextResponse.json({
      success: true,
      data: exercise,
    });
  } catch (error) {
    console.error('Error fetching exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch exercise' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/exercises/:id
 * Update an exercise (state-based system)
 */
export async function PUT(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const body = await request.json();

    // Check if exercise exists
    const existing = await exerciseService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }

    const exercise = await exerciseService.update(id, body);

    return NextResponse.json({
      success: true,
      data: exercise,
    });
  } catch (error) {
    console.error('Error updating exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update exercise' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/exercises/:id
 * Soft delete an exercise
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

    await exerciseService.delete(id);

    return NextResponse.json({
      success: true,
      message: 'Exercise deleted successfully',
    });
  } catch (error) {
    console.error('Error deleting exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete exercise' },
      { status: 500 }
    );
  }
}
