import { NextRequest, NextResponse } from 'next/server';
import { verifyMobileToken } from '@/modules/auth';
import { getExerciseHistory } from '@/modules/training-sessions';

/**
 * GET /api/mobile/sessions/[exerciseId]
 * 
 * Get training history for a specific exercise.
 * Returns sessions and aggregated statistics.
 * 
 * Query Parameters:
 * - limit: Number of sessions to return (default: 20)
 * - offset: Offset for pagination (default: 0)
 * - startDate: Filter sessions after this date (ISO string)
 * - endDate: Filter sessions before this date (ISO string)
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ exerciseId: string }> }
) {
  try {
    // Verify authentication
    const authResult = await verifyMobileToken(request);
    if (!authResult.success || !authResult.userId) {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }

    const { exerciseId } = await params;

    // Parse query parameters
    const searchParams = request.nextUrl.searchParams;
    const queryParams = {
      limit: parseInt(searchParams.get('limit') || '20'),
      offset: parseInt(searchParams.get('offset') || '0'),
      startDate: searchParams.get('startDate') || undefined,
      endDate: searchParams.get('endDate') || undefined,
    };

    // Get exercise history
    const history = await getExerciseHistory(
      authResult.userId,
      exerciseId,
      queryParams
    );

    return NextResponse.json({
      success: true,
      data: history,
    });
  } catch (error) {
    console.error('[Sessions] Error fetching exercise history:', error);
    return NextResponse.json(
      {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to fetch history',
      },
      { status: 500 }
    );
  }
}

/**
 * OPTIONS /api/mobile/sessions/[exerciseId]
 * CORS preflight handler
 */
export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    },
  });
}
