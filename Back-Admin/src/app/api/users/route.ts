import { NextRequest, NextResponse } from 'next/server';
import { usersService } from '@/modules/users/users.service';

/**
 * GET /api/users
 * List users with optional filters
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const status = searchParams.get('status');
    const search = searchParams.get('search');
    const page = parseInt(searchParams.get('page') || '1');
    const limit = parseInt(searchParams.get('limit') || '20');

    const isActive = status === 'active' ? true : status === 'inactive' ? false : undefined;

    const result = await usersService.list({
      isActive,
      search: search || undefined,
      page,
      limit,
    });

    return NextResponse.json({
      success: true,
      data: result.users,
      pagination: result.pagination,
    });
  } catch (error) {
    console.error('Error fetching users:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch users' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/users
 * Create a new user
 */
export async function POST(request: NextRequest) {
  try {
    const body = await request.json();

    if (!body.name || !body.email || !body.password) {
      return NextResponse.json(
        { success: false, error: 'Name, email, and password are required' },
        { status: 400 }
      );
    }

    if (String(body.password).length < 6) {
      return NextResponse.json(
        { success: false, error: 'Password must be at least 6 characters' },
        { status: 400 }
      );
    }

    const subscriptionExpiry = body.subscriptionExpiry
      ? new Date(`${body.subscriptionExpiry}T00:00:00.000Z`)
      : undefined;

    const user = await usersService.create({
      name: body.name,
      email: body.email,
      password: body.password,
      avatarUrl: body.avatarUrl || undefined,
      isPro: body.isPro ?? false,
      subscriptionExpiry,
    });

    return NextResponse.json({
      success: true,
      data: user,
    }, { status: 201 });
  } catch (error: any) {
    console.error('Error creating user:', error);
    if (error?.message === 'Email already registered') {
      return NextResponse.json(
        { success: false, error: 'Email already registered' },
        { status: 409 }
      );
    }
    return NextResponse.json(
      { success: false, error: 'Failed to create user' },
      { status: 500 }
    );
  }
}
