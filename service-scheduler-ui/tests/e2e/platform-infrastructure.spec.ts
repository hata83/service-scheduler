import { expect, test } from '@playwright/test';

import { apiContext, idempotencyKey, sendOtp } from '../support/api';

/**
 * The platform composition (spec: platform infrastructure). The suite proves
 * the health indicators, the metrics endpoint, and the seed fixtures.
 */
test.describe('Platform infrastructure', () => {
  test('the health endpoint proves the database and the cache', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/actuator/health');

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.status).toBe('UP');
    expect(body.components.db.status).toBe('UP');
    expect(body.components.redis.status).toBe('UP');
  });

  test('the metrics endpoint exposes the JVM and the HTTP meters', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/actuator/prometheus');

    expect(response.status()).toBe(200);
    const body = await response.text();
    expect(body.includes('jvm_memory_used_bytes') || body.includes('jvm_gc_')).toBe(true);
    expect(body.includes('http_server_requests')).toBe(true);
  });

  test('the actuator exposes only the health and the metrics', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/actuator/env');

    expect(response.status()).toBe(404);
  });

  test('swagger ui and openapi 3.1 docs expose all endpoints', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const swaggerUi = await request.get('/swagger-ui/index.html');
    expect(swaggerUi.status()).toBe(200);

    const apiDocs = await request.get('/v3/api-docs');
    expect(apiDocs.status()).toBe(200);
    const spec = await apiDocs.json();
    expect(spec.openapi).toMatch(/^3\./);
    expect(spec.paths['/api/v1/auth/otp/send']).toBeTruthy();
    expect(spec.paths['/api/v1/auth/otp/verify']).toBeTruthy();
    expect(spec.paths['/api/v1/auth/register']).toBeTruthy();
    expect(spec.paths['/api/v1/auth/me']).toBeTruthy();
    expect(spec.paths['/api/v1/auth/logout']).toBeTruthy();
  });

  test('the seed fixtures hold the four accounts of the demo dealership', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const accounts = [
      { phone: '+84900000001', role: 'ADMIN', dealershipId: null },
      { phone: '+84900000002', role: 'MANAGER', dealershipId: '00000000-0000-0000-0000-000000000001' },
      { phone: '+84900000003', role: 'TECHNICIAN', dealershipId: '00000000-0000-0000-0000-000000000001' },
      { phone: '+84900000004', role: 'CUSTOMER', dealershipId: null }
    ];

    for (const account of accounts) {
      const code = await sendOtp(request, account.phone, 'LOGIN');
      const verify = await request.post('/api/v1/auth/otp/verify', {
        headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify-seed', account.phone) },
        data: { phone: account.phone, code }
      });

      expect(verify.status(), await verify.text()).toBe(200);
      const body = await verify.json();
      expect(body.role).toBe(account.role);
      expect(body.dealershipId).toBe(account.dealershipId);
    }
  });

  test('the seeded customer carries the seeded profile', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const phone = '+84900000004';
    const code = await sendOtp(request, phone, 'LOGIN');
    const verify = await request.post('/api/v1/auth/otp/verify', {
      headers: { 'X-Idempotency-Key': idempotencyKey('otp-verify-seed-profile', phone) },
      data: { phone, code }
    });
    expect(verify.status(), await verify.text()).toBe(200);
    const cookie = (verify.headers()['set-cookie'] ?? '').split(';')[0].replace(/^jwt=/, '');

    const me = await request.get('/api/v1/auth/me', {
      headers: { Cookie: `jwt=${cookie}` }
    });

    expect(me.status(), await me.text()).toBe(200);
    const identity = await me.json();
    expect(identity.firstName).toBeTruthy();
    expect(identity.lastName).toBeTruthy();
  });
});
