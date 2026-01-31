import { NextRequest, NextResponse } from 'next/server';
import {
  buildObjectName,
  deleteObjectFromGcs,
  getUploadLimits,
  isAllowedMime,
  parseObjectNameFromUrl,
  uploadBufferToGcs,
  type UploadCategory,
} from '@/lib/storage';
import { randomUUID } from 'crypto';

function getCategory(request: NextRequest): UploadCategory | null {
  const category = request.nextUrl.searchParams.get('type');
  if (!category) return null;
  if (
    category === 'camera-position-image' ||
    category === 'exercise-image' ||
    category === 'exercise-audio'
  ) {
    return category;
  }
  return null;
}

export async function POST(request: NextRequest) {
  try {
    const category = getCategory(request);
    if (!category) {
      return NextResponse.json(
        { success: false, error: 'Invalid upload type' },
        { status: 400 }
      );
    }

    const formData = await request.formData();
    const file = formData.get('file');
    if (!file || !(file instanceof File)) {
      return NextResponse.json(
        { success: false, error: 'File is required' },
        { status: 400 }
      );
    }

    if (!isAllowedMime(category, file.type)) {
      return NextResponse.json(
        { success: false, error: 'Unsupported file type' },
        { status: 400 }
      );
    }

    const maxBytes = getUploadLimits(category);
    if (file.size > maxBytes) {
      return NextResponse.json(
        { success: false, error: `File exceeds ${Math.round(maxBytes / 1024 / 1024)}MB limit` },
        { status: 400 }
      );
    }

    const id = randomUUID();
    const objectName = buildObjectName(category, file.name, id);
    const buffer = Buffer.from(await file.arrayBuffer());

    const result = await uploadBufferToGcs(objectName, buffer, file.type);

    return NextResponse.json({
      success: true,
      data: {
        url: result.url,
        objectName: result.objectName,
        size: file.size,
        contentType: file.type,
      },
    });
  } catch (error) {
    console.error('Upload error:', error);
    return NextResponse.json(
      { success: false, error: 'Upload failed' },
      { status: 500 }
    );
  }
}

export async function DELETE(request: NextRequest) {
  try {
    const body = await request.json();
    const objectName = body?.objectName || (body?.url ? parseObjectNameFromUrl(body.url) : null);

    if (!objectName) {
      return NextResponse.json(
        { success: false, error: 'objectName or url is required' },
        { status: 400 }
      );
    }

    await deleteObjectFromGcs(objectName);

    return NextResponse.json({
      success: true,
      message: 'File deleted',
    });
  } catch (error) {
    console.error('Delete error:', error);
    return NextResponse.json(
      { success: false, error: 'Delete failed' },
      { status: 500 }
    );
  }
}
