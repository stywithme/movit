/**
 * AI Text-to-Speech API Route
 * ============================
 * 
 * POST /api/ai/tts
 * Generates speech from text using Gemini TTS.
 * 
 * DELETE /api/ai/tts
 * Deletes an existing audio file.
 */

import { NextRequest, NextResponse } from 'next/server';
import { generateSpeech, deleteAudioFile } from '@/lib/gemini';

interface TTSRequestBody {
  text: string;
  language: 'ar' | 'en';
  voiceName?: string;
}

interface DeleteRequestBody {
  audioPath: string;
}

/**
 * POST - Generate speech from text
 */
export async function POST(request: NextRequest) {
  try {
    const body: TTSRequestBody = await request.json();

    // Validate required fields
    if (!body.text || !body.language) {
      return NextResponse.json(
        { success: false, error: 'Missing required fields: text, language' },
        { status: 400 }
      );
    }

    // Validate language value
    if (!['ar', 'en'].includes(body.language)) {
      return NextResponse.json(
        { success: false, error: 'Invalid language. Supported: ar, en' },
        { status: 400 }
      );
    }

    const result = await generateSpeech({
      text: body.text,
      language: body.language,
      voiceName: body.voiceName,
    });

    if (!result.success) {
      return NextResponse.json(
        { success: false, error: result.error },
        { status: 500 }
      );
    }

    return NextResponse.json({
      success: true,
      audioUrl: result.audioPath,
    });
  } catch (error) {
    console.error('TTS API error:', error);
    return NextResponse.json(
      { success: false, error: 'TTS generation failed' },
      { status: 500 }
    );
  }
}

/**
 * DELETE - Delete an audio file
 */
export async function DELETE(request: NextRequest) {
  try {
    const body: DeleteRequestBody = await request.json();

    if (!body.audioPath) {
      return NextResponse.json(
        { success: false, error: 'Missing audioPath' },
        { status: 400 }
      );
    }

    const deleted = await deleteAudioFile(body.audioPath);

    return NextResponse.json({
      success: deleted,
      message: deleted ? 'Audio file deleted' : 'Failed to delete audio file',
    });
  } catch (error) {
    console.error('Delete audio API error:', error);
    return NextResponse.json(
      { success: false, error: 'Failed to delete audio' },
      { status: 500 }
    );
  }
}
