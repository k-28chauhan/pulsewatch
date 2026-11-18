import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { setAuthToken } from '../api/client';

type Handler = unknown | ((request: { body: unknown }) => unknown);

export interface RecordedCall {
  method: string;
  url: string;
  body: unknown;
}

/** A Response with a ProblemDetail body, like the API's error answers. */
export function problem(status: number, detail: string, errors?: Record<string, string>): Response {
  return new Response(JSON.stringify({ status, detail, errors }), { status, headers: { 'Content-Type': 'application/json' } });
}

/**
 * Replaces fetch with a fake that answers by "METHOD /path" (the query string is optional in the key).
 * Handlers return JSON data, a Response, or a function of the request body. Every call is recorded.
 */
export function mockApi(handlers: Record<string, Handler>) {
  const calls: RecordedCall[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    const method = init?.method ?? 'GET';
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method, url, body });
    const handler = handlers[`${method} ${url}`] ?? handlers[`${method} ${url.split('?')[0]}`];
    if (handler === undefined) return problem(500, `Unmocked request: ${method} ${url}`);
    const result = typeof handler === 'function' ? (handler as (r: { body: unknown }) => unknown)({ body }) : handler;
    if (result instanceof Response) return result;
    return new Response(JSON.stringify(result), { status: 200, headers: { 'Content-Type': 'application/json' } });
  });
  vi.stubGlobal('fetch', fetchMock);
  return { calls, fetchMock };
}

/** Pretends the user is already signed in (as after a page reload). */
export function signIn(email = 'ann@example.com') {
  const expiresAt = new Date(Date.now() + 3600_000).toISOString();
  localStorage.setItem('pulsewatch.session', JSON.stringify({ token: 'test-token', expiresAt, email }));
}

export function renderApp(ui: ReactElement, route = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 } } });
  setAuthToken(null);
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <AuthProvider>{ui}</AuthProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export const overviewRow = (overrides: Record<string, unknown> = {}) => ({
  id: 1,
  name: 'Homepage',
  url: 'https://example.com',
  status: 'UP',
  enabled: true,
  intervalSeconds: 60,
  lastCheckedAt: new Date().toISOString(),
  lastLatencyMs: 120,
  uptime24h: 99.5,
  ...overrides,
});
