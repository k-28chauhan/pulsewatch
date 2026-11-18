import type { MonitorStatus } from '../api/types';

const LABELS: Record<MonitorStatus, string> = {
  UP: 'Up',
  DEGRADED: 'Failing',
  DOWN: 'Down',
  PAUSED: 'Paused',
  PENDING: 'Pending',
};

/** A coloured dot AND a word: colour alone is never the only signal. */
export function StatusBadge({ status }: { status: MonitorStatus }) {
  const key = status.toLowerCase();
  return (
    <span className={`status status-${key}`}>
      <span className={`dot dot-${key}`} aria-hidden="true" />
      {LABELS[status]}
    </span>
  );
}

export function statusLabel(status: MonitorStatus): string {
  return LABELS[status];
}
