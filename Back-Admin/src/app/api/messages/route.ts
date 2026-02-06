import { NextRequest, NextResponse } from 'next/server';
import { messagesService } from '@/modules/messages/messages.service';
import type { CreateMessageInput } from '@/modules/messages/messages.types';

/**
 * GET /api/messages
 * List messages
 */
export async function GET(request: NextRequest) {
  try {
    const searchParams = request.nextUrl.searchParams;
    const includeInactive = searchParams.get('includeInactive') === 'true';
    const category = searchParams.get('category') || undefined;

    const messages = await messagesService.list({ includeInactive, category });

    return NextResponse.json({
      success: true,
      data: messages,
    });
  } catch (error) {
    console.error('Error fetching messages:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch messages' },
      { status: 500 }
    );
  }
}

/**
 * POST /api/messages
 * Create a new message
 */
export async function POST(request: NextRequest) {
  try {
    const body: CreateMessageInput = await request.json();

    if (!body.code || !body.category || !body.content) {
      return NextResponse.json(
        { success: false, error: 'Code, category, and content are required' },
        { status: 400 }
      );
    }

    if (!body.content.en && !body.content.ar) {
      return NextResponse.json(
        { success: false, error: 'Content must have at least English or Arabic value' },
        { status: 400 }
      );
    }

    const existing = await messagesService.getByCode(body.code);
    if (existing) {
      return NextResponse.json(
        { success: false, error: 'Message code already exists' },
        { status: 409 }
      );
    }

    const message = await messagesService.create(body);

    return NextResponse.json({
      success: true,
      data: message,
    }, { status: 201 });
  } catch (error) {
    console.error('Error creating message:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to create message' },
      { status: 500 }
    );
  }
}
