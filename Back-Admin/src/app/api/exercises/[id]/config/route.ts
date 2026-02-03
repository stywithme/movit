/**
 * Exercise Configuration API
 * ==========================
 * 
 * Endpoints for managing exercise weight and metrics configuration.
 * 
 * PATCH /api/exercises/[id]/config
 * - Update weight config
 * - Update report metrics config
 * 
 * GET /api/exercises/[id]/config
 * - Get exercise configuration for mobile
 */

import { NextRequest, NextResponse } from 'next/server';
import { exerciseService } from '@/modules/exercises/exercises.service';
import { getAutoIncludedMetrics, getDefaultPrimaryMetrics } from '@/modules/exercises/exercises.types';

interface RouteContext {
  params: Promise<{ id: string }>;
}

/**
 * GET - Get exercise configuration
 */
export async function GET(
  request: NextRequest,
  context: RouteContext
) {
  try {
    const { id } = await context.params;
    const config = await exerciseService.getExerciseConfig(id);
    
    if (!config) {
      return NextResponse.json(
        { error: 'Exercise not found' },
        { status: 404 }
      );
    }
    
    return NextResponse.json(config);
  } catch (error) {
    console.error('Error fetching exercise config:', error);
    return NextResponse.json(
      { error: 'Failed to fetch exercise config' },
      { status: 500 }
    );
  }
}

/**
 * PATCH - Update exercise configuration
 */
export async function PATCH(
  request: NextRequest,
  context: RouteContext
) {
  try {
    const { id } = await context.params;
    const body = await request.json();
    
    const results: Record<string, unknown> = {};
    
    // Update weight config if provided
    if (body.weight !== undefined) {
      const weightResult = await exerciseService.updateWeightConfig(
        id,
        {
          supportsWeight: body.weight.supported ?? false,
          minWeight: body.weight.min,
          maxWeight: body.weight.max,
          defaultWeight: body.weight.default,
        }
      );
      results.weight = {
        supported: weightResult.supportsWeight,
        min: weightResult.minWeight,
        max: weightResult.maxWeight,
        default: weightResult.defaultWeight,
      };
    }
    
    // Update metrics config if provided
    if (body.metrics !== undefined) {
      const metricsResult = await exerciseService.updateReportMetrics(
        id,
        {
          primary: body.metrics.primary ?? [],
          optional: body.metrics.optional ?? [],
          excluded: body.metrics.excluded ?? [],
        }
      );
      results.metrics = metricsResult.reportMetrics;
    }
    
    return NextResponse.json({
      success: true,
      updated: results,
    });
  } catch (error) {
    console.error('Error updating exercise config:', error);
    return NextResponse.json(
      { error: 'Failed to update exercise config' },
      { status: 500 }
    );
  }
}

/**
 * POST - Generate default metrics config based on exercise type
 */
export async function POST(
  request: NextRequest,
  context: RouteContext
) {
  try {
    const { id } = await context.params;
    const config = await exerciseService.getExerciseConfig(id);
    
    if (!config) {
      return NextResponse.json(
        { error: 'Exercise not found' },
        { status: 404 }
      );
    }
    
    // Get auto-included metrics
    const countingMethod = config.countingMethod as 'up_down' | 'push_pull' | 'hold';
    const autoMetrics = getAutoIncludedMetrics({
      countingMethod,
      isBilateral: config.metrics.isBilateral,
      supportsWeight: config.weight.supported,
    });
    
    // Get default primary metrics
    const primaryMetrics = getDefaultPrimaryMetrics({
      countingMethod,
      isBilateral: config.metrics.isBilateral,
      supportsWeight: config.weight.supported,
    });
    
    return NextResponse.json({
      exerciseId: id,
      exerciseType: {
        countingMethod,
        isBilateral: config.metrics.isBilateral,
        supportsWeight: config.weight.supported,
      },
      recommended: {
        primary: primaryMetrics,
        autoIncluded: autoMetrics,
      },
    });
  } catch (error) {
    console.error('Error generating metrics config:', error);
    return NextResponse.json(
      { error: 'Failed to generate metrics config' },
      { status: 500 }
    );
  }
}
