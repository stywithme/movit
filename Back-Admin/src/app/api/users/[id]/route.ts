import { NextRequest, NextResponse } from 'next/server';
import { usersService } from '@/modules/users/users.service';

interface RouteParams {
  params: Promise<{ id: string }>;
}

/**
 * PUT /api/users/:id
 * Update user status or details
 */
export async function PUT(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    const body = await request.json();

    const subscriptionExpiry = body.subscriptionExpiry === null
      ? null
      : body.subscriptionExpiry
        ? new Date(`${body.subscriptionExpiry}T00:00:00.000Z`)
        : undefined;

    const user = body.isActive !== undefined
      ? await usersService.setActive(id, Boolean(body.isActive))
      : await usersService.update(id, {
          name: body.name,
          email: body.email,
          avatarUrl: body.avatarUrl,
          isPro: body.isPro,
          subscriptionExpiry,
          isActive: body.isActive,
        });

    return NextResponse.json({
      success: true,
      data: user,
    });
  } catch (error) {
    console.error('Error updating user:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update user' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/users/:id
 * Soft delete a user
 */
export async function DELETE(request: NextRequest, { params }: RouteParams) {
  try {
    const { id } = await params;
    await usersService.delete(id);

    return NextResponse.json({
      success: true,
      message: 'User deleted successfully',
    });
  } catch (error) {
    console.error('Error deleting user:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete user' },
      { status: 500 }
    );
  }
}
