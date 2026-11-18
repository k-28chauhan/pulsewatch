import { describe, expect, it } from 'vitest';
import { DEFAULT_VALUES, toRequest, validateMonitor, valuesFromMonitor, type MonitorFormValues } from './monitorValidation';

const valid: MonitorFormValues = { ...DEFAULT_VALUES, name: 'Homepage', url: 'https://example.com' };

describe('validateMonitor', () => {
  it('accepts a minimal valid form', () => {
    expect(validateMonitor(valid)).toEqual({});
  });

  it('requires a name and a URL', () => {
    const errors = validateMonitor({ ...valid, name: '  ', url: '' });

    expect(errors.name).toBeDefined();
    expect(errors.url).toBeDefined();
  });

  it('accepts only http and https URLs, like the server', () => {
    expect(validateMonitor({ ...valid, url: 'ftp://example.com' }).url).toMatch(/http/);
    expect(validateMonitor({ ...valid, url: 'example.com' }).url).toBeDefined();
    expect(validateMonitor({ ...valid, url: 'http://localhost:8081/status/200' })).toEqual({});
  });

  it('enforces the same limits as the server', () => {
    expect(validateMonitor({ ...valid, intervalSeconds: '9' }).intervalSeconds).toBeDefined();
    expect(validateMonitor({ ...valid, timeoutMs: '99' }).timeoutMs).toBeDefined();
    expect(validateMonitor({ ...valid, timeoutMs: '60001' }).timeoutMs).toBeDefined();
    expect(validateMonitor({ ...valid, expectedStatus: '99' }).expectedStatus).toBeDefined();
    expect(validateMonitor({ ...valid, expectedStatus: '600' }).expectedStatus).toBeDefined();
    expect(validateMonitor({ ...valid, failureThreshold: '0' }).failureThreshold).toBeDefined();
    expect(validateMonitor({ ...valid, latencyThresholdMs: '0' }).latencyThresholdMs).toBeDefined();
  });

  it('requires the timeout to be shorter than the interval, naming the limit', () => {
    const errors = validateMonitor({ ...valid, intervalSeconds: '10', timeoutMs: '10000' });

    expect(errors.timeoutMs).toBe('Must be shorter than the interval (10000 ms)');
    expect(validateMonitor({ ...valid, intervalSeconds: '10', timeoutMs: '9999' })).toEqual({});
  });

  it('rejects non-numbers', () => {
    expect(validateMonitor({ ...valid, intervalSeconds: 'abc' }).intervalSeconds).toBeDefined();
    expect(validateMonitor({ ...valid, timeoutMs: '5e3' }).timeoutMs).toBeDefined();
  });

  it('treats an empty slow-response threshold as "off"', () => {
    expect(validateMonitor({ ...valid, latencyThresholdMs: '' })).toEqual({});
  });
});

describe('toRequest', () => {
  it('converts text fields to numbers and an empty threshold to null', () => {
    expect(toRequest({ ...valid, name: ' Homepage ', url: ' https://example.com ' })).toEqual({
      name: 'Homepage',
      url: 'https://example.com',
      method: 'GET',
      expectedStatus: 200,
      intervalSeconds: 60,
      timeoutMs: 5000,
      latencyThresholdMs: null,
      failureThreshold: 3,
    });
  });

  it('keeps a given slow-response threshold', () => {
    expect(toRequest({ ...valid, latencyThresholdMs: '800' }).latencyThresholdMs).toBe(800);
  });
});

describe('valuesFromMonitor', () => {
  it('fills the form from an existing monitor', () => {
    const values = valuesFromMonitor({
      id: 1, name: 'API', url: 'https://api.example.com', method: 'HEAD', expectedStatus: 204, intervalSeconds: 300,
      timeoutMs: 2000, latencyThresholdMs: null, failureThreshold: 2, enabled: true, nextCheckAt: '', createdAt: '', updatedAt: '',
    });

    expect(values).toMatchObject({ name: 'API', method: 'HEAD', expectedStatus: '204', intervalSeconds: '300', latencyThresholdMs: '', failureThreshold: '2' });
  });
});
