import { describe, expect, it } from 'vitest';
import { formatDuration, formatLatency, formatPercent, formatRelative, shortUrl } from './format';

describe('formatLatency', () => {
  it('shows milliseconds under one second and seconds above', () => {
    expect(formatLatency(87)).toBe('87 ms');
    expect(formatLatency(999.6)).toBe('1000 ms');
    expect(formatLatency(1010)).toBe('1.01 s');
    expect(formatLatency(12400)).toBe('12.4 s');
  });

  it('drops trailing zeros', () => {
    expect(formatLatency(5000)).toBe('5 s');
    expect(formatLatency(2500)).toBe('2.5 s');
  });

  it('shows a dash when there is no value', () => {
    expect(formatLatency(null)).toBe('—');
    expect(formatLatency(undefined)).toBe('—');
  });
});

describe('formatPercent', () => {
  it('keeps 100 exact and trims other values to two decimals', () => {
    expect(formatPercent(100)).toBe('100%');
    expect(formatPercent(99.941)).toBe('99.94%');
    expect(formatPercent(99.9)).toBe('99.9%');
    expect(formatPercent(0)).toBe('0%');
  });

  it('treats null as "no data", never as 0 or 100', () => {
    expect(formatPercent(null)).toBe('—');
  });
});

describe('formatDuration', () => {
  it('uses at most two units and drops empty tails', () => {
    expect(formatDuration(45)).toBe('45s');
    expect(formatDuration(60)).toBe('1m');
    expect(formatDuration(252)).toBe('4m 12s');
    expect(formatDuration(3600)).toBe('1h');
    expect(formatDuration(7380)).toBe('2h 3m');
    expect(formatDuration(90_000)).toBe('1d 1h');
  });

  it('never goes negative', () => {
    expect(formatDuration(-5)).toBe('0s');
  });
});

describe('formatRelative', () => {
  const now = Date.parse('2026-11-10T12:00:00Z');

  it('describes the recent past in the largest sensible unit', () => {
    expect(formatRelative('2026-11-10T11:59:58Z', now)).toBe('just now');
    expect(formatRelative('2026-11-10T11:59:30Z', now)).toBe('30 sec. ago');
    expect(formatRelative('2026-11-10T11:55:00Z', now)).toBe('5 min. ago');
    expect(formatRelative('2026-11-10T09:00:00Z', now)).toBe('3 hr. ago');
    expect(formatRelative('2026-11-08T12:00:00Z', now)).toBe('2 days ago');
  });

  it('says "never" when there is no timestamp', () => {
    expect(formatRelative(null, now)).toBe('never');
  });
});

describe('shortUrl', () => {
  it('removes the scheme but keeps path and query', () => {
    expect(shortUrl('https://api.example.com/health?x=1')).toBe('api.example.com/health?x=1');
    expect(shortUrl('http://localhost:8081/status/500')).toBe('localhost:8081/status/500');
  });
});
