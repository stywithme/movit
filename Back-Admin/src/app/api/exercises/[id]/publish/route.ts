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
    
    // Check if exercise exists
    const existing = await exerciseService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }
    
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

    // Check if exercise exists
    const existing = await exerciseService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Exercise not found' },
        { status: 404 }
      );
    }
    
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
