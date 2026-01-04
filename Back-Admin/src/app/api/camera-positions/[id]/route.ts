import { NextRequest, NextResponse } from 'next/server';
import { cameraPositionService } from '@/modules/camera-positions/camera-positions.service';
import { UpdateCameraPositionInput } from '@/modules/camera-positions/camera-positions.types';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/camera-positions/:id
 * Get a single camera position
 */
export async function GET(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const cameraPosition = await cameraPositionService.getById(id);
    
    if (!cameraPosition) {
      return NextResponse.json(
        { success: false, error: 'Camera position not found' },
        { status: 404 }
      );
    }
    
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
      joints: cameraPosition.joints.map((j) => ({
        id: j.joint.id,
        code: j.joint.code,
        name: j.joint.name,
      })),
    };
    
    return NextResponse.json({
      success: true,
      data: transformed,
    });
  } catch (error) {
    console.error('Error fetching camera position:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch camera position' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/camera-positions/:id
 * Update a camera position
 */
export async function PUT(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const body: UpdateCameraPositionInput = await request.json();
    
    // Check if exists
    const existing = await cameraPositionService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Camera position not found' },
        { status: 404 }
      );
    }
    
    const cameraPosition = await cameraPositionService.update(id, body);
    
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
      joints: cameraPosition.joints.map((j) => ({
        id: j.joint.id,
        code: j.joint.code,
        name: j.joint.name,
      })),
    };
    
    return NextResponse.json({
      success: true,
      data: transformed,
    });
  } catch (error) {
    console.error('Error updating camera position:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update camera position' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/camera-positions/:id
 * Delete a camera position (soft delete)
 */
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    
    // Check if exists
    const existing = await cameraPositionService.getById(id);
    if (!existing) {
      return NextResponse.json(
        { success: false, error: 'Camera position not found' },
        { status: 404 }
      );
    }
    
    await cameraPositionService.delete(id);
    
    return NextResponse.json({
      success: true,
      message: 'Camera position deleted successfully',
    });
  } catch (error) {
    console.error('Error deleting camera position:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete camera position' },
      { status: 500 }
    );
  }
}


