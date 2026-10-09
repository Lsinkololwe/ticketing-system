import { beforeEach, describe, expect, it, vi } from 'vitest';
import { resetUnrecognisedReporting } from '../../lib/errors';
import { applyServerError, normaliseServerError, problemFromResponse, RestProblemError } from '../server-errors';

beforeEach(() => resetUnrecognisedReporting());

const gql = (extensions: Record<string, unknown>, message = 'boom') => ({ errors: [{ message, extensions }] });

describe('normaliseServerError: GraphQL', () => {
  it('maps a field-owned code onto its field with neutral copy', () => {
    const n = normaliseServerError(gql({ errorCode: 'SLUG_TAKEN', classification: 'FAILED_PRECONDITION', retryable: false }));
    expect(n.code).toBe('SLUG_TAKEN');
    expect(n.presentation).toBe('field');
    expect(n.fieldErrors).toEqual({ slug: 'That web address is taken. Try another.' });
  });
  it('maps violations, stripping input. and applying fieldMap', () => {
    const n = normaliseServerError(
      gql({ errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.title', constraint: 'NotBlank' }, { path: 'input.tiers[0].price', constraint: 'Positive' }] }),
      { fieldMap: { title: 'name' } }
    );
    expect(n.fieldErrors).toEqual({ name: 'Required', 'tiers.0.price': 'Must be more than zero' });
  });
  it('uses code copy at form level and never reads the message', () => {
    const n = normaliseServerError(gql({ errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION', retryable: false }, 'SECRET INTERNAL TEXT'));
    expect(n.presentation).toBe('form');
    expect(n.message).toBe('Those tickets have sold out.');
    expect(JSON.stringify(n)).not.toContain('SECRET');
  });
  it('unwraps Apollo-style wrappers (.error, graphQLErrors)', () => {
    const e = { graphQLErrors: [{ message: 'x', extensions: { errorCode: 'OTP_INVALID', classification: 'FAILED_PRECONDITION' } }] };
    expect(normaliseServerError({ error: e }).fieldErrors).toHaveProperty('code');
  });
  it('transient failures are toasts; retryAfterSeconds is appended', () => {
    const n = normaliseServerError(gql({ errorCode: 'OTP_RATE_LIMITED', classification: 'UNAVAILABLE', retryable: true, retryAfterSeconds: 30 }));
    expect(n.presentation).toBe('toast');
    expect(n.retryAfterSeconds).toBe(30);
    expect(n.message).toMatch(/Try again in 30 seconds\.$/);
    expect(n.retryable).toBe(true);
  });
  it('flags a revoked session', () => {
    expect(normaliseServerError(gql({ errorCode: 'TOKEN_REVOKED', classification: 'UNAUTHENTICATED' })).endsSession).toBe(true);
    expect(normaliseServerError(gql({ errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' })).endsSession).toBe(false);
  });
  it('an unknown code still renders honest generic copy', () => {
    vi.spyOn(console, 'warn').mockImplementation(() => undefined);
    const n = normaliseServerError(gql({ errorCode: 'BRAND_NEW_CODE' }));
    expect(n.message).toBe('Something went wrong. Please try again.');
    expect(n.presentation).toBe('form');
  });
  it('a network failure is a neutral retryable toast', () => {
    const n = normaliseServerError(new TypeError('Failed to fetch'));
    expect(n.classification).toBe('UNAVAILABLE');
    expect(n.retryable).toBe(true);
    expect(n.presentation).toBe('toast');
  });
});

describe('normaliseServerError: REST RFC 9457', () => {
  it('reads errorCode, fields and retryAfterSeconds from problem+json', () => {
    const n = normaliseServerError(
      new RestProblemError(409, { status: 409, errorCode: 'SLUG_TAKEN', fields: [{ path: 'slug', constraint: 'Pattern' }] })
    );
    expect(n.fieldErrors).toEqual({ slug: 'Wrong format' });
    const t = normaliseServerError(new RestProblemError(503, { status: 503, errorCode: 'PAYMENT_PROVIDER_UNAVAILABLE', retryAfterSeconds: 1, retryable: true }));
    expect(t.message).toMatch(/Try again in 1 second\.$/);
  });
  it('derives classification from HTTP status and Retry-After header', () => {
    const n = normaliseServerError(new RestProblemError(503, { status: 503 }, '12'));
    expect(n.classification).toBe('UNAVAILABLE');
    expect(n.retryAfterSeconds).toBe(12);
  });
  it('accepts a field error map and axios-shaped errors', () => {
    const n = normaliseServerError({ response: { status: 400, data: { errorCode: 'COMMAND_NOT_WELL_FORMED', errors: { 'input.email': 'Taken' } }, headers: {} } });
    expect(n.fieldErrors).toEqual({ email: 'Taken' });
  });
  it('problemFromResponse tolerates a non-JSON body', async () => {
    const e = await problemFromResponse(new Response('<html>', { status: 502, headers: { 'retry-after': '5' } }));
    expect(e).toBeInstanceOf(RestProblemError);
    expect(e.status).toBe(502);
    expect(normaliseServerError(e).retryAfterSeconds).toBe(5);
  });
});

describe('applyServerError', () => {
  const mkForm = () => ({ setError: vi.fn() });
  it('sets field errors with type server', () => {
    const form = mkForm();
    applyServerError(form as never, gql({ errorCode: 'SLUG_TAKEN', classification: 'FAILED_PRECONDITION' }));
    expect(form.setError).toHaveBeenCalledWith('slug', { type: 'server', message: 'That web address is taken. Try another.' });
  });
  it('sets root.server for form-level refusals', () => {
    const form = mkForm();
    applyServerError(form as never, gql({ errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' }));
    expect(form.setError).toHaveBeenCalledWith('root.server', { type: 'server', message: 'Those tickets have sold out.' });
  });
  it('toasts transient errors when notify is given, else banners them', () => {
    const form = mkForm();
    const notify = vi.fn();
    applyServerError(form as never, new TypeError('x'), { notify });
    expect(notify).toHaveBeenCalledWith(expect.objectContaining({ tone: 'error' }));
    expect(form.setError).not.toHaveBeenCalled();
    applyServerError(form as never, new TypeError('x'));
    expect(form.setError).toHaveBeenCalledWith('root.server', expect.anything());
  });
  it('reports session end', () => {
    const onSessionEnded = vi.fn();
    applyServerError(mkForm() as never, gql({ errorCode: 'TOKEN_REVOKED', classification: 'UNAUTHENTICATED' }), { onSessionEnded });
    expect(onSessionEnded).toHaveBeenCalled();
  });
});
