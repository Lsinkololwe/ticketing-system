/**
 * Structured, PII-free logging. Field NAMES on the deny-list are dropped and string VALUES that
 * look like JWTs, emails or phone numbers are masked, so a careless `log.info('x', { err })` can
 * not leak a token. Callers log event names and error codes, never values.
 */
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'silent';
const ORDER: Record<LogLevel, number> = { debug: 10, info: 20, warn: 30, error: 40, silent: 100 };

const DENY_KEYS =
  /(token|secret|password|authorization|cookie|code|verifier|handle|login_?hint|contact|email|phone|msisdn|otp|proof|nonce|state|assertion|sealed)/i;
const JWT_RE = /eyJ[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]{5,}\.[A-Za-z0-9_-]*/g;
const EMAIL_RE = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi;
const PHONE_RE = /\+?\d[\d\s().-]{8,}\d/g;

export function redactString(s: string): string {
  return s.replace(JWT_RE, '[jwt]').replace(EMAIL_RE, '[email]').replace(PHONE_RE, '[number]');
}

export function redact(value: unknown, depth = 0): unknown {
  if (value == null) return value;
  if (typeof value === 'string') return redactString(value);
  if (typeof value !== 'object') return value;
  if (depth > 4) return '[depth]';
  if (value instanceof Error) return { name: value.name, message: redactString(value.message) };
  if (Array.isArray(value)) return value.slice(0, 20).map((v) => redact(v, depth + 1));
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
    out[k] = DENY_KEYS.test(k) ? '[redacted]' : redact(v, depth + 1);
  }
  return out;
}

export interface Logger {
  debug(event: string, fields?: Record<string, unknown>): void;
  info(event: string, fields?: Record<string, unknown>): void;
  warn(event: string, fields?: Record<string, unknown>): void;
  error(event: string, fields?: Record<string, unknown>): void;
}

export type LogSink = (line: string) => void;

export function createLogger(opts: { app: string; level?: LogLevel; sink?: LogSink }): Logger {
  const min = ORDER[opts.level ?? 'info'];
  const sink: LogSink = opts.sink ?? ((l) => process.stdout.write(l + '\n'));
  const emit = (level: Exclude<LogLevel, 'silent'>) => (event: string, fields: Record<string, unknown> = {}) => {
    if (ORDER[level] < min) return;
    sink(JSON.stringify({ ts: new Date().toISOString(), level, app: opts.app, event, ...(redact(fields) as object) }));
  };
  return { debug: emit('debug'), info: emit('info'), warn: emit('warn'), error: emit('error') };
}

export const silentLogger: Logger = { debug() {}, info() {}, warn() {}, error() {} };
