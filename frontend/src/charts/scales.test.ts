import { describe, expect, it } from 'vitest';
import type { ErrorRateBucket } from '../api/types';
import { buildStripCells, cellState, latencyTick, niceCeil, splitRuns, timeTicks, yAxis } from './scales';

describe('niceCeil', () => {
  it('rounds up to 1, 2, 2.5, 5 or 10 times a power of ten', () => {
    expect(niceCeil(0.7)).toBe(1);
    expect(niceCeil(1.4)).toBe(2);
    expect(niceCeil(2.3)).toBe(2.5);
    expect(niceCeil(3)).toBe(5);
    expect(niceCeil(70)).toBe(100);
    expect(niceCeil(250)).toBe(250);
  });

  it('falls back to 1 for zero or negative input', () => {
    expect(niceCeil(0)).toBe(1);
    expect(niceCeil(-4)).toBe(1);
  });
});

describe('yAxis', () => {
  it('gives round, evenly spaced ticks starting at zero that cover the maximum', () => {
    const axis = yAxis(310);
    expect(axis.ticks).toEqual([0, 100, 200, 300, 400]);
    expect(axis.top).toBe(400);
  });

  it('copes with tiny values', () => {
    const axis = yAxis(3);
    expect(axis.top).toBeGreaterThanOrEqual(3);
    expect(axis.ticks[0]).toBe(0);
  });
});

describe('latencyTick', () => {
  it('is compact', () => {
    expect(latencyTick(0)).toBe('0');
    expect(latencyTick(250)).toBe('250 ms');
    expect(latencyTick(1500)).toBe('1.5 s');
    expect(latencyTick(2000)).toBe('2 s');
  });
});

describe('timeTicks', () => {
  it('returns ticks on whole steps inside the range', () => {
    const hour = 3600_000;
    const start = Date.parse('2026-11-10T00:30:00Z');
    const ticks = timeTicks(start, start + 24 * hour, 6 * hour);

    expect(ticks.length).toBeGreaterThanOrEqual(3);
    expect(ticks.every((t) => t >= start && t <= start + 24 * hour)).toBe(true);
    expect(ticks.slice(1).every((t, i) => t - (ticks[i] as number) === 6 * hour)).toBe(true);
  });
});

describe('cellState', () => {
  it('classifies a bucket by its share of failed checks', () => {
    expect(cellState(0, 0)).toBe('none');
    expect(cellState(10, 0)).toBe('ok');
    expect(cellState(10, 2)).toBe('partial');
    expect(cellState(10, 5)).toBe('bad');
    expect(cellState(2, 2)).toBe('bad');
  });
});

describe('buildStripCells', () => {
  const hour = 3600_000;
  const now = Date.parse('2026-11-10T12:30:00Z');

  const bucket = (iso: string, total: number, failed: number): ErrorRateBucket => ({ bucketStart: iso, totalChecks: total, failedChecks: failed, errorRate: failed / total });

  it('makes one cell per bucket of the window, including empty ones', () => {
    const cells = buildStripCells([bucket('2026-11-10T10:00:00Z', 60, 6)], 24 * hour, hour, now);

    expect(cells).toHaveLength(25); // 24 h window with a half-hour offset touches 25 hourly buckets
    expect(cells.filter((c) => c.state === 'none')).toHaveLength(24);
    const filled = cells.find((c) => c.start === Date.parse('2026-11-10T10:00:00Z'));
    expect(filled).toMatchObject({ totalChecks: 60, failedChecks: 6, state: 'partial' });
  });

  it('orders cells oldest first and ends with the current bucket', () => {
    const cells = buildStripCells([], 24 * hour, hour, now);

    expect(cells[0]!.start).toBeLessThan(cells[1]!.start);
    expect(cells[cells.length - 1]!.start).toBe(Date.parse('2026-11-10T12:00:00Z'));
  });
});

describe('splitRuns', () => {
  it('breaks the line where data is missing for longer than the allowed gap', () => {
    const points = [{ t: 0 }, { t: 10 }, { t: 20 }, { t: 100 }, { t: 110 }];

    expect(splitRuns(points, 30)).toEqual([[{ t: 0 }, { t: 10 }, { t: 20 }], [{ t: 100 }, { t: 110 }]]);
  });

  it('returns nothing for no points and one run for a single point', () => {
    expect(splitRuns([], 10)).toEqual([]);
    expect(splitRuns([{ t: 5 }], 10)).toEqual([[{ t: 5 }]]);
  });
});
