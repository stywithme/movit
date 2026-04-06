import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { messagesService } from './messages.service';
import type { BulkGenerateAudioInput, CreateMessageInput, UpdateMessageInput } from './messages.types';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';

const AUDIO_MISSING_QUERY = ['any', 'ar', 'en', 'complete'] as const;

@UseGuards(CaslGuard)
@Controller('messages')
export class MessagesController {
  @Get()
  @CheckPermission('read', 'FeedbackMessage')
  async list(
    @Query('includeInactive') includeInactive?: string,
    @Query('category') category?: string,
    @Query('audioMissing') audioMissing?: string,
    @Query('page') pageStr?: string,
    @Query('limit') limitStr?: string,
    @Query('status') statusQuery?: string,
    @Query('search') search?: string
  ) {
    try {
      const audioFilter =
        audioMissing && (AUDIO_MISSING_QUERY as readonly string[]).includes(audioMissing)
          ? (audioMissing as (typeof AUDIO_MISSING_QUERY)[number])
          : undefined;

      const usePagination = pageStr !== undefined && pageStr !== '';
      if (usePagination) {
        const page = Math.max(1, parseInt(pageStr, 10) || 1);
        const limit = Math.min(100, Math.max(1, parseInt(limitStr || '20', 10) || 20));
        const status =
          statusQuery === 'active' || statusQuery === 'inactive' ? statusQuery : undefined;

        const result = await messagesService.listPaged({
          page,
          limit,
          includeInactive: includeInactive === 'true',
          category: category || undefined,
          audioMissing: audioFilter,
          status,
          search: search?.trim() || undefined,
        });
        return {
          success: true,
          data: result.items,
          pagination: {
            page: result.page,
            limit: result.limit,
            total: result.total,
            totalPages: result.totalPages,
          },
        };
      }

      const messages = await messagesService.list({
        includeInactive: includeInactive === 'true',
        category: category || undefined,
        audioMissing: audioFilter,
      });
      return { success: true, data: messages };
    } catch (error) {
      console.error('Error fetching messages:', error);
      return { success: false, error: 'Failed to fetch messages' };
    }
  }

  @Post()
  @CheckPermission('create', 'FeedbackMessage')
  async create(@Body() body: CreateMessageInput, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.code || !body?.category || !body?.content) {
        res.status(400);
        return { success: false, error: 'Code, category, and content are required' };
      }

      if (!body.content.en && !body.content.ar) {
        res.status(400);
        return { success: false, error: 'Content must have at least English or Arabic value' };
      }

      const existing = await messagesService.getByCode(body.code);
      if (existing) {
        res.status(409);
        return { success: false, error: 'Message code already exists' };
      }

      const message = await messagesService.create(body);
      res.status(201);
      return { success: true, data: message };
    } catch (error) {
      console.error('Error creating message:', error);
      res.status(500);
      return { success: false, error: 'Failed to create message' };
    }
  }

  @Post('bulk-audio')
  @CheckPermission('update', 'FeedbackMessage')
  async bulkGenerateAudio(@Body() body: BulkGenerateAudioInput, @Res({ passthrough: true }) res: Response) {
    try {
      const result = await messagesService.bulkGenerateMissingAudio(body ?? {});
      return { success: true, data: result };
    } catch (error) {
      console.error('Error bulk-generating message audio:', error);
      res.status(500);
      return { success: false, error: 'Failed to generate audio for messages' };
    }
  }

  @Get(':id')
  @CheckPermission('read', 'FeedbackMessage')
  async getById(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      const message = await messagesService.getById(id);
      if (!message) {
        res.status(404);
        return { success: false, error: 'Message not found' };
      }
      return { success: true, data: message };
    } catch (error) {
      console.error('Error fetching message:', error);
      res.status(500);
      return { success: false, error: 'Failed to fetch message' };
    }
  }

  @Put(':id')
  @CheckPermission('update', 'FeedbackMessage')
  async update(
    @Param('id') id: string,
    @Body() body: UpdateMessageInput,
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      if (body?.content && !body.content.en && !body.content.ar) {
        res.status(400);
        return { success: false, error: 'Content must have at least English or Arabic value' };
      }

      if (body?.code) {
        const existing = await messagesService.getByCode(body.code);
        if (existing && existing.id !== id) {
          res.status(409);
          return { success: false, error: 'Message code already exists' };
        }
      }

      const message = await messagesService.update(id, body);
      return { success: true, data: message };
    } catch (error) {
      console.error('Error updating message:', error);
      res.status(500);
      return { success: false, error: 'Failed to update message' };
    }
  }

  @Delete(':id')
  @CheckPermission('delete', 'FeedbackMessage')
  async remove(@Param('id') id: string, @Res({ passthrough: true }) res: Response) {
    try {
      await messagesService.delete(id);
      return { success: true };
    } catch (error) {
      console.error('Error deleting message:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete message' };
    }
  }
}
