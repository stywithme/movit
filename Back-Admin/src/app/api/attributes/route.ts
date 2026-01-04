import { NextResponse } from 'next/server';
import { getPrisma } from '@/lib/prisma/client';

/**
 * GET /api/attributes
 * List all attribute types with their values
 */
export async function GET() {
  try {
    const prisma = await getPrisma();
    const attributes = await prisma.attribute.findMany({
      orderBy: { sortOrder: 'asc' },
      include: {
        values: {
          where: { isActive: true },
          orderBy: { sortOrder: 'asc' },
        },
      },
    });

    return NextResponse.json({
      success: true,
      data: attributes,
    });
  } catch (error) {
    console.error('Error fetching attributes:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch attributes' },
      { status: 500 }
    );
  }
}

