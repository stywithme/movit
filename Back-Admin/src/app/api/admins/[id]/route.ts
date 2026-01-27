import { NextRequest, NextResponse } from 'next/server';
import { adminsService } from '@/modules/admins/admins.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * GET /api/admins/:id
 * Get a single admin
 */
export async function GET(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const admin = await adminsService.getById(id);

    if (!admin) {
      return NextResponse.json(
        { success: false, error: 'Admin not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      data: admin,
    });
  } catch (error) {
    console.error('Error fetching admin:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch admin' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/admins/:id
 * Update admin status or details
 */
export async function PUT(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const body = await request.json();

    const admin = body.isActive !== undefined
      ? await adminsService.setActive(id, Boolean(body.isActive))
      : await adminsService.update(id, {
          name: body.name,
          email: body.email,
          role: body.role,
          isActive: body.isActive,
        });

    return NextResponse.json({
      success: true,
      data: admin,
    });
  } catch (error) {
    console.error('Error updating admin:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update admin' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/admins/:id
 * Soft delete an admin
 */
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    await adminsService.delete(id);

    return NextResponse.json({
      success: true,
      message: 'Admin deleted successfully',
    });
  } catch (error) {
    console.error('Error deleting admin:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete admin' },
      { status: 500 }
    );
  }
}
