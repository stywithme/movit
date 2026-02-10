/**
 * Gemini Translation Helper
 * ==========================
 * 
 * Translates text between Arabic and English using Gemini API.
 */

import { geminiClient, geminiConfig, type SupportedLanguage } from './client';

interface TranslateOptions {
  text: string;
  from: SupportedLanguage;
  to: SupportedLanguage;
  context?: string; // Optional context for better translation
}

interface TranslateResult {
  success: boolean;
  translatedText?: string;
  error?: string;
}

/**
 * Translate text between Arabic and English
 */
export async function translateText(options: TranslateOptions): Promise<TranslateResult> {
  const { text, from, to, context } = options;

  if (!text.trim()) {
    return { success: false, error: 'Text is empty' };
  }

  if (from === to) {
    return { success: true, translatedText: text };
  }

  const fromLang = from === 'ar' ? 'Arabic' : 'English';
  const toLang = to === 'ar' ? 'Arabic' : 'English';

  // Build the prompt
  let prompt = `Translate the following text from ${fromLang} to ${toLang}. 
Return ONLY the translated text, nothing else.
Do not add quotes or any formatting.
Keep the tone and style appropriate for a fitness/exercise coaching context.`;

  if (context) {
    prompt += `\nContext: ${context}`;
  }

  prompt += `\n\nText to translate:\n${text}`;

  try {
    const response = await geminiClient.models.generateContent({
      model: geminiConfig.textModel,
      contents: [{ parts: [{ text: prompt }] }],
    });

    const translatedText = response.candidates?.[0]?.content?.parts?.[0]?.text?.trim();

    if (!translatedText) {
      return { success: false, error: 'No translation received' };
    }

    return { success: true, translatedText };
  } catch (error) {
    console.error('Translation error:', error);
    return { 
      success: false, 
      error: error instanceof Error ? error.message : 'Translation failed' 
    };
  }
}
