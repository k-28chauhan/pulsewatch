/** Small formatting helpers. All pure functions, so they are trivial to test. */

export function formatLatency(ms: number | null | undefined): string {
  if (ms == null) return '—';
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const seconds = ms / 1000;
  return `${Number(seconds < 10 ? seconds.toFixed(2) : seconds.toFixed(1))} s`; // Number() drops trailing zeros: 5.00 -> 5
}

/** 100 -> "100%", 99.941 -> "99.94%", 99.9 -> "99.9%". null means "no data". */
export function formatPercent(value: number | null | undefined): string {
  if (value == null) return '—';
  if (value === 100) return '100%';
  const rounded = Math.round(value * 100) / 100;
  return `${rounded}%`;
}

/** 252 -> "4m 12s", 7380 -> "2h 3m", 60 -> "1m", 3600 -> "1h". Two units at most, and no "0s" tails. */
export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.round(totalSeconds));
  const join = (big: string, small: string, smallValue: number) => (smallValue === 0 ? big : `${big} ${smallValue}${small}`);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return join(`${minutes}m`, 's', seconds % 60);
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return join(`${hours}h`, 'm', minutes % 60);
  const days = Math.floor(hours / 24);
  return join(`${days}d`, 'h', hours % 24);
}

const relative = new Intl.RelativeTimeFormat('en', { numeric: 'auto', style: 'short' });

/** "12 sec. ago", "3 min. ago", "2 hr. ago", "yesterday". `now` is a parameter so tests do not depend on the clock. */
export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return 'never';
  const diffSeconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(diffSeconds);
  if (abs < 5) return 'just now';
  if (abs < 60) return relative.format(diffSeconds, 'second');
  if (abs < 3600) return relative.format(Math.round(diffSeconds / 60), 'minute');
  if (abs < 86400) return relative.format(Math.round(diffSeconds / 3600), 'hour');
  return relative.format(Math.round(diffSeconds / 86400), 'day');
}

const dateTime = new Intl.DateTimeFormat('en', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : '—';
}

const timeOnly = new Intl.DateTimeFormat('en', { hour: '2-digit', minute: '2-digit', hour12: false });
const dayOnly = new Intl.DateTimeFormat('en', { month: 'short', day: 'numeric' });

/** Axis label: a clock time for short ranges, a date for long ones. */
export function formatAxisTime(ms: number, rangeMs: number): string {
  const date = new Date(ms);
  return rangeMs > 36 * 3600_000 ? dayOnly.format(date) : timeOnly.format(date);
}

/** "https://api.example.com/health?x=1" -> "api.example.com/health?x=1" (the scheme adds noise in a list). */
export function shortUrl(url: string): string {
  return url.replace(/^https?:\/\//, '');
}
