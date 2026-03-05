import { Body, Controller, Delete, Post, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import { translateText } from '@/lib/gemini';
import { deleteAudioFile, generateSpeech } from '@/lib/gemini';

interface TranslateRequestBody {
  text: string;
  from: 'ar' | 'en';
  to: 'ar' | 'en';
  context?: string;
}

@UseGuards(CaslGuard)
@Controller('ai')
export class AiController {
  @Post('translate')
  @CheckPermission('update', 'Exercise')
  async translate(@Body() body: TranslateRequestBody, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.text || !body?.from || !body?.to) {
        res.status(400);
        return { success: false, error: 'Missing required fields: text, from, to' };
      }

      if (!['ar', 'en'].includes(body.from) || !['ar', 'en'].includes(body.to)) {
        res.status(400);
        return { success: false, error: 'Invalid language. Supported: ar, en' };
      }

      const result = await translateText({
        text: body.text,
        from: body.from,
        to: body.to,
        context: body.context,
      });

      if (!result.success) {
        res.status(500);
        return { success: false, error: result.error };
      }

      return { success: true, translatedText: result.translatedText };
    } catch (error) {
      console.error('Translation API error:', error);
      res.status(500);
      return { success: false, error: 'Translation failed' };
    }
  }

  @Post('tts')
  @CheckPermission('update', 'Exercise')
  async tts(
    @Body() body: { text?: string; language?: 'ar' | 'en'; voiceName?: string },
    @Res({ passthrough: true }) res: Response
  ) {
    try {
      if (!body?.text || !body?.language) {
        res.status(400);
        return { success: false, error: 'Missing required fields: text, language' };
      }

      if (!['ar', 'en'].includes(body.language)) {
        res.status(400);
        return { success: false, error: 'Invalid language. Supported: ar, en' };
      }

      const result = await generateSpeech({
        text: body.text,
        language: body.language,
        voiceName: body.voiceName,
      });
      if (!result.success) {
        res.status(500);
        return { success: false, error: result.error };
      }

      return { success: true, audioUrl: result.audioPath };
    } catch (error) {
      console.error('TTS API error:', error);
      res.status(500);
      return { success: false, error: 'TTS failed' };
    }
  }

  @Delete('tts')
  @CheckPermission('update', 'Exercise')
  async deleteTts(@Body() body: { audioPath?: string }, @Res({ passthrough: true }) res: Response) {
    try {
      if (!body?.audioPath) {
        res.status(400);
        return { success: false, error: 'Missing audioPath' };
      }

      const deleted = await deleteAudioFile(body.audioPath);
      return {
        success: deleted,
        message: deleted ? 'Audio file deleted' : 'Failed to delete audio file',
      };
    } catch (error) {
      console.error('Delete audio API error:', error);
      res.status(500);
      return { success: false, error: 'Failed to delete audio' };
    }
  }
}
