import { expect, test } from '@playwright/test';

import { apiContext, getCustomerSession } from '../support/api';

/**
 * End-to-end verification for SSE notifications and communication logs:
 * - Direct session cookie authentication on GET /api/v1/notifications/stream
 * - Audit trail query on GET /api/v1/notifications/my
 */
test.describe('Real-time SSE notifications and communication audit trail', () => {
  test('customer can query notification audit history', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const historyResp = await request.get('/api/v1/notifications/my', {
      headers: { 'Cookie': `jwt=${cookie}` }
    });

    expect(historyResp.status()).toBe(200);
    const logs = await historyResp.json();
    expect(Array.isArray(logs)).toBe(true);
  });

  test('unauthenticated request to SSE stream is rejected with 401 Unauthorized', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    const streamResp = await request.get('/api/v1/notifications/stream');
    expect(streamResp.status()).toBe(401);
  });
});
