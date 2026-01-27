import { NextRequest, NextResponse } from 'next/server';
import { adminsService } from '@/modules/admins/admins.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * PUT /api/admins/:id/password
 * Update admin password
 */
export async function PUT(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const body = await request.json();

    if (!body.password || String(body.password).length < 6) {
      return NextResponse.json(
        { success: false, error: 'Password must be at least 6 characters' },
        { status: 400 }
      );
    }

    const admin = await adminsService.updatePassword(id, body.password);

    return NextResponse.json({
      success: true,
      data: admin,
    });
  } catch (error) {
    console.error('Error updating admin password:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update admin password' },
      { status: 500 }
    );
  }
}
