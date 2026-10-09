import { describe, expect, it } from 'vitest';
import { QR_MAX_BYTES, qrMatrix } from './qr';

// 'aaaaaaaaaaaa' encoded at level M: identical module-for-module to a reference encoder, and the
// matrices for other lengths were decoded back to their text with an independent QR decoder.
const GOLDEN = ["1fc87f", "104241", "175e5d", "17555d", "17535d", "105141", "1fd57f", "001700", "17c57c", "081135", "076a8e", "0f1c8e", "19ca60", "001535", "1fc58e", "10578c", "175d63", "175434", "175b8c", "104f8c", "1fd962"];

const toHex = (m: boolean[][]) => m.map((r) => parseInt(r.map((b) => (b ? '1' : '0')).join(''), 2).toString(16).padStart(6, '0'));

describe('qrMatrix', () => {
  it('matches the reference encoding', () => {
    expect(toHex(qrMatrix('a'.repeat(12)))).toEqual(GOLDEN);
  });
  it('grows with the payload and keeps the finder patterns', () => {
    expect(qrMatrix('A').length).toBe(21);
    expect(qrMatrix('x'.repeat(40)).length).toBe(29);
    expect(qrMatrix('x'.repeat(60)).length).toBe(33);
    const m = qrMatrix('QR-TKT-1');
    for (const [r, c] of [[0, 0], [0, m.length - 7], [m.length - 7, 0]]) {
      expect(m[r][c]).toBe(true);
      expect(m[r + 3][c + 3]).toBe(true);
      expect(m[r + 1][c + 1]).toBe(false);
    }
  });
  it('is deterministic', () => {
    expect(qrMatrix('same')).toEqual(qrMatrix('same'));
  });
  it('rejects payloads that do not fit', () => {
    expect(() => qrMatrix('x'.repeat(QR_MAX_BYTES + 1))).toThrow(RangeError);
    expect(qrMatrix('x'.repeat(QR_MAX_BYTES)).length).toBe(57);
  });
});
