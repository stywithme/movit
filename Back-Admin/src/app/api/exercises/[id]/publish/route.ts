import { NextRequest, NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * POST /api/exercises/:id/publish
 * Publish an exercise
 */
export async function POST(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    
    // Verify exercise exists
    const exercise = await exerciseService.getById(id);
    if (!exercise) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }
    
    // TODO: Add validation before publishing
    // - Must have at least one pose variant
    // - Must have at least one primary tracked joint
    // - Must have all 3 difficulty levels
    
    await exerciseService.publish(id);
    
    return NextResponse.json({
      success: true,
      message: 'Exercise published successfully',
    });
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
    
    await exerciseService.unpublish(id);
    
    return NextResponse.json({
      success: true,
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
