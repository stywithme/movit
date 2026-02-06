import { NextRequest, NextResponse } from 'next/server';
import { messagesService } from '@/modules/messages/messages.service';
import type { UpdateMessageInput } from '@/modules/messages/messages.types';

/**
 * GET /api/messages/[id]
 * Get a single message
 */
export async function GET(_: NextRequest, { params }: { params: { id: string } }) {
  try {
    const message = await messagesService.getById(params.id);

    if (!message) {
      return NextResponse.json(
        { success: false, error: 'Message not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({ success: true, data: message });
  } catch (error) {
    console.error('Error fetching message:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to fetch message' },
      { status: 500 }
    );
  }
}

/**
 * PUT /api/messages/[id]
 * Update a message
 */
export async function PUT(request: NextRequest, { params }: { params: { id: string } }) {
  try {
    const body: UpdateMessageInput = await request.json();

    if (body.content && !body.content.en && !body.content.ar) {
      return NextResponse.json(
        { success: false, error: 'Content must have at least English or Arabic value' },
        { status: 400 }
      );
    }

    if (body.code) {
      const existing = await messagesService.getByCode(body.code);
      if (existing && existing.id !== params.id) {
        return NextResponse.json(
          { success: false, error: 'Message code already exists' },
          { status: 409 }
        );
      }
    }

    const message = await messagesService.update(params.id, body);

    return NextResponse.json({ success: true, data: message });
  } catch (error) {
    console.error('Error updating message:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to update message' },
      { status: 500 }
    );
  }
}

/**
 * DELETE /api/messages/[id]
 * Soft delete a message (set inactive)
 */
export async function DELETE(_: NextRequest, { params }: { params: { id: string } }) {
  try {
    await messagesService.delete(params.id);
    return NextResponse.json({ success: true });
  } catch (error) {
    console.error('Error deleting message:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete message' },
      { status: 500 }
    );
  }
}
