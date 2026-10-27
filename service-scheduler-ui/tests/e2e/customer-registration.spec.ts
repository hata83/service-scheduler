import { expect, test } from '@playwright/test';

import { apiContext, freshPhone, idempotencyKey, sendOtp, sessionCookie } from '../support/api';

/**
 * The customer registration flow (spec: one-shot registration).
 *
 * The scenario chain is: typed send, one-shot register with the echoed OTP,
 * the logged-in identity through me, and an editable profile.
 */
test.describe('Customer registration', () => {
  test('a new customer registers, is logged in, and edits the profile', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();
    const firstName = 'Tai';
    const lastName = 'Le';

    // 1. The typed send delivers a REGISTER-purpose code.
    const code = await sendOtp(request, phone, 'REGISTER');

    // 2. The one-shot register creates the account and the session.
    const register = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': idempotencyKey('register', phone) },
      data: { phone, firstName, lastName, code }
    });

    expect(register.status(), await register.text()).toBe(201);
    const registered = await register.json();
    expect(registered.role).toBe('CUSTOMER');
    expect(registered.phone).toBe(phone);
    expect(registered.id).toMatch(/^[0-9a-f-]{36}$/);

    // 3. The session cookie carries the customer identity.
    const cookie = sessionCookie(register);
    const me = await request.get('/api/v1/auth/me', {
      headers: { Cookie: `jwt=${cookie}` }
    });

    expect(me.status(), await me.text()).toBe(200);
    const identity = await me.json();
    expect(identity.id).toBe(registered.id);
    expect(identity.role).toBe('CUSTOMER');
    expect(identity.phone).toBe(phone);
    expect(identity.firstName).toBe(firstName);
    expect(identity.lastName).toBe(lastName);

    // 4. The customer edits the profile names and the email.
    const patch = await request.patch('/api/v1/auth/me', {
      headers: {
        Cookie: `jwt=${cookie}`,
        'X-Idempotency-Key': idempotencyKey('patch-me', phone)
      },
      data: { firstName: 'My', lastName: 'Nguyen', email: 'my.nguyen@example.com' }
    });

    expect(patch.status(), await patch.text()).toBe(200);
    const updated = await patch.json();
    expect(updated.firstName).toBe('My');
    expect(updated.lastName).toBe('Nguyen');
    expect(updated.email).toBe('my.nguyen@example.com');

    // 5. The me call returns the same names.
    const afterPatch = await request.get('/api/v1/auth/me', {
      headers: { Cookie: `jwt=${cookie}` }
    });

    expect(afterPatch.status()).toBe(200);
    const identityAfterPatch = await afterPatch.json();
    expect(identityAfterPatch.firstName).toBe('My');
    expect(identityAfterPatch.lastName).toBe('Nguyen');
  });

  test('a wrong code creates nothing', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();

    const register = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': idempotencyKey('register', phone) },
      data: { phone, firstName: 'Tai', lastName: 'Le', code: '000000' }
    });

    expect(register.status()).toBe(401);
    expect(register.headers()['content-type']).toContain('application/problem+json');
  });

  test('a role field is rejected', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();
    const code = await sendOtp(request, phone, 'REGISTER');

    const register = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': idempotencyKey('register', phone) },
      data: { phone, firstName: 'Tai', lastName: 'Le', code, role: 'ADMIN' }
    });

    expect(register.status()).toBe(403);
    expect(register.headers()['content-type']).toContain('application/problem+json');
  });

  test('a missing idempotency key is rejected', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();
    const code = await sendOtp(request, phone, 'REGISTER');

    const register = await request.post('/api/v1/auth/register', {
      data: { phone, firstName: 'Tai', lastName: 'Le', code }
    });

    expect(register.status()).toBe(400);
    expect(register.headers()['content-type']).toContain('application/problem+json');
  });

  test('a replayed key returns the same session', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();
    const code = await sendOtp(request, phone, 'REGISTER');
    const key = idempotencyKey('register', phone);
    const body = { phone, firstName: 'Tai', lastName: 'Le', code };

    const first = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': key },
      data: body
    });
    const second = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': key },
      data: body
    });

    expect(first.status()).toBe(201);
    expect(second.status()).toBe(201);
    const firstBody = await first.json();
    const secondBody = await second.json();
    expect(secondBody.id).toBe(firstBody.id);
  });
});
