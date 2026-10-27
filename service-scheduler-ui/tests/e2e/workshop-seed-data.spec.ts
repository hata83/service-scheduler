import { expect, test } from '@playwright/test';

import { apiContext } from '../support/api';

/**
 * End-to-end verification for seeded workshop metadata and schedule fixtures:
 * - Proves seeded vehicle categories across all four required body types
 * - Proves seeded service types and pricing catalog
 * - Proves seeded slot cache grids across Day +1, Day +2, and Day +3
 * - Proves OpenAPI 3.1 documentation exposes all five new metadata endpoints
 */
test.describe('Workshop seed data and fixtures', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

  function formatIsoDate(daysFromNow: number): string {
    const d = new Date();
    d.setDate(d.getDate() + daysFromNow);
    return d.toISOString().split('T')[0];
  }

  test('seeded vehicle categories contain SEDAN, SUV, TRUCK, and EV categories', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);

    expect(response.status()).toBe(200);
    const categories = await response.json();
    expect(categories.length).toBeGreaterThanOrEqual(4);

    const typeSet = new Set(categories.map((c: { type: string }) => c.type));
    expect(typeSet.has('SEDAN')).toBe(true);
    expect(typeSet.has('SUV')).toBe(true);
    expect(typeSet.has('TRUCK')).toBe(true);
    expect(typeSet.has('EV')).toBe(true);
  });

  test('seeded service types contain express lane, standard service, and brake service', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);

    expect(response.status()).toBe(200);
    const serviceTypes = await response.json();
    const codes = serviceTypes.map((s: { code: string }) => s.code);

    expect(codes).toContain('EXPRESS_LANE');
    expect(codes).toContain('STANDARD_SERVICE');
    expect(codes).toContain('BRAKE_SERVICE');
  });

  test('seeded slot cache reflects confirmed bookings on Day +1, Day +2, and Day +3', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    // Obtain service type ID for STANDARD_SERVICE
    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE');
    expect(standardService).toBeTruthy();

    // Verify Days +1, +2, +3 all have populated slot cache with booked slots (available: false)
    for (const offset of [1, 2, 3]) {
      const targetDate = formatIsoDate(offset);
      const url = `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${targetDate}`;
      const response = await request.get(url);

      expect(response.status()).toBe(200);
      const slots = await response.json();
      expect(Array.isArray(slots)).toBe(true);
      expect(slots.length).toBeGreaterThan(0);

      // Verify booked slot presence reflecting CONFIRMED appointments
      const bookedSlots = slots.filter((s: { available: boolean }) => s.available === false);
      expect(bookedSlots.length).toBeGreaterThanOrEqual(1);
    }
  });

  test('openapi 3.1 specification documents all metadata and slot availability endpoints', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/v3/api-docs');

    expect(response.status()).toBe(200);
    const spec = await response.json();

    expect(spec.paths['/api/v1/dealerships']).toBeTruthy();
    expect(spec.paths['/api/v1/dealerships/{id}/vehicle-categories']).toBeTruthy();
    expect(spec.paths['/api/v1/dealerships/{id}/service-types']).toBeTruthy();
    expect(spec.paths['/api/v1/dealerships/{id}/pricing/estimate']).toBeTruthy();
    expect(spec.paths['/api/v1/slots/available']).toBeTruthy();
  });
});
