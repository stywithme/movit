import { NextRequest, NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';

/**
 * GET /api/exercises
 * List all exercises with optional filters
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const status = searchParams.get('status') as 'draft' | 'published' | null;
    const categoryId = searchParams.get('categoryId');
    const search = searchParams.get('search');
    const page = parseInt(searchParams.get('page') || '1');
    const limit = parseInt(searchParams.get('limit') || '20');

    const result = await exerciseService.list({
      status: status || undefined,
      categoryId: categoryId || undefined,
      search: search || undefined,
      page,
      limit,
    });

    return NextResponse.json({
      success: true,
      data: result.exercises,
      pagination: result.pagination,
    });
  } catch (error) {
    console.error('Error fetching exercises:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch exercises' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/exercises
 * Create a new exercise (supports both basic and complete creation)
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    // Validate required fields
    if (!body.name || !body.categoryId || !body.countingMethodId) {
      return NextResponse.json(
        { success: false, error: 'Name, categoryId, and countingMethodId are required' },
        { status: 400 }
      );
    }

    // Validate name has at least one language
    if (!body.name.en && !body.name.ar) {
      return NextResponse.json(
        { success: false, error: 'Name must have at least English or Arabic value' },
        { status: 400 }
      );
    }

    let exercise;

    // Check if this is a complete exercise creation (has difficultyLevels)
    if (body.difficultyLevels && body.difficultyLevels.length > 0) {
      exercise = await exerciseService.createComplete(body);
    } else {
      exercise = await exerciseService.create(body);
    }

    return NextResponse.json({
      success: true,
      data: exercise,
    }, { status: 201 });
  } catch (error) {
    console.error('Error creating exercise:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to create exercise' },
      { status: 500 }
    );
  }
}
