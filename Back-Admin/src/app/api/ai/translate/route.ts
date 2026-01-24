/**
 * AI Translation API Route
 * =========================
 * 
 * POST /api/ai/translate
 * Translates text between Arabic and English using Gemini.
 */

import { NextRequest, NextResponse } from 'next/server';
import { translateText } from '@/lib/gemini';

interface TranslateRequestBody {
  text: string;
  from: 'ar' | 'en';
  to: 'ar' | 'en';
  context?: string;
}

export async function POST(request: NextRequest) {
  try {
    const body: TranslateRequestBody = await request.json();

    // Validate required fields
    if (!body.text || !body.from || !body.to) {
      return NextResponse.json(
        { success: false, error: 'Missing required fields: text, from, to' },
        { status: 400 }
      );
    }

    // Validate language values
    if (!['ar', 'en'].includes(body.from) || !['ar', 'en'].includes(body.to)) {
      return NextResponse.json(
        { success: false, error: 'Invalid language. Supported: ar, en' },
        { status: 400 }
      );
    }

    const result = await translateText({
      text: body.text,
      from: body.from,
      to: body.to,
      context: body.context,
    });

    if (!result.success) {
      return NextResponse.json(
        { success: false, error: result.error },
        { status: 500 }
      );
    }

    return NextResponse.json({
      success: true,
      translatedText: result.translatedText,
    });
  } catch (error) {
    console.error('Translation API error:', error);
    return NextResponse.json(
      { success: false, error: 'Translation failed' },
      { status: 500 }
    );
  }
}
