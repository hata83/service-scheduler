import { expect, test } from '@playwright/test';

import { apiContext } from '../support/api';

/**
 * Admin UI dashboard and tracing observability tests.
 * Proves the admin dashboard static assets, redirect, and HTTP exchange tracing.
 */
test.describe('Admin operations and tracing dashboard', () => {
  test('the admin endpoint redirects to index.html and serves the dashboard', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/admin', { maxRedirects: 5 });

    expect(response.status()).toBe(200);
    const html = await response.text();
    expect(html).toContain('Unified Service Scheduler — Admin Dashboard');
    expect(html).toContain('Database Status');
    expect(html).toContain('Redis Cache');
    expect(html).toContain('Live HTTP Request Tracing');
  });

  test('the actuator exposes httpexchanges for tracing request flows', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/actuator/httpexchanges');

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(Array.isArray(body.exchanges)).toBe(true);
  });
});
