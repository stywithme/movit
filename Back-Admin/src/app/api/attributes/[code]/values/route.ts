import { NextRequest, NextResponse } from 'next/server';
import { getPrisma } from '@/lib/prisma/client';

interface RouteParams {
  params: Promise<{ code: string }>;
}

/**
 * GET /api/attributes/:code/values
 * Get all values for a specific attribute type
 */
export async function GET(request: NextRequest, { params }: RouteParams) {
  try {
    const { code } = await params;
    const prisma = await getPrisma();

    const attribute = await prisma.attribute.findUnique({
      where: { code },
      include: {
        values: {
          where: { isActive: true },
          orderBy: { sortOrder: 'asc' },
        },
      },
    });

    if (!attribute) {
      return NextResponse.json(
        { success: false, error: 'Attribute not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      data: {
        attribute: {
          id: attribute.id,
          code: attribute.code,
          name: attribute.name,
        },
        values: attribute.values,
      },
    });
  } catch (error) {
    console.error('Error fetching attribute values:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch attribute values' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/attributes/:code/values
 * Create a new value for a specific attribute type
 */
export async function POST(request: NextRequest, { params }: RouteParams) {
  try {
    const { code } = await params;
    const body = await request.json();
    const prisma = await getPrisma();

    const attribute = await prisma.attribute.findUnique({
      where: { code },
    });

    if (!attribute) {
      return NextResponse.json(
        { success: false, error: 'Attribute not found' },
        { status: 404 }
      );
    }

    // Validate required fields
    if (!body.code || !body.name) {
      return NextResponse.json(
        { success: false, error: 'Code and name are required' },
        { status: 400 }
      );
    }

    // Check if code already exists
    const existingValue = await prisma.attributeValue.findUnique({
      where: { code: body.code },
    });

    if (existingValue) {
      return NextResponse.json(
        { success: false, error: 'Value code already exists' },
        { status: 409 }
      );
    }

    // Get max sort order
    const maxSortOrder = await prisma.attributeValue.aggregate({
      where: { attributeId: attribute.id },
      _max: { sortOrder: true },
    });

    const newValue = await prisma.attributeValue.create({
      data: {
        attributeId: attribute.id,
        code: body.code,
        name: body.name,
        description: body.description || null,
        icon: body.icon || null,
        color: body.color || null,
        sortOrder: (maxSortOrder._max.sortOrder || 0) + 1,
        isActive: body.isActive ?? true,
      },
    });

    return NextResponse.json({
      success: true,
      data: newValue,
    }, { status: 201 });
  } catch (error) {
    console.error('Error creating attribute value:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to create attribute value' },
      { status: 500 }
    );
  }
}

