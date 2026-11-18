import { describe, expect, it } from 'vitest';
import { decodeJwtPayload } from './jwt';

const base64url = (value: object) => btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

describe('decodeJwtPayload', () => {
  it('reads the claims out of the middle part of a token', () => {
    const token = `header.${base64url({ sub: '42', email: 'ann@example.com' })}.signature`;

    expect(decodeJwtPayload(token)).toEqual({ sub: '42', email: 'ann@example.com' });
  });

  it('handles non-ASCII characters', () => {
    const payload = base64url({ email: 'zoë@example.com' });
    // btoa only handles Latin-1, so encode the UTF-8 bytes the way a real token does
    const utf8 = btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify({ email: 'zoë@example.com' })))).replace(/=+$/, '');
    expect(payload).toBeTruthy();
    expect(decodeJwtPayload(`h.${utf8}.s`)).toEqual({ email: 'zoë@example.com' });
  });

  it('returns null for garbage instead of throwing', () => {
    expect(decodeJwtPayload('not-a-token')).toBeNull();
    expect(decodeJwtPayload('a.%%%.c')).toBeNull();
  });
});
