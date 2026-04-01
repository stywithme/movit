import path from 'path';
import { getGcsBucket, getGcsConfig, getPublicUrl } from './gcs';
export { getGcsBucket } from './gcs';

const DEFAULT_MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const DEFAULT_MAX_AUDIO_BYTES = 30 * 1024 * 1024;

export type UploadCategory =
  | 'camera-position-image'
  | 'exercise-image'
  | 'workout-image'
  | 'exercise-audio';

const CATEGORY_CONFIG: Record<UploadCategory, { folder: string; maxBytes: number; allowedMime: string[] }> = {
  'camera-position-image': {
    folder: 'camera-positions',
    maxBytes: DEFAULT_MAX_IMAGE_BYTES,
    allowedMime: ['image/jpeg', 'image/png', 'image/webp'],
  },
  'exercise-image': {
    folder: 'exercises/images',
    maxBytes: DEFAULT_MAX_IMAGE_BYTES,
    allowedMime: ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
  },
  'workout-image': {
    folder: 'workouts/images',
    maxBytes: DEFAULT_MAX_IMAGE_BYTES,
    allowedMime: ['image/jpeg', 'image/png', 'image/webp', 'image/gif'],
  },
  'exercise-audio': {
    folder: 'exercises/audio',
    maxBytes: DEFAULT_MAX_AUDIO_BYTES,
    allowedMime: ['audio/wav', 'audio/mpeg', 'audio/mp3', 'audio/webm', 'audio/ogg'],
  },
};

export function getCategoryConfig(category: UploadCategory) {
  return CATEGORY_CONFIG[category];
}

export function isAllowedMime(category: UploadCategory, mimeType: string) {
  return CATEGORY_CONFIG[category].allowedMime.includes(mimeType);
}

export function sanitizeFilename(filename: string): string {
  const base = path.basename(filename).toLowerCase();
  return base.replace(/[^a-z0-9._-]+/g, '-');
}

export function buildObjectName(category: UploadCategory, originalName: string, id: string) {
  const config = CATEGORY_CONFIG[category];
  const safeName = sanitizeFilename(originalName);
  const ext = path.extname(safeName);
  const fileBase = safeName.replace(ext, '');
  return `${config.folder}/${id}-${fileBase}${ext || ''}`;
}

export async function uploadBufferToGcs(
  objectName: string,
  buffer: Buffer,
  contentType: string
) {
  const bucket = getGcsBucket();
  const { publicRead } = getGcsConfig();
  const file = bucket.file(objectName);

  await file.save(buffer, {
    resumable: false,
    contentType,
    metadata: { contentType },
  });

  // Try to make public if requested, but skip if bucket has uniform access
  if (publicRead) {
    try {
      await file.makePublic();
    } catch (err: unknown) {
      // Uniform bucket-level access is enabled - skip individual ACL
      // The bucket should be configured with public access at bucket level
      const errorCode = (err as { code?: number })?.code;
      if (errorCode !== 400) {
        throw err; // Re-throw if it's not the uniform access error
      }
      console.log('[GCS] Skipping makePublic - bucket has uniform access enabled');
    }
  }

  return {
    objectName,
    url: getPublicUrl(objectName),
  };
}

export async function deleteObjectFromGcs(objectName: string) {
  const bucket = getGcsBucket();
  const file = bucket.file(objectName);
  await file.delete({ ignoreNotFound: true });
}

export async function deleteByUrl(url: string): Promise<boolean> {
  const objectName = parseObjectNameFromUrl(url);
  if (!objectName) return false;
  await deleteObjectFromGcs(objectName);
  return true;
}

export function parseObjectNameFromUrl(url: string): string | null {
  let bucketName: string;
  try {
    ({ bucketName } = getGcsConfig());
  } catch {
    return null;
  }
  const publicHost = `https://storage.googleapis.com/${bucketName}/`;
  const altHost = `https://${bucketName}.storage.googleapis.com/`;

  if (url.startsWith(publicHost)) {
    return url.slice(publicHost.length);
  }
  if (url.startsWith(altHost)) {
    return url.slice(altHost.length);
  }
  return null;
}

export function getUploadLimits(category: UploadCategory) {
  return CATEGORY_CONFIG[category].maxBytes;
}
