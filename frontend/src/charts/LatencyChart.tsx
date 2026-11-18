import { useMemo, useState } from 'react';
import type { LatencyBucket, Window } from '../api/types';
import { formatAxisTime, formatDateTime, formatLatency } from '../lib/format';
import { latencyTick, splitRuns, timeTicks, yAxis } from './scales';
import { useElementWidth } from './useElementWidth';

const HEIGHT = 220;
const MARGIN = { top: 10, right: 34, bottom: 24, left: 52 };
const WINDOW_MS = { '24h': 24 * 3600_000, '7d': 7 * 24 * 3600_000 } as const;
const BUCKET_MS = { '24h': 15 * 60_000, '7d': 3600_000 } as const;

interface Point {
  t: number;
  p50: number;
  p95: number;
  checks: number;
}

export function LatencyLegend() {
  return (
    <div className="legend" aria-hidden="false">
      <span><i style={{ background: 'var(--series-primary)' }} />p50 median</span>
      <span><i style={{ background: 'var(--series-secondary)' }} />p95 slowest 5%</span>
    </div>
  );
}

interface Props {
  points: LatencyBucket[];
  window: Exclude<Window, '30d'>;
  /** Injected for tests; defaults to the real clock. */
  now?: number;
}

/**
 * Response time over time: median (p50) and slowest-5% (p95) latency of the successful checks.
 * Hand-drawn SVG, so every mark follows the design spec: 2px lines, hairline grid, one-hue-plus-gray.
 * Hover or arrow keys move a crosshair; the same numbers are in the table underneath.
 */
export function LatencyChart({ points, window, now = Date.now() }: Props) {
  const [containerRef, width] = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);

  const windowMs = WINDOW_MS[window];
  const bucketMs = BUCKET_MS[window];
  const start = now - windowMs;

  const data: Point[] = useMemo(
    () =>
      points
        .filter((p) => p.p50 != null && p.p95 != null)
        .map((p) => ({ t: new Date(p.bucketStart).getTime() + bucketMs / 2, p50: p.p50 as number, p95: p.p95 as number, checks: p.checks }))
        .sort((a, b) => a.t - b.t),
    [points, bucketMs],
  );

  if (data.length === 0) {
    return <div className="chart-note">No successful checks in this period yet.</div>;
  }

  const plotW = Math.max(width - MARGIN.left - MARGIN.right, 50);
  const plotH = HEIGHT - MARGIN.top - MARGIN.bottom;
  const axis = yAxis(Math.max(...data.map((d) => d.p95)) * 1.05);
  const x = (t: number) => MARGIN.left + ((t - start) / windowMs) * plotW;
  const y = (v: number) => MARGIN.top + plotH - (v / axis.top) * plotH;

  const xTicks = timeTicks(start, now, window === '24h' ? 6 * 3600_000 : 24 * 3600_000);
  const runs = (key: 'p50' | 'p95') =>
    splitRuns(data, bucketMs * 3).map((run) => run.map((d, i) => `${i === 0 ? 'M' : 'L'}${x(d.t).toFixed(1)},${y(d[key]).toFixed(1)}`).join(' '));
  const singles = (key: 'p50' | 'p95') => splitRuns(data, bucketMs * 3).filter((run) => run.length === 1).map((run) => run[0] as Point).map((d) => ({ cx: x(d.t), cy: y(d[key]) }));

  const last = data[data.length - 1] as Point;
  const labelsFit = Math.abs(y(last.p50) - y(last.p95)) >= 14;
  const hovered = active === null ? null : (data[active] as Point);

  function nearestIndex(clientX: number, rect: DOMRect): number {
    const px = clientX - rect.left;
    let best = 0;
    for (let i = 1; i < data.length; i++) {
      if (Math.abs(x((data[i] as Point).t) - px) < Math.abs(x((data[best] as Point).t) - px)) best = i;
    }
    return best;
  }

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const step = event.key === 'ArrowLeft' ? -1 : 1;
    setActive((current) => Math.min(data.length - 1, Math.max(0, (current ?? data.length) + step)));
  }

  const tooltipLeft = hovered ? (x(hovered.t) > width - 190 ? x(hovered.t) - 176 : x(hovered.t) + 14) : 0;

  return (
    <div className="chart" ref={containerRef}>
      <svg
        width={width}
        height={HEIGHT}
        viewBox={`0 0 ${width} ${HEIGHT}`}
        role="img"
        aria-label={`Response time over the last ${window}. Median ${formatLatency(last.p50)}, slowest 5 percent ${formatLatency(last.p95)} at the latest point. A data table follows.`}
        tabIndex={0}
        onKeyDown={onKeyDown}
        onBlur={() => setActive(null)}
        onPointerMove={(e) => setActive(nearestIndex(e.clientX, e.currentTarget.getBoundingClientRect()))}
        onPointerLeave={() => setActive(null)}
      >
        {axis.ticks.map((tick) => (
          <g key={tick}>
            <line className="grid" x1={MARGIN.left} x2={MARGIN.left + plotW} y1={y(tick)} y2={y(tick)} />
            <text className="axis-label" x={MARGIN.left - 8} y={y(tick) + 4} textAnchor="end">{latencyTick(tick)}</text>
          </g>
        ))}
        {xTicks.map((tick) => (
          <text key={tick} className="axis-label" x={x(tick)} y={HEIGHT - 6} textAnchor="middle">{formatAxisTime(tick, windowMs)}</text>
        ))}

        {/* p95 first, so the median line is drawn on top where they touch */}
        {runs('p95').map((d, i) => <path key={`p95-${i}`} d={d} fill="none" stroke="var(--series-secondary)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />)}
        {runs('p50').map((d, i) => <path key={`p50-${i}`} d={d} fill="none" stroke="var(--series-primary)" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />)}
        {singles('p95').map((p, i) => <circle key={`s95-${i}`} cx={p.cx} cy={p.cy} r={4} fill="var(--series-secondary)" stroke="var(--surface)" strokeWidth={2} />)}
        {singles('p50').map((p, i) => <circle key={`s50-${i}`} cx={p.cx} cy={p.cy} r={4} fill="var(--series-primary)" stroke="var(--surface)" strokeWidth={2} />)}

        {/* latest value: end dot (with its 2px surface ring) and, when they do not collide, a direct label */}
        <circle cx={x(last.t)} cy={y(last.p95)} r={4} fill="var(--series-secondary)" stroke="var(--surface)" strokeWidth={2} />
        <circle cx={x(last.t)} cy={y(last.p50)} r={4} fill="var(--series-primary)" stroke="var(--surface)" strokeWidth={2} />
        {labelsFit && (
          <>
            <text className="direct-label" x={x(last.t) + 9} y={y(last.p95) + 4}>p95</text>
            <text className="direct-label" x={x(last.t) + 9} y={y(last.p50) + 4}>p50</text>
          </>
        )}

        {hovered && (
          <g pointerEvents="none">
            <line className="grid" style={{ stroke: 'var(--border-strong)' }} x1={x(hovered.t)} x2={x(hovered.t)} y1={MARGIN.top} y2={MARGIN.top + plotH} />
            <circle cx={x(hovered.t)} cy={y(hovered.p95)} r={4} fill="var(--series-secondary)" stroke="var(--surface)" strokeWidth={2} />
            <circle cx={x(hovered.t)} cy={y(hovered.p50)} r={4} fill="var(--series-primary)" stroke="var(--surface)" strokeWidth={2} />
          </g>
        )}
      </svg>

      {hovered && (
        <div className="tooltip" role="status" style={{ left: tooltipLeft, top: 4 }}>
          <div className="t-title">{formatDateTime(new Date(hovered.t - bucketMs / 2).toISOString())}</div>
          <div className="t-row"><span><i style={{ background: 'var(--series-primary)' }} />p50</span><b>{formatLatency(hovered.p50)}</b></div>
          <div className="t-row"><span><i style={{ background: 'var(--series-secondary)' }} />p95</span><b>{formatLatency(hovered.p95)}</b></div>
          <div className="t-title" style={{ marginTop: 4, marginBottom: 0 }}>{hovered.checks} checks</div>
        </div>
      )}

      <details className="table-view">
        <summary>View as table</summary>
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr><th>Time</th><th>p50</th><th>p95</th><th>Checks</th></tr>
            </thead>
            <tbody>
              {[...data].reverse().map((d) => (
                <tr key={d.t}>
                  <td>{formatDateTime(new Date(d.t - bucketMs / 2).toISOString())}</td>
                  <td>{formatLatency(d.p50)}</td>
                  <td>{formatLatency(d.p95)}</td>
                  <td>{d.checks}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
