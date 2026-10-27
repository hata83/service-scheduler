import { expect, test } from '@playwright/test';

import { apiContext } from '../support/api';

/**
 * End-to-end verification for workshop metadata APIs:
 * - Dealership listing
 * - Vehicle categories by dealership (without VIN)
 * - Service types by dealership
 * - Instant pricing estimate calculation
 */
test.describe('Workshop metadata and pricing', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';
  const UNKNOWN_ID = '00000000-0000-0000-0000-ffffffffffff';

  test('dealerships list exposes active workshops with operational parameters', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/api/v1/dealerships');

    expect(response.status()).toBe(200);
    const dealerships = await response.json();
    expect(Array.isArray(dealerships)).toBe(true);
    expect(dealerships.length).toBeGreaterThanOrEqual(1);

    const demo = dealerships.find((d: { id: string }) => d.id === DEMO_DEALERSHIP_ID);
    expect(demo).toBeTruthy();
    expect(demo.name).toBe('Hata Demo Service Center');
    expect(demo.timezone).toBe('Asia/Ho_Chi_Minh');
    expect(demo.slotIntervalMinutes).toBe(30);
    expect(demo.openTime).toBe('08:00:00');
    expect(demo.closeTime).toBe('17:30:00');
  });

  test('vehicle categories list scopes supported brands, models, and types without VIN', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);

    expect(response.status()).toBe(200);
    const categories = await response.json();
    expect(Array.isArray(categories)).toBe(true);
    expect(categories.length).toBeGreaterThanOrEqual(4);

    const types = categories.map((c: { type: string }) => c.type);
    expect(types).toContain('SEDAN');
    expect(types).toContain('SUV');
    expect(types).toContain('TRUCK');
    expect(types).toContain('EV');

    for (const category of categories) {
      expect(category.id).toBeTruthy();
      expect(category.brand).toBeTruthy();
      expect(category.model).toBeTruthy();
      expect(category.type).toBeTruthy();
      // Enforce architectural boundary: vehicle categories never contain vehicle_vin
      expect((category as { vin?: string }).vin).toBeUndefined();
    }
  });

  test('vehicle categories returns 404 problem details for non-existent dealership', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${UNKNOWN_ID}/vehicle-categories`);

    expect(response.status()).toBe(404);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const problem = await response.json();
    expect(problem.status).toBe(404);
    expect(problem.title).toBe('Not Found');
  });

  test('service types list exposes duration and description', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);

    expect(response.status()).toBe(200);
    const serviceTypes = await response.json();
    expect(Array.isArray(serviceTypes)).toBe(true);
    expect(serviceTypes.length).toBeGreaterThanOrEqual(3);

    const expressLane = serviceTypes.find((s: { code: string }) => s.code === 'EXPRESS_LANE');
    expect(expressLane).toBeTruthy();
    expect(expressLane.estimatedMinutes).toBe(30);
    expect(expressLane.description).toBeTruthy();
    expect(Array.isArray(expressLane.allowedBayTypes)).toBe(true);
    expect(expressLane.allowedBayTypes).toContain('QUICK_LUBE');
  });

  test('service types returns 404 problem details for non-existent dealership', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${UNKNOWN_ID}/service-types`);

    expect(response.status()).toBe(404);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const problem = await response.json();
    expect(problem.status).toBe(404);
  });

  test('pricing estimate calculates itemized and total cost for selected category and services', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    // Fetch Sedan category
    const catResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);
    const categories = await catResp.json();
    const sedan = categories.find((c: { type: string }) => c.type === 'SEDAN');
    expect(sedan).toBeTruthy();

    // Fetch service types
    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const expressLane = serviceTypes.find((s: { code: string }) => s.code === 'EXPRESS_LANE');
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE');
    expect(expressLane).toBeTruthy();
    expect(standardService).toBeTruthy();

    const estimateUrl = `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/pricing/estimate?categoryId=${sedan.id}&serviceTypeIds=${expressLane.id}&serviceTypeIds=${standardService.id}`;
    const estimateResp = await request.get(estimateUrl);

    expect(estimateResp.status()).toBe(200);
    const estimate = await estimateResp.json();
    expect(estimate.dealershipId).toBe(DEMO_DEALERSHIP_ID);
    expect(estimate.vehicleCategoryId).toBe(sedan.id);
    expect(Array.isArray(estimate.items)).toBe(true);
    expect(estimate.items.length).toBe(2);

    const expressItem = estimate.items.find((i: { serviceTypeId: string }) => i.serviceTypeId === expressLane.id);
    const standardItem = estimate.items.find((i: { serviceTypeId: string }) => i.serviceTypeId === standardService.id);
    expect(expressItem.cost).toBe(60.0);
    expect(standardItem.cost).toBe(150.0);
    expect(estimate.totalCost).toBe(210.0);
  });

  test('pricing estimate rejects missing parameters with 400 problem details', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/pricing/estimate`);

    expect(response.status()).toBe(400);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const problem = await response.json();
    expect(problem.status).toBe(400);
  });
});
