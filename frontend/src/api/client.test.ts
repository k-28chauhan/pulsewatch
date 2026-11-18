import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, get, post, setAuthToken, setUnauthorizedHandler } from './client';
import { problem } from '../test/utils';

afterEach(() => {
  setAuthToken(null);
  setUnauthorizedHandler(() => {});
  vi.unstubAllGlobals();
});

function stubFetch(response: Response) {
  const fn = vi.fn(async () => response);
  vi.stubGlobal('fetch', fn);
  return fn;
}

describe('request', () => {
  it('sends the bearer token once one is set', async () => {
    const fetchMock = stubFetch(new Response('[]', { status: 200 }));
    setAuthToken('abc');

    await get('/api/monitors');

    const headers = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].headers as Headers;
    expect(headers.get('Authorization')).toBe('Bearer abc');
  });

  it('sends no Authorization header without a token', async () => {
    const fetchMock = stubFetch(new Response('[]', { status: 200 }));

    await get('/api/monitors');

    const headers = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].headers as Headers;
    expect(headers.has('Authorization')).toBe(false);
  });

  it('posts JSON with a content type', async () => {
    const fetchMock = stubFetch(new Response('{}', { status: 200 }));

    await post('/api/monitors', { name: 'x' });

    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"name":"x"}');
    expect((init.headers as Headers).get('Content-Type')).toBe('application/json');
  });

  it('returns undefined for 204 No Content', async () => {
    stubFetch(new Response(null, { status: 204 }));

    await expect(get('/api/monitors/1')).resolves.toBeUndefined();
  });

  it('turns a ProblemDetail into an ApiError with field errors', async () => {
    stubFetch(problem(400, 'Validation failed', { url: 'must be a valid URL' }));

    const error = await get('/api/monitors').catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).message).toBe('Validation failed');
    expect((error as ApiError).fieldErrors).toEqual({ url: 'must be a valid URL' });
  });

  it('survives an error body that is not JSON', async () => {
    stubFetch(new Response('<html>Bad gateway</html>', { status: 502 }));

    const error = (await get('/api/monitors').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(502);
    expect(error.message).toContain('502');
  });

  it('reports an unreachable server in plain words', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch'); }));

    const error = (await get('/api/monitors').catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(0);
    expect(error.message).toMatch(/cannot reach the server/i);
  });

  it('signs the user out on a 401 from a protected endpoint', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    stubFetch(problem(401, 'expired'));

    await get('/api/monitors').catch(() => {});

    expect(onUnauthorized).toHaveBeenCalledOnce();
  });

  it('does NOT sign out on a 401 from login: that just means a wrong password', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    stubFetch(problem(401, 'Invalid email or password'));

    await post('/api/auth/login', { email: 'a', password: 'b' }).catch(() => {});

    expect(onUnauthorized).not.toHaveBeenCalled();
  });
});
