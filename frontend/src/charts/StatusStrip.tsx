import { useRef, useState } from 'react';
import type { ErrorRateBucket, Window } from '../api/types';
import { formatAxisTime, formatDateTime, formatPercent } from '../lib/format';
import { buildStripCells, type CellState, type StripCell } from './scales';

const WINDOW_MS = { '24h': 24 * 3600_000, '7d': 7 * 24 * 3600_000, '30d': 30 * 24 * 3600_000 } as const;
const BUCKET_MS = { '24h': 3600_000, '7d': 6 * 3600_000, '30d': 24 * 3600_000 } as const;

const STATE_LABEL: Record<CellState, string> = {
  ok: 'All checks passed',
  partial: 'Some checks failed',
  bad: 'Most checks failed',
  none: 'No data',
};

export function StatusStripLegend() {
  const items: [CellState, string][] = [['ok', 'All passed'], ['partial', 'Some failed'], ['bad', 'Mostly failed'], ['none', 'No data']];
  return (
    <div className="legend">
      {items.map(([state, label]) => (
        <span key={state}>
          <i className="sw" style={{ background: state === 'ok' ? 'var(--good)' : state === 'partial' ? 'var(--warning)' : state === 'bad' ? 'var(--critical)' : 'var(--border)' }} />
          {label}
        </span>
      ))}
    </div>
  );
}

function describe(cell: StripCell): string {
  const when = formatDateTime(new Date(cell.start).toISOString());
  if (cell.state === 'none') return `${when}: no checks`;
  return `${when}: ${cell.totalChecks} checks, ${cell.failedChecks} failed`;
}

interface Props {
  buckets: ErrorRateBucket[];
  window: Window;
  now?: number;
}

/**
 * One cell per time bucket, coloured by how many checks failed in it. The same idea as a status page's
 * uptime bar, but exact: hover or tab to a cell for the numbers, or open the table below.
 */
export function StatusStrip({ buckets, window, now = Date.now() }: Props) {
  const wrap = useRef<HTMLDivElement>(null);
  const [active, setActive] = useState<{ cell: StripCell; left: number } | null>(null);

  const windowMs = WINDOW_MS[window];
  const cells = buildStripCells(buckets, windowMs, BUCKET_MS[window], now);

  function show(cell: StripCell, element: HTMLElement) {
    const outer = wrap.current?.getBoundingClientRect();
    const rect = element.getBoundingClientRect();
    if (!outer) return;
    setActive({ cell, left: Math.min(Math.max(rect.left - outer.left + rect.width / 2 - 80, 0), Math.max(outer.width - 170, 0)) });
  }

  return (
    <div className="strip-wrap" ref={wrap}>
      <div className="strip" role="group" aria-label={`Check results over the last ${window}, one cell per ${BUCKET_MS[window] >= 86400_000 ? 'day' : BUCKET_MS[window] / 3600_000 + ' hours'}`}>
        {cells.map((cell) => (
          <button
            key={cell.start}
            type="button"
            className="strip-cell"
            data-state={cell.state}
            aria-label={`${describe(cell)}. ${STATE_LABEL[cell.state]}`}
            onPointerEnter={(e) => show(cell, e.currentTarget)}
            onFocus={(e) => show(cell, e.currentTarget)}
            onPointerLeave={() => setActive(null)}
            onBlur={() => setActive(null)}
          />
        ))}
      </div>
      <div className="strip-axis" aria-hidden="true">
        <span>{formatAxisTime(now - windowMs, windowMs)}</span>
        <span>now</span>
      </div>

      {active && (
        <div className="tooltip" role="status" style={{ left: active.left, top: -64 }}>
          <div className="t-title">{formatDateTime(new Date(active.cell.start).toISOString())}</div>
          {active.cell.state === 'none' ? (
            <div className="t-row"><span>No checks</span></div>
          ) : (
            <>
              <div className="t-row"><span>Checks</span><b>{active.cell.totalChecks}</b></div>
              <div className="t-row"><span>Failed</span><b>{active.cell.failedChecks} ({formatPercent(Math.round((active.cell.failedChecks / active.cell.totalChecks) * 1000) / 10)})</b></div>
            </>
          )}
        </div>
      )}

      <details className="table-view">
        <summary>View as table</summary>
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr><th>From</th><th>Checks</th><th>Failed</th><th>Result</th></tr>
            </thead>
            <tbody>
              {[...cells].reverse().filter((c) => c.totalChecks > 0).map((cell) => (
                <tr key={cell.start}>
                  <td>{formatDateTime(new Date(cell.start).toISOString())}</td>
                  <td>{cell.totalChecks}</td>
                  <td>{cell.failedChecks}</td>
                  <td>{STATE_LABEL[cell.state]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
