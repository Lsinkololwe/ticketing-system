import {
  createCipheriv,
  createDecipheriv,
  createHash,
  createHmac,
  randomBytes,
  timingSafeEqual,
} from 'node:crypto';

/** 32 random bytes, base64url. Used for cookie secrets, state, nonce. */
export function randomId(bytes = 32): string {
  return randomBytes(bytes).toString('base64url');
}

export function sha256Hex(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

export function sha256Base64Url(value: string): string {
  return createHash('sha256').update(value).digest('base64url');
}

export function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

export interface EncKey {
  kid: string;
  key: Buffer;
}

/**
 * Parses `kid:base64key,kid2:base64key2`. The first key encrypts, every key decrypts
 * (rotation: prepend the new key, drop the old one after the longest session lifetime).
 */
export function parseEncKeys(raw: string): EncKey[] {
  const keys = raw
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((entry) => {
      const idx = entry.indexOf(':');
      if (idx < 1) throw new Error('BFF_ENC_KEYS entries must look like kid:base64key');
      const kid = entry.slice(0, idx);
      const key = Buffer.from(entry.slice(idx + 1), 'base64');
      if (key.length !== 32) throw new Error(`BFF_ENC_KEYS key "${kid}" must decode to 32 bytes`);
      return { kid, key };
    });
  if (keys.length === 0) throw new Error('BFF_ENC_KEYS is empty');
  return keys;
}

/**
 * AES-256-GCM. Wire format: `v1.<kid>.<iv>.<ciphertext>.<tag>` (base64url parts).
 * `aad` binds the ciphertext to its purpose and Redis key so a record cannot be replayed
 * under another key or type.
 */
export function seal(plaintext: string, keys: EncKey[], aad: string): string {
  const { kid, key } = keys[0];
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from(aad));
  const ct = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const tag = cipher.getAuthTag();
  return ['v1', kid, iv.toString('base64url'), ct.toString('base64url'), tag.toString('base64url')].join('.');
}

export function open(sealed: string, keys: EncKey[], aad: string): string | null {
  const parts = sealed.split('.');
  if (parts.length !== 5 || parts[0] !== 'v1') return null;
  const [, kid, iv, ct, tag] = parts;
  const key = keys.find((k) => k.kid === kid);
  if (!key) return null;
  try {
    const decipher = createDecipheriv('aes-256-gcm', key.key, Buffer.from(iv, 'base64url'));
    decipher.setAAD(Buffer.from(aad));
    decipher.setAuthTag(Buffer.from(tag, 'base64url'));
    return Buffer.concat([decipher.update(Buffer.from(ct, 'base64url')), decipher.final()]).toString('utf8');
  } catch {
    return null;
  }
}

/** Keyed hash for identifiers that must not appear in Redis keys or logs (contacts, IPs). */
export function hmacHex(value: string, secret: Buffer): string {
  return createHmac('sha256', secret).update(value).digest('hex');
}
