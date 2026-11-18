import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { MonitorDetailPage } from './MonitorDetailPage';
import { mockApi, overviewRow, problem, renderApp, signIn } from '../test/utils';

const monitor = {
  id: 5, name: 'Production API', url: 'https://api.example.com/health', method: 'GET', expectedStatus: 200, intervalSeconds: 60,
  timeoutMs: 5000, latencyThresholdMs: 800, failureThreshold: 3, enabled: true, nextCheckAt: '', createdAt: '', updatedAt: '',
};

const uptime = (percent: number | null, total: number, failed: number, avg: number | null) => ({ uptimePercent: percent, totalChecks: total, failedChecks: failed, avgLatencyMs: avg });

const stats = (overrides: Record<string, unknown> = {}) => ({
  monitorId: 5,
  generatedAt: new Date().toISOString(),
  uptime24h: uptime(99.5, 200, 1, 123),
  uptime7d: uptime(98.25, 1400, 24, 140),
  uptime30d: uptime(97, 5000, 150, 150),
  latency24h: { p50: 110, p95: 240, p99: 300, max: 410 },
  latency7d: { p50: 115, p95: 260, p99: 320, max: 900 },
  errorRate24h: [],
  openIncident: null,
  ...overrides,
});

const incidentsPage = (content: unknown[], total = content.length, page = 0, totalPages = 1) => ({ content, page, size: 10, totalElements: total, totalPages });

function api(extra: Record<string, unknown> = {}, statsOverrides: Record<string, unknown> = {}) {
  return mockApi({
    'GET /api/monitors/5': monitor,
    'GET /api/monitors/overview': [overviewRow({ id: 5, name: 'Production API', status: 'UP' })],
    'GET /api/monitors/5/stats': stats(statsOverrides),
    'GET /api/monitors/5/error-rate': [],
    'GET /api/monitors/5/latency': [],
    'GET /api/monitors/5/incidents': incidentsPage([]),
    ...extra,
  });
}

function renderDetail(id = '5') {
  signIn();
  return renderApp(
    <Routes>
      <Route path="/monitors/:id" element={<MonitorDetailPage />} />
      <Route path="/" element={<p>List page</p>} />
    </Routes>,
    `/monitors/${id}`,
  );
}

describe('MonitorDetailPage', () => {
  it('shows the monitor, its status and the figures of the selected window', async () => {
    api();
    renderDetail();

    expect(await screen.findByRole('heading', { name: 'Production API' })).toBeInTheDocument();
    expect(screen.getByText('GET https://api.example.com/health')).toBeInTheDocument();
    expect(await screen.findByText('99.5%')).toBeInTheDocument();
    expect(screen.getByText('1 failed of 200 checks')).toBeInTheDocument();
    expect(screen.getByText('123 ms')).toBeInTheDocument(); // average response
    expect(screen.getByText('240 ms')).toBeInTheDocument(); // p95
    expect(screen.getByText('Up')).toBeInTheDocument();
  });

  it('switches every figure when another time range is chosen', async () => {
    const { calls } = api();
    renderDetail();
    await screen.findByText('99.5%');

    await userEvent.click(screen.getByRole('radio', { name: '7 days' }));

    expect(await screen.findByText('98.25%')).toBeInTheDocument();
    expect(screen.getByText('260 ms')).toBeInTheDocument();
    await waitFor(() => expect(calls.some((c) => c.url === '/api/monitors/5/error-rate?window=7d')).toBe(true));
    expect(calls.some((c) => c.url === '/api/monitors/5/latency?window=7d')).toBe(true);
  });

  it('explains that 30-day response times are not kept, and does not ask the server for them', async () => {
    const { calls } = api();
    renderDetail();
    await screen.findByText('99.5%');

    await userEvent.click(screen.getByRole('radio', { name: '30 days' }));

    expect(await screen.findByText('97%')).toBeInTheDocument();
    expect(screen.getByText(/Response times are kept for 7 days/)).toBeInTheDocument();
    expect(calls.some((c) => c.url.includes('/latency?window=30d'))).toBe(false);
  });

  it('shows a banner with the outage duration while an incident is open', async () => {
    api({}, { openIncident: { id: 1, monitorId: 5, startedAt: '2026-11-10T10:00:00Z', resolvedAt: null, open: true, durationSeconds: 252, error: 'Timed out after 5000 ms' } });
    renderDetail();

    const banner = await screen.findByRole('alert');
    expect(banner).toHaveTextContent('Down for 4m 12s');
    expect(banner).toHaveTextContent('Timed out after 5000 ms');
  });

  it('lists incidents and loads more on request', async () => {
    const first = { id: 2, monitorId: 5, startedAt: '2026-11-09T10:00:00Z', resolvedAt: '2026-11-09T10:05:00Z', open: false, durationSeconds: 300, error: 'boom' };
    const second = { id: 1, monitorId: 5, startedAt: '2026-11-01T10:00:00Z', resolvedAt: '2026-11-01T10:01:00Z', open: false, durationSeconds: 60, error: 'older boom' };
    api({
      'GET /api/monitors/5/incidents?page=0&size=10': incidentsPage([first], 2, 0, 2),
      'GET /api/monitors/5/incidents?page=1&size=10': incidentsPage([second], 2, 1, 2),
    });
    renderDetail();

    expect(await screen.findByText('boom')).toBeInTheDocument();
    expect(screen.getByText('2 total')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show more' }));
    expect(await screen.findByText('older boom')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Show more' })).not.toBeInTheDocument();
  });

  it('says so when there are no incidents', async () => {
    api();
    renderDetail();

    expect(await screen.findByText(/No incidents/)).toBeInTheDocument();
  });

  it('pauses the monitor', async () => {
    const { calls } = api({ 'POST /api/monitors/5/pause': { ...monitor, enabled: false } });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: 'Pause' }));

    await waitFor(() => expect(calls.some((c) => c.method === 'POST' && c.url === '/api/monitors/5/pause')).toBe(true));
  });

  it('asks for confirmation before deleting, then returns to the list', async () => {
    const { calls } = api({ 'DELETE /api/monitors/5': new Response(null, { status: 204 }) });
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete monitor' }));
    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveTextContent('cannot be undone');
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false); // nothing happens until confirmed

    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete monitor' }));

    expect(await screen.findByText('List page')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'DELETE' && c.url === '/api/monitors/5')).toBe(true);
  });

  it('can be cancelled out of the delete dialog', async () => {
    const { calls } = api();
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: 'Delete monitor' }));
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(calls.some((c) => c.method === 'DELETE')).toBe(false);
  });

  it('opens the edit drawer filled with the monitor', async () => {
    api();
    renderDetail();

    await userEvent.click(await screen.findByRole('button', { name: 'Edit' }));

    expect(screen.getByRole('dialog', { name: 'Edit monitor' })).toBeInTheDocument();
    expect(screen.getByLabelText('Name')).toHaveValue('Production API');
  });

  it('shows a friendly page for a monitor that does not exist or belongs to someone else', async () => {
    mockApi({ 'GET /api/monitors/99': problem(404, 'Monitor 99 not found') });
    renderDetail('99');

    expect(await screen.findByText('Monitor not found')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to monitors' })).toHaveAttribute('href', '/');
  });
});
