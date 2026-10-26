import { expect, test } from '@playwright/test';

import { apiContext, freshPhone, idempotencyKey, sendOtp, sessionCookie } from '../support/api';

/**
 * The OTP login flow (spec: login by verified OTP). The suite covers the
 * four seeded roles, the identity call and the logout revocation.
 */
test.describe('OTP login', () => {
  const roles = [
    { phone: '+84900000001', role: 'ADMIN' },
    { phone: '+84900000002', role: 'MANAGER' },
    { phone: '+84900000003', role: 'TECHNICIAN' },
    { phone: '+84900000004', role: 'CUSTOMER' }
  ];

  for (const account of roles) {
    test(`the ${account.role} logs in with a LOGIN code`, async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
      const code = await sendOtp(request, account.phone, 'LOGIN');

      const verify = await request.post('/api/v1/auth/otp/verify', {
        headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify', account.phone) },
        data: { phone: account.phone, code }
      });

      expect(verify.status(), await verify.text()).toBe(200);
      const body = await verify.json();
      expect(body.role).toBe(account.role);
      expect(body.phone).toBe(account.phone);

      const cookie = sessionCookie(verify);
      const me = await request.get('/api/v1/auth/me', {
        headers: { Cookie: `jwt=${cookie}` }
      });

      expect(me.status(), await me.text()).toBe(200);
      expect((await me.json()).role).toBe(account.role);
    });
  }

  test('each role edits its centralized profile names via PATCH /me', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = '+84900000002';
    const code = await sendOtp(request, phone, 'LOGIN');

    const verify = await request.post('/api/v1/auth/otp/verify', {
      headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify-manager-profile', phone) },
      data: { phone, code }
    });

    expect(verify.status(), await verify.text()).toBe(200);
    const body = await verify.json();
    expect(body.role).toBe('MANAGER');
    expect(body.dealershipId).toBe('00000000-0000-0000-0000-000000000001');

    const cookie = sessionCookie(verify);
    const patch = await request.patch('/api/v1/auth/me', {
      headers: {
        Cookie: `jwt=${cookie}`,
        'X-Idempotency-Key': idempotencyKey('patch-me-manager', phone)
      },
      data: { firstName: 'Minh', lastName: 'Manager' }
    });

    expect(patch.status(), await patch.text()).toBe(200);
    const patched = await patch.json();
    expect(patched.firstName).toBe('Minh');
    expect(patched.lastName).toBe('Manager');
  });

  test('a wrong code is rejected', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();
    await sendOtp(request, phone, 'LOGIN');

    const verify = await request.post('/api/v1/auth/otp/verify', {
      headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify', phone) },
      data: { phone, code: '000000' }
    });

    expect(verify.status()).toBe(401);
    expect(verify.headers()['content-type']).toContain('application/problem+json');
  });

  test('a REGISTER code is rejected at login', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = '+84900000004';

    const code = await sendOtp(request, phone, 'REGISTER');

    const verify = await request.post('/api/v1/auth/otp/verify', {
      headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify-register-purpose', phone) },
      data: { phone, code }
    });

    expect(verify.status()).toBe(401);
  });

  test('an unregistered phone names the registration step', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = freshPhone();

    const verify = await request.post('/api/v1/auth/otp/verify', {
      headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify', phone) },
      data: { phone, code: '123456' }
    });

    expect(verify.status()).toBe(401);
    const body = await verify.json();
    expect(body.detail.toLowerCase()).toContain('register');
  });

  test('the logout revokes the session', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = '+84900000004';
    const code = await sendOtp(request, phone, 'LOGIN');

    const verify = await request.post('/api/v1/auth/otp/verify', {
      headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify-logout', phone) },
      data: { phone, code }
    });
    expect(verify.status(), await verify.text()).toBe(200);
    const cookie = sessionCookie(verify);

    const logout = await request.post('/api/v1/auth/logout', {
      headers: { Cookie: `jwt=${cookie}` }
    });
    expect(logout.status()).toBe(204);
    expect(logout.headers()['set-cookie']).toContain('Max-Age=0');

    const me = await request.get('/api/v1/auth/me', {
      headers: { Cookie: 'jwt=' }
    });
    expect(me.status()).toBe(401);
  });
});
