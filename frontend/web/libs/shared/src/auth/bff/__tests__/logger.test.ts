import { describe, expect, it } from 'vitest';
import { createLogger, redact } from '../logger';

const jwt = 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.c2lnbmF0dXJl';

describe('logger', () => {
  it('drops deny-listed fields and masks JWT/email/phone values', () => {
    const lines: string[] = [];
    const log = createLogger({ app: 't', sink: (l) => lines.push(l) });
    log.info('evt', { accessToken: jwt, refresh_token: 'r', code: 'c', handle: 'h', message: `user a.b@example.com +260971234567 ${jwt}`, ok: 1, nested: { idToken: jwt, email: 'x@y.zz' } });
    const out = lines[0];
    expect(out).not.toContain('eyJ');
    expect(out).not.toContain('example.com');
    expect(out).not.toContain('260971234567');
    expect(JSON.parse(out)).toMatchObject({ event: 'evt', app: 't', ok: 1, accessToken: '[redacted]' });
  });
  it('redacts Error messages and honours the level', () => {
    expect(redact(new Error(`bad ${jwt}`))).toEqual({ name: 'Error', message: 'bad [jwt]' });
    const lines: string[] = [];
    const log = createLogger({ app: 't', level: 'warn', sink: (l) => lines.push(l) });
    log.info('no');
    log.warn('yes');
    expect(lines).toHaveLength(1);
  });
});
