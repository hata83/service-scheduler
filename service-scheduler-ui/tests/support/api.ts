import { APIRequestContext, PlaywrightWorkerArgs, expect } from '@playwright/test';

/**
 * The API helpers of the Phase 1 suite. Every call carries the idempotency
 * key that the endpoint requires (ADR-008).
 */

/**
 * The idempotency keys carry a run id and a call counter. The cache lives 24
 * hours, so a fixed key would replay a stale session from an earlier suite
 * run. A new key starts a fresh operation, per design D8.
 */
const RUN_ID = Math.random().toString(36).slice(2, 8);
let callCounter = 0;
let clientCounter = 0;

let vinCounter = 100000;

/**
 * Adds run-unique digits to the default VIN base. The process counter restarts
 * on every suite run, so the bare base would reuse the VINs of an earlier
 * run. A reused VIN that is already booked on the test date yields a vehicle
 * conflict the test does not target. The stamp comes from the wall clock in
 * base 36, so two runs less than 36 ms apart share a stamp. Valid VIN charset
 * is ^[A-HJ-NPR-Z0-9]{17}$, which excludes I, O, and Q.
 */
const VIN_RUN_STAMP = BigInt(Date.now())
  .toString(36)
  .toUpperCase()
  .replace(/[^0-9A-HJ-NPR-Z]/g, 'A')
  .padStart(4, 'A')
  .slice(-4);

/**
 * Builds a valid 17-character ISO VIN string conforming to ^[A-HJ-NPR-Z0-9]{17}$.
 */
export function freshVin(prefix = `1HGCR2F${VIN_RUN_STAMP}`): string {
  vinCounter += 1;
  const suffix = String(vinCounter).padStart(6, '0');
  return `${prefix}${suffix}`;
}

/**
 * Builds a fresh E.164 phone number. Each run uses its own number, so the
 * suite never depends on a clean database.
 */
export function freshPhone(prefix = '+849'): string {
  const suffix = String(Date.now()).slice(-9);
  return `${prefix}${suffix}`;
}

/**
 * Builds the idempotency key for one operation.
 */
export function idempotencyKey(operation: string, phone: string): string {
  callCounter += 1;
  return `${operation}-${phone.replace('+', '')}-${RUN_ID}-${callCounter}`;
}

/**
 * Creates an API context with its own client address. The test profile trusts
 * the forwarded address, so each test owns its rate-limit budget and the
 * suite never trips the limit of another test.
 */
export async function apiContext(playwright: PlaywrightWorkerArgs['playwright'], baseURL: string): Promise<APIRequestContext> {
  clientCounter += 1;
  return playwright.request.newContext({
    baseURL,
    extraHTTPHeaders: { 'X-Forwarded-For': `203.0.113.${(clientCounter % 250) + 1}` }
  });
}

/**
 * Requests a purpose-scoped OTP code. The test profile echoes the code.
 */
export async function sendOtp(
  request: APIRequestContext,
  phone: string,
  type: 'LOGIN' | 'REGISTER'
): Promise<string> {
  const response = await request.post('/api/v1/auth/otp/send', {
    headers: { 'X-Idempotency-Key': idempotencyKey(`otp-send-${type}`, phone) },
    data: { phone, type }
  });

  expect(response.status(), await response.text()).toBe(202);
  const body = await response.json();
  expect(body.otpCode).toMatch(/^[0-9]{6}$/);
  return body.otpCode;
}

/**
 * Reads the session cookie value from a response.
 */
export function sessionCookie(response: Awaited<ReturnType<APIRequestContext['post']>>): string {
  const header = response.headers()['set-cookie'] ?? '';
  const value = header.split(';')[0].replace(/^jwt=/, '');
  expect(value).not.toBe('');
  return value;
}

let cachedCustomerCookie: string | null = null;

/**
 * Returns a cached customer session JWT, obtaining one via OTP login if not yet cached.
 */
export async function getCustomerSession(request: APIRequestContext): Promise<string> {
  if (cachedCustomerCookie) {
    return cachedCustomerCookie;
  }
  const phone = '+84900000004';
  const code = await sendOtp(request, phone, 'LOGIN');
  const verifyResp = await request.post('/api/v1/auth/otp/verify', {
    headers: { 'X-Idempotency-Key': idempotencyKey('shared-customer-login', phone) },
    data: { phone, code }
  });
  expect(verifyResp.status()).toBe(200);
  cachedCustomerCookie = sessionCookie(verifyResp);
  return cachedCustomerCookie;
}
