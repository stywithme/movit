/**
 * Gemini Text-to-Speech Helper
 * ==============================
 * 
 * Converts text to speech using Gemini TTS API.
 * Supports Arabic and English with configurable voices.
 */

import { geminiClient, geminiConfig, type SupportedLanguage } from './client';
import * as fs from 'fs/promises';
import * as path from 'path';

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
  voiceName?: string; // Override default voice
}

interface TTSResult {
  success: boolean;
  audioPath?: string;     // Relative path for client access
  audioFullPath?: string; // Full path on server
  error?: string;
}

/**
 * Generate speech from text and save to file
 */
export async function generateSpeech(options: TTSOptions): Promise<TTSResult> {
  const { text, language, voiceName } = options;

  if (!text.trim()) {
    return { success: false, error: 'Text is empty' };
  }

  // Select voice based on language or use override
  const voice = voiceName || geminiConfig.voices[language];

  // Build speech prompt with language-specific instructions
  const langInstruction = language === 'ar' 
    ? 'Speak this text in Arabic with clear pronunciation suitable for fitness coaching:'
    : 'Speak this text in English with clear, encouraging tone suitable for fitness coaching:';

  const prompt = `${langInstruction}\n${text}`;

  try {
    console.log('[TTS] Generating speech for:', { language, voice, textLength: text.length });
    console.log('[TTS] Using model:', geminiConfig.ttsModel);
    
    const response = await geminiClient.models.generateContent({
      model: geminiConfig.ttsModel,
      contents: [{ parts: [{ text: prompt }] }],
      config: {
        responseModalities: ['AUDIO'],
        speechConfig: {
          voiceConfig: {
            prebuiltVoiceConfig: { voiceName: voice },
          },
        },
      },
    });

    console.log('[TTS] Response received:', JSON.stringify(response, null, 2).substring(0, 500));
    
    const audioData = response.candidates?.[0]?.content?.parts?.[0]?.inlineData?.data;

    if (!audioData) {
      console.error('[TTS] No audio data in response. Full response:', JSON.stringify(response, null, 2));
      return { success: false, error: 'No audio data received' };
    }
    
    console.log('[TTS] Audio data received, length:', audioData.length);

    // Generate unique filename
    const timestamp = Date.now();
    const randomId = Math.random().toString(36).substring(2, 8);
    const filename = `tts_${language}_${timestamp}_${randomId}.wav`;

    // Ensure audio directory exists
    const audioDir = path.join(process.cwd(), 'public', 'audio', 'tts');
    await fs.mkdir(audioDir, { recursive: true });

    // Convert PCM data to WAV format
    const pcmBuffer = Buffer.from(audioData, 'base64');
    const wavBuffer = createWavBuffer(pcmBuffer);
    
    // Save audio file
    const fullPath = path.join(audioDir, filename);
    await fs.writeFile(fullPath, wavBuffer);
    
    console.log('[TTS] Audio file saved:', fullPath);
    console.log('[TTS] PCM size:', pcmBuffer.length, 'bytes, WAV size:', wavBuffer.length, 'bytes');

    // Return relative path for client access
    const relativePath = `/audio/tts/${filename}`;

    return {
      success: true,
      audioPath: relativePath,
      audioFullPath: fullPath,
    };
  } catch (error) {
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
    // Convert relative path to full path
    const fullPath = path.join(process.cwd(), 'public', audioPath);
    await fs.unlink(fullPath);
    return true;
  } catch (error) {
    console.error('Error deleting audio file:', error);
    return false;
  }
}
