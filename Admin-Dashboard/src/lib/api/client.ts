export interface ApiPagination {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  error?: string;
  message?: string;
  pagination?: ApiPagination;
}

export class ApiError extends Error {
  status: number;
  payload: unknown;

  constructor(message: string, status: number, payload?: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
  }
}

export interface ApiRequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | null | undefined>;
  redirectOnUnauthorized?: boolean;
}

function buildUrl(path: string, params?: ApiRequestOptions['params']) {
  const url = path.startsWith('/api') ? path : `/api${path.startsWith('/') ? path : `/${path}`}`;
  if (!params) return url;

  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      searchParams.set(key, String(value));
    }
  });

  const query = searchParams.toString();
  return query ? `${url}?${query}` : url;
}

async function parseJson(response: Response) {
  const text = await response.text();
  if (!text) return null;

  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<ApiResponse<T>> {
  const { params, redirectOnUnauthorized = true, headers, body, ...init } = options;
  const isFormData = typeof FormData !== 'undefined' && body instanceof FormData;

  const response = await fetch(buildUrl(path, params), {
    credentials: 'include',
    ...init,
    body,
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
  });

  const payload = await parseJson(response);

  if (response.status === 401 && redirectOnUnauthorized && typeof window !== 'undefined') {
    const redirect = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.href = `/admin/login?redirect=${redirect}`;
  }

  if (!response.ok) {
    throw new ApiError(extractApiError(payload, response.statusText), response.status, payload);
  }

  if (payload && typeof payload === 'object' && 'success' in payload && payload.success === false) {
    throw new ApiError(extractApiError(payload), response.status, payload);
  }

  return payload as ApiResponse<T>;
}

export function extractApiError(payload: unknown, fallback = 'Request failed') {
  if (!payload) return fallback;
  if (typeof payload === 'string') return payload;
  if (typeof payload === 'object') {
    const record = payload as Record<string, unknown>;
    if (typeof record.error === 'string') return record.error;
    if (typeof record.message === 'string') return record.message;
    if (Array.isArray(record.message)) return record.message.join(', ');
  }
  return fallback;
}

export const api = {
  get: <T>(path: string, options?: ApiRequestOptions) => apiRequest<T>(path, { ...options, method: 'GET' }),
  post: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    apiRequest<T>(path, {
      ...options,
      method: 'POST',
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
    }),
  put: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    apiRequest<T>(path, {
      ...options,
      method: 'PUT',
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
    }),
  patch: <T>(path: string, body?: unknown, options?: ApiRequestOptions) =>
    apiRequest<T>(path, {
      ...options,
      method: 'PATCH',
      body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
    }),
  delete: <T>(path: string, options?: ApiRequestOptions) => apiRequest<T>(path, { ...options, method: 'DELETE' }),
};
