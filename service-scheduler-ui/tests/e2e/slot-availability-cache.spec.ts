import { expect, test } from '@playwright/test';

import { apiContext } from '../support/api';

/**
 * End-to-end verification for cached slot availability:
 * - Reads candidate slot grid from Redis cache
 * - Identifies booked slots vs available capacity
 * - Fallbacks gracefully to empty list on cache miss
 * - Validates required query parameters
 */
test.describe('Cached slot availability', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

  function formatIsoDate(daysFromNow: number): string {
    const now = new Date();
    const hcmDateStr = now.toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
    const [yyyy, mm, dd] = hcmDateStr.split('-').map(Number);
    const target = new Date(Date.UTC(yyyy, mm - 1, dd + daysFromNow));
    return target.toISOString().split('T')[0];
  }

  test('slot grid returns cached intervals with availability indicators for Day +1', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    // Obtain Standard Service service type (has booked slot on Day +1)
    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE');
    expect(standardService).toBeTruthy();

    const tomorrow = formatIsoDate(1);
    const slotsUrl = `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${tomorrow}`;
    const slotsResp = await request.get(slotsUrl);

    expect(slotsResp.status()).toBe(200);
    const slots = await slotsResp.json();
    expect(Array.isArray(slots)).toBe(true);
    expect(slots.length).toBeGreaterThan(0);

    // Verify slot structure
    for (const slot of slots) {
      expect(slot.startTime).toBeTruthy();
      expect(slot.endTime).toBeTruthy();
      expect(typeof slot.available).toBe('boolean');
    }

    // Verify that the seeded Day +1 schedule contains a booked slot (available: false)
    const bookedSlot = slots.find((s: { available: boolean }) => s.available === false);
    expect(bookedSlot).toBeTruthy();

    // Verify that available slots exist (available: true)
    const availableSlot = slots.find((s: { available: boolean }) => s.available === true);
    expect(availableSlot).toBeTruthy();
  });

  test('unpopulated date returns empty array as cache fallback without error', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes[0];

    const farFuture = formatIsoDate(30);
    const slotsUrl = `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${farFuture}`;
    const slotsResp = await request.get(slotsUrl);

    expect(slotsResp.status()).toBe(200);
    const slots = await slotsResp.json();
    expect(Array.isArray(slots)).toBe(true);
    expect(slots.length).toBe(0);
  });

  test('missing query parameters returns 400 problem details', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/api/v1/slots/available');

    expect(response.status()).toBe(400);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const problem = await response.json();
    expect(problem.status).toBe(400);
  });
});
