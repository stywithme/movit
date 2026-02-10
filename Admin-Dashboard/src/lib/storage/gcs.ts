import { Storage } from '@google-cloud/storage';

interface GcsClientConfig {
  projectId: string;
  bucketName: string;
  publicRead: boolean;
  storage: Storage;
}

let cachedConfig: GcsClientConfig | null = null;

function buildStorageClient(): GcsClientConfig {
  const projectId = process.env.GCS_PROJECT_ID;
  const bucketName = process.env.GCS_BUCKET_NAME;
  const credentialsJson = process.env.GCS_CREDENTIALS_JSON;
  const credentialsPath = process.env.GCS_CREDENTIALS_PATH;

  if (!projectId || !bucketName) {
    throw new Error('GCS_PROJECT_ID and GCS_BUCKET_NAME must be configured');
  }

  let storage: Storage;
  if (credentialsPath) {
    storage = new Storage({ projectId, keyFilename: credentialsPath });
  } else if (credentialsJson) {
    const credentials = JSON.parse(credentialsJson);
    storage = new Storage({ projectId, credentials });
  } else {
    storage = new Storage({ projectId });
  }

  const publicRead = process.env.GCS_PUBLIC_READ !== 'false';

  return { projectId, bucketName, publicRead, storage };
}

export function getGcsConfig(): GcsClientConfig {
  if (!cachedConfig) {
    cachedConfig = buildStorageClient();
  }
  return cachedConfig;
}

export function getGcsBucket() {
  const { storage, bucketName } = getGcsConfig();
  return storage.bucket(bucketName);
}

export function getPublicUrl(objectName: string): string {
  const { bucketName } = getGcsConfig();
  return `https://storage.googleapis.com/${bucketName}/${objectName}`;
}
