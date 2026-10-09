import { describe, expect, it } from 'vitest';
import { hmacHex, open, parseEncKeys, randomId, safeEqual, seal, sha256Hex } from '../crypto';

const k = (b: number, kid = 'k1') => `${kid}:${Buffer.alloc(32, b).toString('base64')}`;

describe('crypto', () => {
  it('round-trips and never repeats an IV', () => {
    const keys = parseEncKeys(k(1));
    const a = seal('hello', keys, 'aad');
    const b = seal('hello', keys, 'aad');
    expect(a).not.toBe(b);
    expect(open(a, keys, 'aad')).toBe('hello');
    expect(a.startsWith('v1.k1.')).toBe(true);
  });
  it('binds ciphertext to its AAD and rejects tampering', () => {
    const keys = parseEncKeys(k(1));
    const s = seal('secret', keys, 'sess:a');
    expect(open(s, keys, 'sess:b')).toBeNull();
    const parts = s.split('.');
    parts[3] = Buffer.from('xxxxxxxx').toString('base64url');
    expect(open(parts.join('.'), keys, 'sess:a')).toBeNull();
    expect(open('garbage', keys, 'sess:a')).toBeNull();
  });
  it('rotates: first key encrypts, all keys decrypt, removed key stops working', () => {
    const old = parseEncKeys(k(1, 'old'));
    const sealedOld = seal('x', old, 'a');
    const rotated = parseEncKeys(`${k(2, 'new')},${k(1, 'old')}`);
    expect(open(sealedOld, rotated, 'a')).toBe('x');
    expect(seal('y', rotated, 'a').startsWith('v1.new.')).toBe(true);
    expect(open(sealedOld, parseEncKeys(k(2, 'new')), 'a')).toBeNull();
  });
  it('validates key material', () => {
    expect(() => parseEncKeys('')).toThrow();
    expect(() => parseEncKeys('nokid')).toThrow();
    expect(() => parseEncKeys(`k:${Buffer.alloc(16).toString('base64')}`)).toThrow(/32 bytes/);
  });
  it('helpers', () => {
    expect(randomId()).toHaveLength(43);
    expect(sha256Hex('a')).toHaveLength(64);
    expect(safeEqual('a', 'a')).toBe(true);
    expect(safeEqual('a', 'ab')).toBe(false);
    expect(hmacHex('x', Buffer.alloc(32, 1))).not.toBe(hmacHex('x', Buffer.alloc(32, 2)));
  });
});
