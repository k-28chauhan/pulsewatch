import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MonitorDrawer } from './MonitorDrawer';
import type { Monitor } from '../api/types';
import { mockApi, problem, renderApp, signIn } from '../test/utils';

const existing: Monitor = {
  id: 9, name: 'Old name', url: 'https://old.example.com', method: 'GET', expectedStatus: 200, intervalSeconds: 60,
  timeoutMs: 5000, latencyThresholdMs: null, failureThreshold: 3, enabled: true, nextCheckAt: '', createdAt: '', updatedAt: '',
};

describe('MonitorDrawer (create)', () => {
  it('shows inline errors and sends nothing when the form is empty', async () => {
    signIn();
    const { fetchMock } = mockApi({});
    renderApp(<MonitorDrawer onClose={() => {}} />);

    await userEvent.click(screen.getByRole('button', { name: 'Create monitor' }));

    expect(await screen.findByText('Give the monitor a name')).toBeInTheDocument();
    expect(screen.getByText('Enter the URL to check')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('posts the request, then closes', async () => {
    signIn();
    const onClose = vi.fn();
    const { calls } = mockApi({ 'POST /api/monitors': { ...existing, id: 10 }, 'GET /api/monitors/overview': [] });
    renderApp(<MonitorDrawer onClose={onClose} />);

    await userEvent.type(screen.getByLabelText('Name'), 'Homepage');
    await userEvent.type(screen.getByLabelText('URL'), 'https://example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Create monitor' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(calls.find((c) => c.method === 'POST')).toMatchObject({
      url: '/api/monitors',
      body: { name: 'Homepage', url: 'https://example.com', method: 'GET', expectedStatus: 200, intervalSeconds: 60, timeoutMs: 5000, latencyThresholdMs: null, failureThreshold: 3 },
    });
  });

  it('catches a timeout that is not shorter than the interval before asking the server', async () => {
    signIn();
    const { fetchMock } = mockApi({});
    renderApp(<MonitorDrawer onClose={() => {}} />);

    await userEvent.type(screen.getByLabelText('Name'), 'x');
    await userEvent.type(screen.getByLabelText('URL'), 'https://example.com');
    await userEvent.selectOptions(screen.getByLabelText('Check'), '10');
    await userEvent.clear(screen.getByLabelText('Timeout'));
    await userEvent.type(screen.getByLabelText('Timeout'), '10000');
    await userEvent.click(screen.getByRole('button', { name: 'Create monitor' }));

    expect(await screen.findByText('Must be shorter than the interval (10000 ms)')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('shows the field errors the server sends back and stays open', async () => {
    signIn();
    const onClose = vi.fn();
    mockApi({ 'POST /api/monitors': problem(400, 'Validation failed', { url: 'must match "^https?://.*"' }) });
    renderApp(<MonitorDrawer onClose={onClose} />);

    await userEvent.type(screen.getByLabelText('Name'), 'x');
    await userEvent.type(screen.getByLabelText('URL'), 'https://example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Create monitor' }));

    expect(await screen.findByText('must match "^https?://.*"')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });

  it('closes on Escape and on Cancel', async () => {
    signIn();
    mockApi({});
    const onClose = vi.fn();
    renderApp(<MonitorDrawer onClose={onClose} />);

    await userEvent.keyboard('{Escape}');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it('keeps the advanced fields tucked away until asked for', async () => {
    signIn();
    mockApi({});
    renderApp(<MonitorDrawer onClose={() => {}} />);

    expect(screen.queryByLabelText('Expected status')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Advanced' }));
    expect(screen.getByLabelText('Expected status')).toBeInTheDocument();
    expect(screen.getByLabelText('Slow response threshold')).toBeInTheDocument();
  });
});

describe('MonitorDrawer (edit)', () => {
  it('is filled with the monitor and PUTs the changes', async () => {
    signIn();
    const onClose = vi.fn();
    const { calls } = mockApi({ 'PUT /api/monitors/9': existing, 'GET /api/monitors/overview': [] });
    renderApp(<MonitorDrawer monitor={existing} onClose={onClose} />);

    expect(screen.getByLabelText('Name')).toHaveValue('Old name');
    await userEvent.clear(screen.getByLabelText('Name'));
    await userEvent.type(screen.getByLabelText('Name'), 'New name');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(calls.find((c) => c.method === 'PUT')).toMatchObject({ url: '/api/monitors/9', body: { name: 'New name', url: 'https://old.example.com' } });
  });

  it('offers the monitor\'s current interval even if it is not one of the presets', () => {
    signIn();
    mockApi({});
    renderApp(<MonitorDrawer monitor={{ ...existing, intervalSeconds: 45 }} onClose={() => {}} />);

    expect(screen.getByRole('option', { name: 'Every 45 seconds' })).toBeInTheDocument();
  });
});
