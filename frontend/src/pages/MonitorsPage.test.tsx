import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { MonitorsPage, sortMonitors } from './MonitorsPage';
import type { MonitorOverview } from '../api/types';
import { mockApi, overviewRow, problem, renderApp, signIn } from '../test/utils';

const row = (overrides: Partial<MonitorOverview>) => overviewRow(overrides as Record<string, unknown>) as MonitorOverview;

describe('sortMonitors', () => {
  it('puts trouble first, then healthy ones, paused last, and sorts by name within a group', () => {
    const sorted = sortMonitors([
      row({ id: 1, name: 'b-up', status: 'UP' }),
      row({ id: 2, name: 'a-paused', status: 'PAUSED' }),
      row({ id: 3, name: 'z-down', status: 'DOWN' }),
      row({ id: 4, name: 'a-up', status: 'UP' }),
      row({ id: 5, name: 'c-failing', status: 'DEGRADED' }),
      row({ id: 6, name: 'd-new', status: 'PENDING' }),
    ]);

    expect(sorted.map((m) => m.name)).toEqual(['z-down', 'c-failing', 'd-new', 'a-up', 'b-up', 'a-paused']);
  });
});

describe('MonitorsPage', () => {
  it('lists monitors with a word for every status, trouble first', async () => {
    signIn();
    mockApi({
      'GET /api/monitors/overview': [
        overviewRow({ id: 1, name: 'Healthy', status: 'UP' }),
        overviewRow({ id: 2, name: 'Broken', status: 'DOWN', lastLatencyMs: null, uptime24h: 91.2 }),
        overviewRow({ id: 3, name: 'Asleep', status: 'PAUSED', lastCheckedAt: null, uptime24h: null, lastLatencyMs: null }),
      ],
    });
    renderApp(<MonitorsPage />);

    const rows = await screen.findAllByRole('row');
    const body = rows.slice(1); // the first row is the header
    expect(body.map((r) => within(r).getByText(/Healthy|Broken|Asleep/).textContent)).toEqual(['Broken', 'Healthy', 'Asleep']);
    expect(within(body[0]!).getByText('Down')).toBeInTheDocument();
    expect(within(body[1]!).getByText('Up')).toBeInTheDocument();
    expect(within(body[2]!).getByText('Paused')).toBeInTheDocument();
    expect(within(body[0]!).getByText('91.2%')).toBeInTheDocument();
    expect(within(body[2]!).getByText('never')).toBeInTheDocument();
  });

  it('summarises how many monitors are in each state', async () => {
    signIn();
    mockApi({
      'GET /api/monitors/overview': [
        overviewRow({ id: 1, status: 'UP' }), overviewRow({ id: 2, status: 'UP' }),
        overviewRow({ id: 3, status: 'DOWN' }), overviewRow({ id: 4, status: 'DEGRADED' }),
      ],
    });
    renderApp(<MonitorsPage />);

    const summary = await screen.findByLabelText('Summary');
    expect(summary).toHaveTextContent('1 down');
    expect(summary).toHaveTextContent('1 failing');
    expect(summary).toHaveTextContent('2 up');
  });

  it('links every row to its detail page', async () => {
    signIn();
    mockApi({ 'GET /api/monitors/overview': [overviewRow({ id: 42, name: 'Homepage' })] });
    renderApp(<MonitorsPage />);

    expect((await screen.findByRole('row', { name: /Homepage/ })).getAttribute('href')).toBe('/monitors/42');
  });

  it('invites the user to add a first monitor when there are none', async () => {
    signIn();
    mockApi({ 'GET /api/monitors/overview': [] });
    renderApp(<MonitorsPage />);

    expect(await screen.findByText('No monitors yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add your first monitor' }));
    expect(screen.getByRole('dialog', { name: 'New monitor' })).toBeInTheDocument();
  });

  it('shows an error with a retry button when the server fails', async () => {
    signIn();
    let attempts = 0;
    mockApi({ 'GET /api/monitors/overview': () => (++attempts === 1 ? problem(503, 'Service unavailable') : [overviewRow({ name: 'Recovered' })]) });
    renderApp(<MonitorsPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Service unavailable');
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Recovered')).toBeInTheDocument();
  });

  it('filters by name or URL once the list is long enough', async () => {
    signIn();
    const many = Array.from({ length: 7 }, (_, i) => overviewRow({ id: i + 1, name: `Service ${i + 1}`, url: `https://svc${i + 1}.example.com` }));
    mockApi({ 'GET /api/monitors/overview': many });
    renderApp(<MonitorsPage />);

    await screen.findByText('Service 1');
    await userEvent.type(screen.getByLabelText('Filter monitors'), 'svc3');

    expect(screen.getByText('Service 3')).toBeInTheDocument();
    expect(screen.queryByText('Service 1')).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Filter monitors'));
    await userEvent.type(screen.getByLabelText('Filter monitors'), 'zzz');
    expect(screen.getByText(/No monitor matches/)).toBeInTheDocument();
  });
});
