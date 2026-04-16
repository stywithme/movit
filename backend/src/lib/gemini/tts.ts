/**
 * Gemini Text-to-Speech Helper
 * ==============================
 * 
 * Converts text to speech using Gemini TTS API.
 * Supports Arabic and English with configurable voices.
 */

import { geminiClient, geminiConfig, type SupportedLanguage } from './client';
import { DEFAULT_TTS_MODEL, normalizeTtsModelId } from './tts-constants';
import { uploadBufferToGcs, deleteObjectFromGcs, parseObjectNameFromUrl } from '@/lib/storage';

/**
 * Create WAV file from PCM data
 * Gemini TTS returns raw PCM data, we need to add WAV headers
 */
function createWavBuffer(pcmData: Buffer, sampleRate = 24000, channels = 1, bitsPerSample = 16): Buffer {
  const byteRate = sampleRate * channels * (bitsPerSample / 8);
  const blockAlign = channels * (bitsPerSample / 8);
  const dataSize = pcmData.length;
  const fileSize = 36 + dataSize;

  // Create WAV header (44 bytes)
  const header = Buffer.alloc(44);

  // RIFF chunk descriptor
  header.write('RIFF', 0);                      // ChunkID
  header.writeUInt32LE(fileSize, 4);            // ChunkSize
  header.write('WAVE', 8);                      // Format

  // fmt sub-chunk
  header.write('fmt ', 12);                     // Subchunk1ID
  header.writeUInt32LE(16, 16);                 // Subchunk1Size (16 for PCM)
  header.writeUInt16LE(1, 20);                  // AudioFormat (1 = PCM)
  header.writeUInt16LE(channels, 22);           // NumChannels
  header.writeUInt32LE(sampleRate, 24);         // SampleRate
  header.writeUInt32LE(byteRate, 28);           // ByteRate
  header.writeUInt16LE(blockAlign, 32);         // BlockAlign
  header.writeUInt16LE(bitsPerSample, 34);      // BitsPerSample

  // data sub-chunk
  header.write('data', 36);                     // Subchunk2ID
  header.writeUInt32LE(dataSize, 40);           // Subchunk2Size

  // Combine header and PCM data
  return Buffer.concat([header, pcmData]);
}

interface TTSOptions {
  text: string;
  language: SupportedLanguage;
  voiceName?: string;
  model?: string;
  languageCode?: string;
  sharedStylePrompt?: string;
  stylePrompt?: string;
  systemInstruction?: string;
  temperature?: number;
  seed?: number;
}

interface TTSResult {
  success: boolean;
  audioPath?: string; // Public URL for client access
  error?: string;
}

function isModelNotFoundError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const maybe = error as { status?: string; code?: number; message?: string };
  const message = (maybe.message || '').toLowerCase();
  return maybe.code === 404 || maybe.status === 'NOT_FOUND' || message.includes('not found for api version');
}

/**
 * Generate speech from text and save to file
 */
export async function generateSpeech(options: TTSOptions): Promise<TTSResult> {
  const {
    text,
    language,
    voiceName,
    model,
    languageCode,
    sharedStylePrompt,
    stylePrompt,
    systemInstruction,
    temperature,
    seed,
  } = options;

  if (!text.trim()) {
    return { success: false, error: 'Text is empty' };
  }

  const voice = voiceName || geminiConfig.voices[language];
  const requestedModel = normalizeTtsModelId(model);
  const selectedModel = requestedModel || geminiConfig.ttsModel || DEFAULT_TTS_MODEL;
  const langInstruction =
    language === 'ar'
      ? 'Read this Arabic fitness guidance with clear pronunciation and natural coaching delivery.'
      : 'Read this English fitness guidance with clear pronunciation and an encouraging coaching delivery.';

  const promptParts = [langInstruction];
  if (sharedStylePrompt?.trim()) {
    promptParts.push(`Shared style instructions: ${sharedStylePrompt.trim()}`);
  }
  if (stylePrompt?.trim()) {
    promptParts.push(`Language-specific style instructions: ${stylePrompt.trim()}`);
  }
  promptParts.push('Speak the following text exactly as written without adding, removing, or translating words.');
  promptParts.push(text.trim());
  const prompt = promptParts.join('\n\n');

  const generateWithModel = async (modelId: string): Promise<TTSResult> => {
    console.log('[TTS] Generating speech for:', {
      language,
      voice,
      requestedModel: model ?? '(none)',
      resolvedModel: modelId,
      languageCode: languageCode || '(default)',
      textLength: text.length,
      hasSharedStylePrompt: !!sharedStylePrompt?.trim(),
      hasLanguageStylePrompt: !!stylePrompt?.trim(),
      hasSystemInstruction: !!systemInstruction?.trim(),
      temperature,
      seed,
    });

    const response = await geminiClient.models.generateContent({
      model: modelId,
      contents: [{ parts: [{ text: prompt }] }],
      ...(systemInstruction?.trim() ? { systemInstruction: systemInstruction.trim() } : {}),
      config: {
        responseModalities: ['AUDIO'],
        speechConfig: {
          ...(languageCode?.trim() ? { languageCode: languageCode.trim() } : {}),
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: voice },
          },
        },
        ...(typeof temperature === 'number' ? { temperature } : {}),
        ...(typeof seed === 'number' ? { seed } : {}),
      },
    });

    console.log('[TTS] Response received:', JSON.stringify(response, null, 2).substring(0, 500));

    const audioData = response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;
    if (!audioData) {
      console.error('[TTS] No audio data in response. Full response:', JSON.stringify(response, null, 2));
      return { success: false, error: 'No audio data received' };
    }

    const timestamp = Date.now();
    const randomId = Math.random().toString(36).substring(2, 8);
    const filename = `tts_${language}_${timestamp}_${randomId}.wav`;

    const pcmBuffer = Buffer.from(audioData, 'base64');
    const wavBuffer = createWavBuffer(pcmBuffer);

    const objectName = `exercises/audio/${filename}`;
    const uploadResult = await uploadBufferToGcs(objectName, wavBuffer, 'audio/wav');

    console.log('[TTS] Audio file uploaded:', uploadResult.url);
    console.log('[TTS] PCM size:', pcmBuffer.length, 'bytes, WAV size:', wavBuffer.length, 'bytes');

    return {
      success: true,
      audioPath: uploadResult.url,
    };
  };

  try {
    return await generateWithModel(selectedModel);
  } catch (error) {
    if (isModelNotFoundError(error) && selectedModel !== DEFAULT_TTS_MODEL) {
      console.warn('[TTS] Requested model is unavailable, retrying with fallback:', {
        requestedModel: selectedModel,
        fallbackModel: DEFAULT_TTS_MODEL,
      });
      try {
        return await generateWithModel(DEFAULT_TTS_MODEL);
      } catch (fallbackError) {
        console.error('[TTS] Fallback model also failed:', fallbackError);
        return {
          success: false,
          error: fallbackError instanceof Error ? fallbackError.message : 'TTS generation failed',
        };
      }
    }

    console.error('[TTS] Error:', error);
    return {
      success: false,
      error: error instanceof Error ? error.message : 'TTS generation failed',
    };
  }
}

/**
 * Delete an audio file
 */
export async function deleteAudioFile(audioPath: string): Promise<boolean> {
  try {
    const objectName = parseObjectNameFromUrl(audioPath);
    if (!objectName) {
      return false;
    }
    await deleteObjectFromGcs(objectName);
    return true;
  } catch (error) {
    console.error('Error deleting audio file:', error);
    return false;
  }
}
