import { NextRequest, NextResponse } from 'next/server';
import { workoutService } from '@/modules/workouts/workouts.service';
import { validateCreateWorkout } from '@/modules/workouts/workouts.validation';

/**
 * GET /api/workouts
 * List all workouts with optional filters
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const status = searchParams.get('status') as 'draft' | 'published' | null;
    const type = searchParams.get('type') as 'circuit' | 'super_set' | null;
    const search = searchParams.get('search');
    const page = parseInt(searchParams.get('page') || '1');
    const limit = parseInt(searchParams.get('limit') || '20');

    const result = await workoutService.list({
      status: status || undefined,
      type: type || undefined,
      search: search || undefined,
      page,
      limit,
    });

    return NextResponse.json({
      success: true,
      data: result.workouts,
      pagination: result.pagination,
    });
  } catch (error) {
    console.error('Error fetching workouts:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch workouts' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/workouts
 * Create a new workout
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate input
    const errors = validateCreateWorkout(body);
    if (errors.length > 0) {
      return NextResponse.json(
        { success: false, errors },
        { status: 400 }
      );
    }

    const workout = await workoutService.create(body);

    return NextResponse.json({
      success: true,
      data: workout,
    }, { status: 201 });
  } catch (error) {
    console.error('Error creating workout:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to create workout' },
      { status: 500 }
    );
  }
}
