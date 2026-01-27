import { NextRequest, NextResponse } from 'next/server';
import { workoutService } from '@/modules/workouts/workouts.service';

/**
 * GET /api/mobile/workouts
 * Get all published workouts for mobile app
 */
export async function GET(request: NextRequest) {
  try {
    const workouts = await workoutService.getPublishedForMobile();

    return NextResponse.json({
      success: true,
      data: workouts,
      meta: {
        count: workouts.length,
        timestamp: new Date().toISOString(),
      },
    });
  } catch (error) {
    console.error('Error fetching workouts for mobile:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch workouts' },
      { status: 500 }
    );
  }
}
