import { NextRequest, NextResponse } from 'next/server';
import { verifyMobileToken } from '@/modules/auth';
import {
  saveSession,
  getAllHistory,
} from '@/modules/training-sessions';
import type { SessionUploadPayload } from '@/modules/training-sessions';

/**
 * POST /api/mobile/sessions
 * 
 * Upload a training session from mobile app.
 * Saves session metrics and optionally legacy report data.
 * 
 * Body: SessionUploadPayload
 */
export async function POST(request: NextRequest) {
  try {
    // Verify authentication
    const authResult = await verifyMobileToken(request);
    
    // Debug log for auth issues
    if (!authResult.success) {
      console.log('[Sessions] Auth failed:', authResult.error);
      const authHeader = request.headers.get('Authorization');
      console.log('[Sessions] Auth header present:', !!authHeader, authHeader?.substring(0, 20) + '...');
    }
    
    if (!authResult.success || !authResult.userId) {
      return NextResponse.json(
        { success: false, error: authResult.error || 'Unauthorized' },
        { status: 401 }
      );
    }

    // Parse request body
    const payload: SessionUploadPayload = await request.json();

    // Validate required fields
    if (!payload.id || !payload.exerciseId || !payload.sessionMetrics) {
      return NextResponse.json(
        { success: false, error: 'Missing required fields: id, exerciseId, sessionMetrics' },
        { status: 400 }
      );
    }

    // Save session
    const session = await saveSession(authResult.userId, payload);

    console.log(`[Sessions] Saved session ${session.id} for user ${authResult.userId}:`, {
      exerciseId: session.exerciseId,
      totalReps: session.totalReps,
      avgScore: session.sessionMetrics?.avgFormScore ? session.sessionMetrics.avgFormScore / 10 : null,
      weightKg: session.weightKg,
    });

    return NextResponse.json({
      success: true,
      data: session,
    });
  } catch (error) {
    console.error('[Sessions] Error saving session:', error);
    return NextResponse.json(
      {
        success: false,
        error: error instanceof Error ? error.message : 'Failed to save session',
      },
      { status: 500 }
    );
  }
}

/**
 * GET /api/mobile/sessions
 * 
 * Get all training sessions for the authenticated user.
 * 
 * Query Parameters:
 * - limit: Number of sessions to return (default: 50)
 * - offset: Offset for pagination (default: 0)
 * - startDate: Filter sessions after this date (ISO string)
 * - endDate: Filter sessions before this date (ISO string)
 */
export async function GET(request: NextRequest) {
  try {
    // Verify authentication
    const authResult = await verifyMobileToken(request);
    if (!authResult.success || !authResult.userId) {
      return NextResponse.json(
        { success: false, error: 'Unauthorized' },
        { status: 401 }
      );
    }

    // Parse query parameters
    const searchParams = request.nextUrl.searchParams;
    const params = {
      limit: parseInt(searchParams.get('limit') || '50'),
      offset: parseInt(searchParams.get('offset') || '0'),
      startDate: searchParams.get('startDate') || undefined,
      endDate: searchParams.get('endDate') || undefined,
    };

    // Get sessions
    const sessions = await getAllHistory(authResult.userId, params);

    return NextResponse.json({
      success: true,
      data: sessions,
      meta: {
        count: sessions.length,
        offset: params.offset,
        limit: params.limit,
      },
    });
  } catch (error) {
    console.error('[Sessions] Error fetching sessions:', error);
    return NextResponse.json(
      {
        success: false,
        error: 'Failed to fetch sessions',
      },
      { status: 500 }
    );
  }
}

/**
 * OPTIONS /api/mobile/sessions
 * CORS preflight handler
 */
export async function OPTIONS() {
  return new NextResponse(null, {
    status: 204,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    },
  });
}
