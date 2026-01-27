import { NextRequest, NextResponse } from 'next/server';
import { adminsService } from '@/modules/admins/admins.service';

/**
 * GET /api/admins
 * List admins with optional filters
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const status = searchParams.get('status');
    const search = searchParams.get('search');
    const page = parseInt(searchParams.get('page') || '1');
    const limit = parseInt(searchParams.get('limit') || '20');

    const isActive = status === 'active' ? true : status === 'inactive' ? false : undefined;

    const result = await adminsService.list({
      isActive,
      search: search || undefined,
      page,
      limit,
    });

    return NextResponse.json({
      success: true,
      data: result.admins,
      pagination: result.pagination,
    });
  } catch (error) {
    console.error('Error fetching admins:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch admins' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/admins
 * Create a new admin
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

    const admin = await adminsService.create({
      name: body.name,
      email: body.email,
      password: body.password,
      role: body.role || 'admin',
    });

    return NextResponse.json({
      success: true,
      data: admin,
    }, { status: 201 });
  } catch (error: any) {
    console.error('Error creating admin:', error);
    if (error?.message === 'Email already registered') {
      return NextResponse.json(
        { success: false, error: 'Email already registered' },
        { status: 409 }
      );
    }
    return NextResponse.json(
      { success: false, error: 'Failed to create admin' },
      { status: 500 }
    );
  }
}
