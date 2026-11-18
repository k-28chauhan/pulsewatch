import type { ErrorRateBucket } from '../api/types';

/** Rounds up to 1, 2, 2.5, 5 or 10 times a power of ten: "nice" numbers for an axis. */
export function niceCeil(value: number): number {
  if (!(value > 0)) return 1;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const fraction = value / magnitude;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 2.5 ? 2.5 : fraction <= 5 ? 5 : 10;
  return nice * magnitude;
}

/** Y axis for values from 0 to `max`: { top, ticks } with evenly spaced round ticks, e.g. 0, 100, 200, 300, 400. */
export function yAxis(max: number, intervals = 4): { top: number; ticks: number[] } {
  const step = niceCeil(max / intervals);
  return { top: step * intervals, ticks: Array.from({ length: intervals + 1 }, (_, i) => i * step) };
}

/** Compact axis label: 250 -> "250 ms", 1500 -> "1.5 s". */
export function latencyTick(ms: number): string {
  if (ms === 0) return '0';
  return ms < 1000 ? `${ms} ms` : `${Number((ms / 1000).toFixed(1))} s`;
}

/** Evenly spaced X ticks that fall on whole steps of local time (so a 6 h step labels 00:00, 06:00, ...). */
export function timeTicks(startMs: number, endMs: number, stepMs: number): number[] {
  const offset = new Date(startMs).getTimezoneOffset() * 60_000; // local midnight is not a multiple of a day in UTC
  const first = Math.ceil((startMs - offset) / stepMs) * stepMs + offset;
  const ticks: number[] = [];
  for (let t = first; t <= endMs; t += stepMs) ticks.push(t);
  return ticks;
}

export type CellState = 'ok' | 'partial' | 'bad' | 'none';

export interface StripCell {
  start: number;
  end: number;
  totalChecks: number;
  failedChecks: number;
  state: CellState;
}

export function cellState(total: number, failed: number): CellState {
  if (total === 0) return 'none';
  if (failed === 0) return 'ok';
  return failed / total < 0.5 ? 'partial' : 'bad';
}

/**
 * One cell for EVERY bucket of the window, including buckets with no data (the server omits those),
 * so a monitor that was silent for six hours shows six grey cells instead of a misleadingly short strip.
 */
export function buildStripCells(buckets: ErrorRateBucket[], windowMs: number, bucketMs: number, now: number): StripCell[] {
  const byStart = new Map(buckets.map((b) => [new Date(b.bucketStart).getTime(), b]));
  const firstStart = Math.floor((now - windowMs) / bucketMs) * bucketMs;
  const lastStart = Math.floor(now / bucketMs) * bucketMs;
  const cells: StripCell[] = [];
  for (let start = firstStart; start <= lastStart; start += bucketMs) {
    const bucket = byStart.get(start);
    const total = bucket?.totalChecks ?? 0;
    const failed = bucket?.failedChecks ?? 0;
    cells.push({ start, end: start + bucketMs, totalChecks: total, failedChecks: failed, state: cellState(total, failed) });
  }
  return cells;
}

/** Splits points into runs: a gap longer than `maxGapMs` starts a new run, so the line breaks instead of bridging missing data. */
export function splitRuns<T extends { t: number }>(points: T[], maxGapMs: number): T[][] {
  const runs: T[][] = [];
  let current: T[] = [];
  for (const point of points) {
    const previous = current[current.length - 1];
    if (previous && point.t - previous.t > maxGapMs) {
      runs.push(current);
      current = [];
    }
    current.push(point);
  }
  if (current.length > 0) runs.push(current);
  return runs;
}
