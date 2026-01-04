import { NextRequest, NextResponse } from 'next/server';
import { cameraPositionService } from '@/modules/camera-positions/camera-positions.service';
import { CreateCameraPositionInput } from '@/modules/camera-positions/camera-positions.types';

/**
 * GET /api/camera-positions
 * List all camera positions
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const includeInactive = searchParams.get('includeInactive') === 'true';
    
    const cameraPositions = await cameraPositionService.list(includeInactive);
    
    // Transform response to include joints array
    const transformed = cameraPositions.map((cp: any) => ({
      id: cp.id,
      code: cp.code,
      name: cp.name,
      description: cp.description,
      imageUrl: cp.imageUrl,
      isActive: cp.isActive,
      sortOrder: cp.sortOrder,
      createdAt: cp.createdAt,
      updatedAt: cp.updatedAt,
      joints: cp.joints?.map((j: any) => ({
        id: j.joint.id,
        code: j.joint.code,
        name: j.joint.name,
      })) || [],
    }));
    
    return NextResponse.json({
      success: true,
      data: transformed,
    });
  } catch (error) {
    console.error('Error fetching camera positions:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch camera positions' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/camera-positions
 * Create a new camera position
 */
export async function POST(request: NextRequest) {
  try {
    const body: CreateCameraPositionInput = await request.json();
    
    // Validate required fields
    if (!body.code || !body.name) {
      return NextResponse.json(
        { success: false, error: 'Code and name are required' },
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
    
    // Check if code already exists
    const existing = await cameraPositionService.getByCode(body.code);
    if (existing) {
      return NextResponse.json(
        { success: false, error: 'Camera position with this code already exists' },
        { status: 409 }
      );
    }
    
    const cameraPosition = await cameraPositionService.create(body);
    
    // Transform response
    const transformed = {
      id: cameraPosition.id,
      code: cameraPosition.code,
      name: cameraPosition.name,
      description: cameraPosition.description,
      imageUrl: cameraPosition.imageUrl,
      isActive: cameraPosition.isActive,
      sortOrder: cameraPosition.sortOrder,
      createdAt: cameraPosition.createdAt,
      updatedAt: cameraPosition.updatedAt,
      joints: (cameraPosition as any).joints?.map((j: any) => ({
        id: j.joint.id,
        code: j.joint.code,
        name: j.joint.name,
      })) || [],
    };
    
    return NextResponse.json({
      success: true,
      data: transformed,
    }, { status: 201 });
  } catch (error) {
    console.error('Error creating camera position:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to create camera position' },
      { status: 500 }
    );
  }
}


