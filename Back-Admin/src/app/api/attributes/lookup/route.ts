import { NextResponse } from 'next/server';
import { getPrisma } from '@/lib/prisma/client';

/**
 * GET /api/attributes/lookup
 * Get all lookup data needed for the exercise wizard
 */
export async function GET() {
  try {
    const prisma = await getPrisma();
    
    // Fetch all attributes with their values
    const attributes = await prisma.attribute.findMany({
      include: {
        values: {
          where: { isActive: true },
          orderBy: { sortOrder: 'asc' },
        },
      },
    });
    
    // Fetch camera positions
    const cameraPositions = await prisma.cameraPosition.findMany({
      where: { isActive: true },
      orderBy: { sortOrder: 'asc' },
    });
    
    // Group attributes by type
    const getValuesByCode = (code: string) => {
      const attr = attributes.find(a => a.code === code);
      return (attr?.values || []).map(v => ({
        id: v.id,
        code: v.code,
        name: v.name as { ar: string; en: string },
        description: v.description as { ar: string; en: string } | undefined,
        icon: v.icon,
        color: v.color,
      }));
    };
    
    const data = {
      categories: getValuesByCode('category'),
      countingMethods: getValuesByCode('counting_method'),
      joints: getValuesByCode('joint'),
      muscles: getValuesByCode('muscle'),
      equipment: getValuesByCode('equipment'),
      tags: getValuesByCode('tag'),
      cameraPositions: cameraPositions.map(cp => ({
        id: cp.id,
        code: cp.code,
        schemaCode: cp.schemaCode,
        name: cp.name as { ar: string; en: string },
        description: cp.description as { ar: string; en: string } | undefined,
        imageUrl: cp.imageUrl,
      })),
    };
    
    return NextResponse.json({
      success: true,
      data,
    });
  } catch (error) {
    console.error('Error fetching lookup data:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch lookup data' },
      { status: 500 }
    );
  }
}
