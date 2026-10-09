/**
 * Minimal QR Code encoder (ISO/IEC 18004): byte mode, error-correction level M, versions 1 to 10
 * (up to 213 bytes). Enough for ticket codes and validation URLs. Pure and dependency-free so it
 * renders on the server and in tests.
 */

/** [ec codewords per block, blocks in group 1, data cw per block in group 1, blocks in group 2, data cw per block in group 2] */
const M_TABLE: Record<number, [number, number, number, number, number]> = {
  1: [10, 1, 16, 0, 0],
  2: [16, 1, 28, 0, 0],
  3: [26, 1, 44, 0, 0],
  4: [18, 2, 32, 0, 0],
  5: [24, 2, 43, 0, 0],
  6: [16, 4, 27, 0, 0],
  7: [18, 4, 31, 0, 0],
  8: [22, 2, 38, 2, 39],
  9: [22, 3, 36, 2, 37],
  10: [26, 4, 43, 1, 44],
};
const ALIGN: Record<number, number[]> = {
  1: [],
  2: [6, 18],
  3: [6, 22],
  4: [6, 26],
  5: [6, 30],
  6: [6, 34],
  7: [6, 22, 38],
  8: [6, 24, 42],
  9: [6, 26, 46],
  10: [6, 28, 50],
};

const dataCapacity = (v: number) => {
  const [, b1, d1, b2, d2] = M_TABLE[v];
  return b1 * d1 + b2 * d2;
};

/** Largest payload (bytes) this encoder accepts. */
export const QR_MAX_BYTES = dataCapacity(10) - 3;

// ---- Reed-Solomon over GF(256), polynomial 0x11D ----
const EXP = new Array<number>(512);
const LOG = new Array<number>(256);
(() => {
  let x = 1;
  for (let i = 0; i < 255; i += 1) {
    EXP[i] = x;
    LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= 0x11d;
  }
  for (let i = 255; i < 512; i += 1) EXP[i] = EXP[i - 255];
})();
const gfMul = (a: number, b: number) => (a === 0 || b === 0 ? 0 : EXP[LOG[a] + LOG[b]]);

function rsGenerator(degree: number): number[] {
  let poly = [1];
  for (let i = 0; i < degree; i += 1) {
    const next = new Array<number>(poly.length + 1).fill(0);
    for (let j = 0; j < poly.length; j += 1) {
      next[j] ^= poly[j];
      next[j + 1] ^= gfMul(poly[j], EXP[i]);
    }
    poly = next;
  }
  return poly;
}
function rsRemainder(data: number[], degree: number): number[] {
  const gen = rsGenerator(degree);
  const rem = new Array<number>(degree).fill(0);
  for (const b of data) {
    const factor = b ^ (rem.shift() as number);
    rem.push(0);
    for (let i = 0; i < degree; i += 1) rem[i] ^= gfMul(gen[i + 1], factor);
  }
  return rem;
}

function utf8(text: string): number[] {
  return Array.from(new TextEncoder().encode(text));
}

function pickVersion(len: number): number {
  for (let v = 1; v <= 10; v += 1) {
    const countBits = v < 10 ? 8 : 16;
    if (4 + countBits + len * 8 <= dataCapacity(v) * 8) return v;
  }
  throw new RangeError(`QR payload of ${len} bytes is too long`);
}

function buildCodewords(bytes: number[], version: number): number[] {
  const bits: number[] = [];
  const put = (val: number, n: number) => {
    for (let i = n - 1; i >= 0; i -= 1) bits.push((val >>> i) & 1);
  };
  put(0b0100, 4);
  put(bytes.length, version < 10 ? 8 : 16);
  bytes.forEach((b) => put(b, 8));
  const cap = dataCapacity(version) * 8;
  put(0, Math.min(4, cap - bits.length));
  while (bits.length % 8) bits.push(0);
  for (let pad = 0xec; bits.length < cap; pad ^= 0xec ^ 0x11) put(pad, 8);
  const data: number[] = [];
  for (let i = 0; i < bits.length; i += 8) data.push(parseInt(bits.slice(i, i + 8).join(''), 2));

  const [ecLen, b1, d1, b2, d2] = M_TABLE[version];
  const blocks: number[][] = [];
  let pos = 0;
  for (let i = 0; i < b1 + b2; i += 1) {
    const n = i < b1 ? d1 : d2;
    blocks.push(data.slice(pos, pos + n));
    pos += n;
  }
  const ecs = blocks.map((b) => rsRemainder(b, ecLen));
  const out: number[] = [];
  const maxData = Math.max(d1, d2);
  for (let i = 0; i < maxData; i += 1) blocks.forEach((b) => i < b.length && out.push(b[i]));
  for (let i = 0; i < ecLen; i += 1) ecs.forEach((e) => out.push(e[i]));
  return out;
}

type Grid = boolean[][];

function bchFormat(data: number): number {
  let rem = data;
  for (let i = 0; i < 10; i += 1) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
  return ((data << 10) | rem) ^ 0x5412;
}
function bchVersion(v: number): number {
  let rem = v;
  for (let i = 0; i < 12; i += 1) rem = (rem << 1) ^ ((rem >>> 11) * 0x1f25);
  return (v << 12) | rem;
}

const MASKS: Array<(r: number, c: number) => boolean> = [
  (r, c) => (r + c) % 2 === 0,
  (r) => r % 2 === 0,
  (_r, c) => c % 3 === 0,
  (r, c) => (r + c) % 3 === 0,
  (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
  (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
  (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0,
  (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0,
];

function penalty(g: Grid): number {
  const n = g.length;
  let score = 0;
  const run = (line: boolean[]) => {
    let s = 0;
    let len = 1;
    for (let i = 1; i <= line.length; i += 1) {
      if (i < line.length && line[i] === line[i - 1]) len += 1;
      else {
        if (len >= 5) s += len - 2;
        len = 1;
      }
    }
    const str = line.map((b) => (b ? '1' : '0')).join('');
    for (const pat of ['10111010000', '00001011101']) {
      let at = str.indexOf(pat);
      while (at !== -1) {
        s += 40;
        at = str.indexOf(pat, at + 1);
      }
    }
    return s;
  };
  for (let i = 0; i < n; i += 1) {
    score += run(g[i]);
    score += run(g.map((row) => row[i]));
  }
  for (let r = 0; r < n - 1; r += 1)
    for (let c = 0; c < n - 1; c += 1) if (g[r][c] === g[r][c + 1] && g[r][c] === g[r + 1][c] && g[r][c] === g[r + 1][c + 1]) score += 3;
  const dark = g.reduce((a, row) => a + row.filter(Boolean).length, 0);
  score += Math.floor(Math.abs((dark * 100) / (n * n) - 50) / 5) * 10;
  return score;
}

/** Encode `text` and return the module matrix (true = dark). */
export function qrMatrix(text: string): boolean[][] {
  const bytes = utf8(text);
  const version = pickVersion(bytes.length);
  const n = 17 + version * 4;
  const modules: Grid = Array.from({ length: n }, () => new Array<boolean>(n).fill(false));
  const fn: Grid = Array.from({ length: n }, () => new Array<boolean>(n).fill(false));
  const set = (r: number, c: number, dark: boolean, isFn = true) => {
    if (r < 0 || c < 0 || r >= n || c >= n) return;
    modules[r][c] = dark;
    if (isFn) fn[r][c] = true;
  };

  // finder patterns + separators
  const finder = (r0: number, c0: number) => {
    for (let dr = -1; dr <= 7; dr += 1)
      for (let dc = -1; dc <= 7; dc += 1) {
        const inside = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
        const dark = inside && (dr === 0 || dr === 6 || dc === 0 || dc === 6 || (dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4));
        set(r0 + dr, c0 + dc, dark);
      }
  };
  finder(0, 0);
  finder(0, n - 7);
  finder(n - 7, 0);
  // timing
  for (let i = 8; i < n - 8; i += 1) {
    set(6, i, i % 2 === 0);
    set(i, 6, i % 2 === 0);
  }
  // alignment
  const pos = ALIGN[version];
  pos.forEach((r, i) =>
    pos.forEach((c, j) => {
      if ((i === 0 && j === 0) || (i === 0 && j === pos.length - 1) || (i === pos.length - 1 && j === 0)) return;
      for (let dr = -2; dr <= 2; dr += 1) for (let dc = -2; dc <= 2; dc += 1) set(r + dr, c + dc, Math.max(Math.abs(dr), Math.abs(dc)) !== 1);
    })
  );
  // dark module + reserve format areas
  set(n - 8, 8, true);
  for (let i = 0; i < 9; i += 1) {
    if (i === 6) continue; // keep the timing pattern
    set(8, i, false);
    set(i, 8, false);
  }
  for (let i = 0; i < 8; i += 1) {
    set(8, n - 1 - i, false);
    set(n - 1 - i, 8, false);
  }
  set(n - 8, 8, true);
  // version info
  if (version >= 7) {
    const info = bchVersion(version);
    for (let i = 0; i < 18; i += 1) {
      const bit = ((info >>> i) & 1) === 1;
      const a = Math.floor(i / 3);
      const b = (i % 3) + n - 11;
      set(a, b, bit);
      set(b, a, bit);
    }
  }

  // place data
  const cw = buildCodewords(bytes, version);
  const bitsArr: boolean[] = [];
  cw.forEach((b) => {
    for (let i = 7; i >= 0; i -= 1) bitsArr.push(((b >>> i) & 1) === 1);
  });
  let bi = 0;
  let up = true;
  for (let right = n - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let k = 0; k < n; k += 1) {
      const r = up ? n - 1 - k : k;
      for (let dc = 0; dc < 2; dc += 1) {
        const c = right - dc;
        if (!fn[r][c]) {
          modules[r][c] = bi < bitsArr.length ? bitsArr[bi] : false;
          bi += 1;
        }
      }
    }
    up = !up;
  }

  // choose the best mask
  let best: Grid | null = null;
  let bestScore = Infinity;
  for (let m = 0; m < 8; m += 1) {
    const g = modules.map((row) => row.slice());
    for (let r = 0; r < n; r += 1) for (let c = 0; c < n; c += 1) if (!fn[r][c] && MASKS[m](r, c)) g[r][c] = !g[r][c];
    const fmt = bchFormat((0b00 << 3) | m); // level M = 00
    const bit = (i: number) => ((fmt >>> i) & 1) === 1;
    for (let i = 0; i <= 5; i += 1) g[i][8] = bit(i);
    g[7][8] = bit(6);
    g[8][8] = bit(7);
    g[8][7] = bit(8);
    for (let i = 9; i < 15; i += 1) g[8][14 - i] = bit(i);
    for (let i = 0; i < 8; i += 1) g[8][n - 1 - i] = bit(i);
    for (let i = 8; i < 15; i += 1) g[n - 15 + i][8] = bit(i);
    g[n - 8][8] = true;
    const s = penalty(g);
    if (s < bestScore) {
      bestScore = s;
      best = g;
    }
  }
  return best as Grid;
}
