import { Body, Controller, Delete, Get, Param, Post, Put, Query, Res } from '@nestjs/common';
import type { Response } from 'express';
import { messagesService } from './messages.service';
import type { CreateMessageInput, UpdateMessageInput } from './messages.types';

@Controller('messages')
export class MessagesController {
  @Get()
  async list(@Query('includeInactive') includeInactive?: string, @Query('category') category?: string) {
    try {
      const messages = await messagesService.list({
        includeInactive: includeInactive === 'true',
        category: category || undefined,
      });
      return { success: true, data: messages };
    } catch (error) {
      console.error('Error fetching messages:', error);
      return { success: false, error: 'Failed to fetch messages' };
    }
  }

  @Post()
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

  @Get(':id')
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
