import { spawn, type ChildProcess } from 'node:child_process';
import net from 'node:net';
import path from 'node:path';
import fs from 'node:fs';
import os from 'node:os';
import https from 'node:https';
import http from 'node:http';
import { execFileSync } from 'node:child_process';
import type { BffApp } from '../../libs/shared/src/auth/bff/config';
import { HARNESS_ENC_KEYS } from './session';

export interface AppTarget {
  /** Directory name under apps/. */
  dir: 'ticketing' | 'organization-admin' | 'admin';
  bff: BffApp;
}
export const APPS: Record<string, AppTarget> = {
  ticketing: { dir: 'ticketing', bff: 'buyer' },
  'organization-admin': { dir: 'organization-admin', bff: 'organizer' },
  admin: { dir: 'admin', bff: 'admin' },
};

export const freePort = () =>
  new Promise<number>((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, '127.0.0.1', () => {
      const p = (s.address() as net.AddressInfo).port;
      s.close(() => resolve(p));
    });
    s.on('error', reject);
  });


/**
 * TEST-ONLY TLS front for `HARNESS_MODE=start`. The BFF refuses a non-https APP_URL when
 * NODE_ENV=production, so the production server runs on a plain port and this https server (self-signed
 * certificate for localhost, made with openssl, in a temp dir) forwards to it with
 * `x-forwarded-proto: https`. Browsers must ignore the certificate error (the harness config sets
 * `ignoreHTTPSErrors`). Nothing here ships with an app.
 */
async function startTlsFront(targetPort: number): Promise<{ url: string; stop(): Promise<void> }> {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'harness-tls-'));
  const key = path.join(dir, 'key.pem');
  const cert = path.join(dir, 'cert.pem');
  execFileSync('openssl', ['req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', key, '-out', cert, '-days', '2', '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1'], { stdio: 'ignore' });
  const port = await freePort();
  const server = https.createServer({ key: fs.readFileSync(key), cert: fs.readFileSync(cert) }, (req, res) => {
    const up = http.request({ host: '127.0.0.1', port: targetPort, path: req.url, method: req.method, headers: { ...req.headers, 'x-forwarded-proto': 'https', 'x-forwarded-host': req.headers.host ?? '' } }, (r) => {
      res.writeHead(r.statusCode ?? 502, r.headers);
      r.pipe(res);
    });
    up.on('error', () => {
      res.writeHead(502);
      res.end();
    });
    req.pipe(up);
  });
  await new Promise<void>((r) => server.listen(port, '127.0.0.1', r));
  return {
    url: `https://localhost:${port}`,
    stop: () =>
      new Promise<void>((r) => {
        server.close(() => r());
        server.closeAllConnections?.();
        fs.rmSync(dir, { recursive: true, force: true });
      }),
  };
}

export interface RunningApp {
  url: string;
  port: number;
  log: string;
  stop(): Promise<void>;
}

/**
 * Starts the app (`next dev`, or `next start` after a build when HARNESS_MODE=start) on a free
 * port with every upstream pointed at the fake upstream and the session store at Redis.
 * `extraEnv` wins over the defaults.
 */
export async function startApp(target: AppTarget, o: { upstreamUrl: string; redisUrl: string; extraEnv?: Record<string, string> }): Promise<RunningApp> {
  const port = await freePort();
  const cwd = path.resolve(__dirname, '../../apps', target.dir);
  const mode = process.env.HARNESS_MODE === 'start' ? 'start' : 'dev';
  if (mode === 'start' && !fs.existsSync(path.join(cwd, '.next', 'BUILD_ID'))) throw new Error(`HARNESS_MODE=start needs a build: run \`cd apps/${target.dir} && npx next build\` first (not nx: it writes to dist, next start reads .next)`);
  const tls = mode === 'start' ? await startTlsFront(port) : null;
  const url = tls ? tls.url : `http://localhost:${port}`;
  const env: Record<string, string> = {
    ...(process.env as Record<string, string>),
    NODE_ENV: mode === 'start' ? 'production' : 'development',
    APP_URL: url,
    REDIS_URL: o.redisUrl,
    BFF_ENC_KEYS: HARNESS_ENC_KEYS,
    GRAPHQL_URL: `${o.upstreamUrl}/graphql`,
    API_BASE_URL: o.upstreamUrl,
    IDENTITY_BASE_URL: o.upstreamUrl,
    IDENTITY_TOKEN_URL: `${o.upstreamUrl}/protocol/openid-connect/token`,
    // Nothing talks to Keycloak in this harness; the issuer only has to be a well-formed URL.
    KEYCLOAK_ISSUER: `${o.upstreamUrl}/realms/harness`,
    KEYCLOAK_CLIENT_SECRET: 'harness',
    IDENTITY_CLIENT_ID: 'harness',
    IDENTITY_CLIENT_SECRET: 'harness',
    NEXT_TELEMETRY_DISABLED: '1',
    ...o.extraEnv,
  };
  const child: ChildProcess = spawn('npx', ['next', mode, '--port', String(port)], { cwd, env, stdio: ['ignore', 'pipe', 'pipe'], detached: true });
  let log = '';
  const take = (b: Buffer) => {
    log += b.toString();
    if (log.length > 200_000) log = log.slice(-100_000);
  };
  child.stdout?.on('data', take);
  child.stderr?.on('data', take);
  let exited = false;
  child.on('exit', () => (exited = true));

  const deadline = Date.now() + 240_000;
  for (;;) {
    if (exited) {
      await tls?.stop();
      throw new Error(`app exited before it was ready:\n${log.slice(-3000)}`);
    }
    if (Date.now() > deadline) throw new Error(`app not ready in time:\n${log.slice(-3000)}`);
    try {
      // Any HTTP answer means the server is up; the first hit also compiles the home route.
      await fetch(`http://localhost:${port}/`, { redirect: 'manual' });
      break;
    } catch {
      /* not listening yet */
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  return {
    url,
    port,
    get log() {
      return log;
    },
    stop: () =>
      new Promise<void>((resolve) => {
        void tls?.stop();
        if (exited) return resolve();
        child.on('exit', () => resolve());
        try {
          // The whole group: `npx` forks the real next process.
          process.kill(-(child.pid as number), 'SIGTERM');
        } catch {
          child.kill('SIGTERM');
        }
        setTimeout(() => {
          try {
            process.kill(-(child.pid as number), 'SIGKILL');
          } catch {
            /* gone */
          }
          resolve();
        }, 8000);
      }),
  } as RunningApp;
}
