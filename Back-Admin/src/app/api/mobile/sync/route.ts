import { NextRequest, NextResponse } from 'next/server';
import { mobileSyncService } from '@/modules/mobile-sync';
import type { SyncRequestParams } from '@/modules/mobile-sync';

/**
 * GET /api/mobile/sync
 * 
 * Mobile sync endpoint for Android app.
 * 
 * Query Parameters:
 * - updatedAfter: ISO timestamp for incremental sync (optional)
 * - forceRefresh: Force full sync, ignoring updatedAfter (optional)
 * 
 * Examples:
 * - Full sync: GET /api/mobile/sync
 * - Incremental: GET /api/mobile/sync?updatedAfter=2026-01-24T10:00:00.000Z
 * - Force refresh: GET /api/mobile/sync?forceRefresh=true
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    
    // Parse request parameters
    const params: SyncRequestParams = {
      updatedAfter: searchParams.get('updatedAfter') || undefined,
      forceRefresh: searchParams.get('forceRefresh') === 'true',
    };
    
    // Get base URL for audio files
    const protocol = request.headers.get('x-forwarded-proto') || 'http';
    const host = request.headers.get('host') || 'localhost:3000';
    const baseUrl = `${protocol}://${host}`;
    
    // Perform sync
    const response = await mobileSyncService.sync(params, baseUrl);
    
    // Log sync details
    console.log(`[Mobile Sync] ${response.meta.isFullSync ? 'Full' : 'Incremental'} sync:`, {
      exercisesReturned: response.meta.exercisesInResponse,
      totalExercises: response.meta.totalExercises,
      audioFiles: response.data.audioManifest.files.length,
      deletedIds: response.data.deletedExerciseIds.length,
    });
    
    // Debug: Log reportMetrics for each exercise
    for (const exercise of response.data.exercises) {
      console.log(`[Mobile Sync] Exercise "${exercise.slug}" reportMetrics:`, exercise.reportMetrics);
    }
    
    return NextResponse.json(response);
  } catch (error) {
    console.error('[Mobile Sync] Error:', error);
    return NextResponse.json(
      {
        success: false,
        error: 'Failed to sync exercises',
        timestamp: new Date().toISOString(),
      },
      { status: 500 }
    );
  }
}

/**
 * OPTIONS /api/mobile/sync
 * 
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
