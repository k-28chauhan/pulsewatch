/**
 * The single place that talks HTTP. Adds the bearer token, turns error responses into ApiError,
 * and tells the app when the token is no longer accepted.
 */

export class ApiError extends Error {
  readonly status: number;
  /** Per-field messages from a 400 validation response, e.g. { url: "must be a valid URL" }. */
  readonly fieldErrors: Record<string, string>;

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

let authToken: string | null = null;
let unauthorizedHandler: () => void = () => {};

/** Set synchronously, BEFORE any request can be made: the very first fetch after a page reload must already carry the token. */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

/** Called when a protected endpoint answers 401 (an expired or invalid token). */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

interface ProblemDetail {
  detail?: string;
  title?: string;
  errors?: Record<string, string>;
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) headers.set('Content-Type', 'application/json');
  if (authToken) headers.set('Authorization', `Bearer ${authToken}`);

  let response: Response;
  try {
    response = await fetch(path, { ...init, headers });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }

  if (!response.ok) {
    // A 401 from /api/auth/* just means "wrong password"; anywhere else it means "your session is no good".
    if (response.status === 401 && !path.startsWith('/api/auth/')) unauthorizedHandler();
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail;
    throw new ApiError(response.status, problem.detail ?? problem.title ?? `Request failed (${response.status})`, problem.errors);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export const get = <T>(path: string) => request<T>(path);
export const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
export const put = <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) });
export const del = <T>(path: string) => request<T>(path, { method: 'DELETE' });
