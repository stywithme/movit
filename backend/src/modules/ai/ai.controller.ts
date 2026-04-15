import { Body, Controller, Delete, Get, Post, Res, UseGuards } from '@nestjs/common';
import type { Response } from 'express';
import { CaslGuard } from '@/lib/casl/casl.guard';
import { CheckPermission } from '@/lib/casl/check-permission.decorator';
import {
  translateText,
  deleteAudioFile,
  generateSpeech,
  geminiConfig,
  TTS_VOICES,
  TTS_MODELS,
  TTS_LANGUAGE_CODES,
} from '@/lib/gemini';

interface TranslateRequestBody {
  text: string;
  from: 'ar' | 'en';
  to: 'ar' | 'en';
  context?: string;
}

interface TtsRequestBody {
  text?: string;
  language?: 'ar' | 'en';
  voiceName?: string;
  model?: string;
  languageCode?: string;
  sharedStylePrompt?: string;
  stylePrompt?: string;
  systemInstruction?: string;
  temperature?: number;
  seed?: number;
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

  @Get('tts/config')
  @CheckPermission('read', 'FeedbackMessage')
  async ttsConfig(@Res({ passthrough: true }) res: Response) {
    try {
      return {
        success: true,
        data: {
          voices: [...TTS_VOICES],
          models: [...TTS_MODELS],
          languageCodes: [...TTS_LANGUAGE_CODES],
          defaults: {
            ttsModel: geminiConfig.ttsModel,
            voiceAr: geminiConfig.voices.ar,
            voiceEn: geminiConfig.voices.en,
          },
        },
      };
    } catch (error) {
      console.error('TTS config error:', error);
      res.status(500);
      return { success: false, error: 'Failed to load TTS configuration' };
    }
  }

  @Post('tts')
  @CheckPermission('update', 'Exercise')
  async tts(@Body() body: TtsRequestBody, @Res({ passthrough: true }) res: Response) {
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
        model: body.model,
        languageCode: body.languageCode,
        sharedStylePrompt: body.sharedStylePrompt,
        stylePrompt: body.stylePrompt,
        systemInstruction: body.systemInstruction,
        temperature: body.temperature,
        seed: body.seed,
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
