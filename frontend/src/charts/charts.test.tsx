import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { ErrorRateBucket, LatencyBucket } from '../api/types';
import { LatencyChart } from './LatencyChart';
import { StatusStrip } from './StatusStrip';

const NOW = Date.parse('2026-11-10T12:30:00Z');
const MIN = 60_000;

const latency = (minutesAgo: number, p50: number | null, p95: number | null, checks = 3): LatencyBucket => ({
  bucketStart: new Date(NOW - minutesAgo * MIN).toISOString(),
  p50,
  p95,
  checks,
});

describe('StatusStrip', () => {
  const bucket = (iso: string, total: number, failed: number): ErrorRateBucket => ({ bucketStart: iso, totalChecks: total, failedChecks: failed, errorRate: failed / total });

  it('draws one cell per hour of the window and colours them by failures', () => {
    render(<StatusStrip window="24h" now={NOW} buckets={[bucket('2026-11-10T11:00:00Z', 60, 0), bucket('2026-11-10T10:00:00Z', 60, 6), bucket('2026-11-10T09:00:00Z', 60, 60)]} />);

    const cells = screen.getAllByRole('button');
    expect(cells).toHaveLength(25);
    expect(cells.filter((c) => c.dataset.state === 'ok')).toHaveLength(1);
    expect(cells.filter((c) => c.dataset.state === 'partial')).toHaveLength(1);
    expect(cells.filter((c) => c.dataset.state === 'bad')).toHaveLength(1);
    expect(cells.filter((c) => c.dataset.state === 'none')).toHaveLength(22);
  });

  it('describes every cell in words, so colour is never the only signal', () => {
    render(<StatusStrip window="24h" now={NOW} buckets={[bucket('2026-11-10T10:00:00Z', 60, 6)]} />);

    expect(screen.getByRole('button', { name: /60 checks, 6 failed\. Some checks failed/ })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /no checks\. No data/ }).length).toBeGreaterThan(0);
  });

  it('shows exact numbers in a tooltip when a cell gets keyboard focus', async () => {
    render(<StatusStrip window="24h" now={NOW} buckets={[bucket('2026-11-10T10:00:00Z', 60, 6)]} />);

    screen.getByRole('button', { name: /60 checks/ }).focus();

    const tooltip = await screen.findByRole('status');
    expect(tooltip).toHaveTextContent('60');
    expect(tooltip).toHaveTextContent('6 (10%)');
  });

  it('has a table view listing only buckets that have data', async () => {
    render(<StatusStrip window="24h" now={NOW} buckets={[bucket('2026-11-10T10:00:00Z', 60, 6)]} />);

    await userEvent.click(screen.getByText('View as table'));

    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows).toHaveLength(2); // header + the one bucket with data
    expect(rows[1]).toHaveTextContent('Some checks failed');
  });
});

describe('LatencyChart', () => {
  it('says so when there is nothing to draw', () => {
    render(<LatencyChart points={[]} window="24h" now={NOW} />);

    expect(screen.getByText('No successful checks in this period yet.')).toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });

  it('draws a median and a p95 line and describes the latest values for screen readers', () => {
    const { container } = render(<LatencyChart window="24h" now={NOW} points={[latency(90, 100, 180), latency(75, 120, 220), latency(60, 110, 200)]} />);

    expect(container.querySelectorAll('path')).toHaveLength(2); // one line per series
    expect(screen.getByRole('img').getAttribute('aria-label')).toMatch(/Median 110 ms, slowest 5 percent 200 ms/);
  });

  it('breaks the line where data is missing instead of bridging the gap', () => {
    const { container } = render(<LatencyChart window="24h" now={NOW} points={[latency(300, 100, 150), latency(285, 110, 160), latency(30, 120, 170), latency(15, 115, 165)]} />);

    expect(container.querySelectorAll('path')).toHaveLength(4); // two runs per series
  });

  it('ignores buckets without successful checks', () => {
    const { container } = render(<LatencyChart window="24h" now={NOW} points={[latency(60, null, null), latency(45, 100, 150), latency(30, 110, 160)]} />);

    expect(container.querySelectorAll('path')).toHaveLength(2);
  });

  it('moves a crosshair with the arrow keys and shows both percentiles', async () => {
    render(<LatencyChart window="24h" now={NOW} points={[latency(90, 100, 180), latency(75, 120, 220), latency(60, 110, 200)]} />);

    screen.getByRole('img').focus();
    await userEvent.keyboard('{ArrowLeft}');

    const tooltip = await screen.findByRole('status');
    expect(tooltip).toHaveTextContent('p50');
    expect(tooltip).toHaveTextContent('p95');
    expect(tooltip).toHaveTextContent('110 ms'); // one step left from "no selection" lands on the last point
  });

  it('offers every point in a table', async () => {
    render(<LatencyChart window="24h" now={NOW} points={[latency(90, 100, 180), latency(75, 1200, 2200)]} />);

    await userEvent.click(screen.getByText('View as table'));

    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('1.2 s');
  });
});
