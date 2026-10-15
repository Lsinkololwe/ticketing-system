import { describe, expect, it, vi } from 'vitest';
import { sendToGrant } from './media';

describe('sendToGrant', () => {
  const file = new Blob([new Uint8Array(6 * 1048576)], { type: 'image/png' });

  it('sends the file with the method and the signed headers, without the ones the browser sets', async () => {
    const send = vi.fn().mockResolvedValue({ ok: true, status: 200 });

    await sendToGrant(
      {
        uploadUrl: 'https://bucket.example/media/org/1-poster.png?X-Amz-Signature=abc',
        method: 'PUT',
        headers: [
          { name: 'Content-Type', value: 'image/png' },
          { name: 'Content-Length', value: '6291456' },
          { name: 'host', value: 'bucket.example' },
          { name: 'x-amz-server-side-encryption', value: 'AES256' },
        ],
      },
      file,
      send as unknown as typeof fetch
    );

    expect(send).toHaveBeenCalledTimes(1);
    const [url, init] = send.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('X-Amz-Signature=abc');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe(file);
    const headers = init.headers as Headers;
    expect(headers.get('content-type')).toBe('image/png');
    expect(headers.get('x-amz-server-side-encryption')).toBe('AES256');
    expect(headers.has('content-length')).toBe(false);
    expect(headers.has('host')).toBe(false);
  });

  it('turns a refusal by the store into an error carrying its status, and sends no credentials of its own', async () => {
    const send = vi.fn().mockResolvedValue({ ok: false, status: 403 });

    await expect(sendToGrant({ uploadUrl: 'https://bucket.example/x', method: 'PUT', headers: [] }, file, send as unknown as typeof fetch))
      .rejects.toThrow('(403)');
    const init = send.mock.calls[0][1] as RequestInit;
    expect(init.credentials).toBeUndefined();
  });
});
