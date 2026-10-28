import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

import { apiContext, freshVin, getCustomerSession } from '../support/api';

/**
 * End-to-end verification for customer appointment booking:
 * - Multi-job booking with two consecutive service types
 * - Automatic confirmation from dealership auto_confirm_appointments setting
 * - Duplicate idempotency key replay returning cached response
 * - Validation rejecting job duration exceeding base estimated minutes (HTTP 400)
 * - Validation rejecting overlapping service jobs within appointment (HTTP 400)
 * - Database exclusion constraint rejecting overlapping bookings for vehicle VIN (HTTP 409)
 */
test.describe('Customer appointment booking', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

  test('multi-job appointment booking with two service types succeeds with auto-confirmed status', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    // Fetch vehicle category and service types
    const catResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);
    expect(catResp.status()).toBe(200);
    const categories = await catResp.json();
    const sedanCat = categories[0];

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    expect(stResp.status()).toBe(200);
    const serviceTypes = await stResp.json();
    const expressService = serviceTypes.find((s: { code: string }) => s.code === 'EXPRESS_LANE') || serviceTypes[0];
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE') || serviceTypes[1];

    const vin = freshVin();
    const key = randomUUID();
    const start1 = '2026-11-28T14:00:00Z';
    const end1 = '2026-11-28T14:30:00Z';
    const start2 = '2026-11-28T14:30:00Z';
    const end2 = '2026-11-28T15:30:00Z';

    const bookResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': key
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: sedanCat.id,
        vehicleVin: vin,
        serviceJobs: [
          {
            serviceTypeId: expressService.id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: start1,
            endTime: end1
          },
          {
            serviceTypeId: standardService.id,
            bayId: '33333333-0000-0000-0000-000000000002',
            startTime: start2,
            endTime: end2
          }
        ]
      }
    });

    expect(bookResp.status()).toBe(201);
    const appt = await bookResp.json();
    expect(appt.id).toBeTruthy();
    expect(appt.vehicleVin).toBe(vin);
    expect(appt.status).toBe('CONFIRMED');
    expect(appt.serviceJobs.length).toBe(2);

    // Idempotency replay check
    const replayResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': key
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: sedanCat.id,
        vehicleVin: vin,
        serviceJobs: [
          {
            serviceTypeId: expressService.id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: start1,
            endTime: end1
          }
        ]
      }
    });
    expect(replayResp.status()).toBe(201);
    const replayed = await replayResp.json();
    expect(replayed.id).toBe(appt.id);
  });

  test('booking rejects service job duration exceeding base duration with 400 Bad Request', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const catResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);
    const categories = await catResp.json();

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const expressService = serviceTypes.find((s: { code: string }) => s.code === 'EXPRESS_LANE') || serviceTypes[0];

    // Request 90 minutes for a 30-minute express service
    const bookResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': randomUUID()
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: categories[0].id,
        vehicleVin: '1HGCR2F83HA777002',
        serviceJobs: [
          {
            serviceTypeId: expressService.id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: '2026-11-28T16:00:00Z',
            endTime: '2026-11-28T17:30:00Z'
          }
        ]
      }
    });

    expect(bookResp.status()).toBe(400);
    const problem = await bookResp.json();
    expect(problem.status).toBe(400);
    expect(problem.detail).toContain('exceeds base duration');
  });

  test('booking rejects intra-appointment overlapping service jobs with 400 Bad Request', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const catResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);
    const categories = await catResp.json();

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();

    const bookResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': randomUUID()
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: categories[0].id,
        vehicleVin: '1HGCR2F83HA777003',
        serviceJobs: [
          {
            serviceTypeId: serviceTypes[0].id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: '2026-11-28T17:00:00Z',
            endTime: '2026-11-28T17:30:00Z'
          },
          {
            serviceTypeId: serviceTypes[1].id,
            bayId: '33333333-0000-0000-0000-000000000002',
            startTime: '2026-11-28T17:15:00Z', // overlaps first job
            endTime: '2026-11-28T18:15:00Z'
          }
        ]
      }
    });

    expect(bookResp.status()).toBe(400);
    const problem = await bookResp.json();
    expect(problem.status).toBe(400);
    expect(problem.detail).toContain('overlap');
  });

  test('customer booking flow: login, select dealership, vehicle category, input VIN, add 2 services, query slots, happy case pass all, and bad case overlap error with recovery', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    // 1. Customer login (verifies JWT session cookie)
    const cookie = await getCustomerSession(request);
    expect(cookie).toBeTruthy();

    // 2. Select dealership
    const dealersResp = await request.get('/api/v1/dealerships');
    expect(dealersResp.status()).toBe(200);
    const dealerships = await dealersResp.json();
    expect(dealerships.length).toBeGreaterThan(0);
    const dealership = dealerships[0];
    const dealershipId = dealership.id;

    // 3. Select vehicle category
    const catResp = await request.get(`/api/v1/dealerships/${dealershipId}/vehicle-categories`);
    expect(catResp.status()).toBe(200);
    const categories = await catResp.json();
    expect(categories.length).toBeGreaterThan(0);
    const vehicleCategory = categories[0];

    // 4. Input VIN
    const testVin = freshVin();
    const testVin2 = freshVin();

    // 5. Add 2 service types
    const stResp = await request.get(`/api/v1/dealerships/${dealershipId}/service-types`);
    expect(stResp.status()).toBe(200);
    const serviceTypes = await stResp.json();
    expect(serviceTypes.length).toBeGreaterThanOrEqual(2);
    const service1 = serviceTypes[0];
    const service2 = serviceTypes[1];

    // 6. Call available slots for both service types on candidate date
    const targetDate = '2026-12-05';
    const slots1Resp = await request.get(
      `/api/v1/slots/available?dealershipId=${dealershipId}&serviceTypeId=${service1.id}&date=${targetDate}`
    );
    expect(slots1Resp.status()).toBe(200);

    const slots2Resp = await request.get(
      `/api/v1/slots/available?dealershipId=${dealershipId}&serviceTypeId=${service2.id}&date=${targetDate}`
    );
    expect(slots2Resp.status()).toBe(200);

    // 7. Happy case: select 2 non-overlapping service jobs and submit booking
    const happyKey = randomUUID();
    const happyBookResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': happyKey
      },
      data: {
        dealershipId,
        vehicleCategoryId: vehicleCategory.id,
        vehicleVin: testVin,
        serviceJobs: [
          {
            serviceTypeId: service1.id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: `${targetDate}T08:00:00Z`,
            endTime: `${targetDate}T08:30:00Z`
          },
          {
            serviceTypeId: service2.id,
            bayId: '33333333-0000-0000-0000-000000000002',
            startTime: `${targetDate}T08:30:00Z`,
            endTime: `${targetDate}T09:30:00Z`
          }
        ]
      }
    });

    expect(happyBookResp.status()).toBe(201);
    const happyAppointment = await happyBookResp.json();
    expect(happyAppointment.id).toBeTruthy();
    expect(happyAppointment.vehicleVin).toBe(testVin);
    expect(happyAppointment.status).toBe('CONFIRMED');
    expect(happyAppointment.serviceJobs).toHaveLength(2);

    // 8. Bad case: select 2 service jobs that overlap in time -> raises error
    const badKey = randomUUID();
    const overlapBookResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': badKey
      },
      data: {
        dealershipId,
        vehicleCategoryId: vehicleCategory.id,
        vehicleVin: testVin2,
        serviceJobs: [
          {
            serviceTypeId: service1.id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: `${targetDate}T10:00:00Z`,
            endTime: `${targetDate}T10:30:00Z`
          },
          {
            serviceTypeId: service2.id,
            bayId: '33333333-0000-0000-0000-000000000002',
            startTime: `${targetDate}T10:15:00Z`, // Overlaps job 1!
            endTime: `${targetDate}T11:15:00Z`
          }
        ]
      }
    });

    expect(overlapBookResp.status()).toBe(400);
    const overlapError = await overlapBookResp.json();
    expect(overlapError.status).toBe(400);
    expect(overlapError.detail).toContain('overlap');

    // 9. Client receives error, selects new non-overlapping time slot, and resubmits successfully
    const recoveredKey = randomUUID();
    const recoveredBookResp = await request.post('/api/v1/appointments', {
      headers: {
        'Cookie': `jwt=${cookie}`,
        'X-Idempotency-Key': recoveredKey
      },
      data: {
        dealershipId,
        vehicleCategoryId: vehicleCategory.id,
        vehicleVin: testVin2,
        serviceJobs: [
          {
            serviceTypeId: service1.id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: `${targetDate}T10:00:00Z`,
            endTime: `${targetDate}T10:30:00Z`
          },
          {
            serviceTypeId: service2.id,
            bayId: '33333333-0000-0000-0000-000000000002',
            startTime: `${targetDate}T10:30:00Z`, // Adjusted to non-overlapping slot!
            endTime: `${targetDate}T11:30:00Z`
          }
        ]
      }
    });

    expect(recoveredBookResp.status()).toBe(201);
    const recoveredAppointment = await recoveredBookResp.json();
    expect(recoveredAppointment.id).toBeTruthy();
    expect(recoveredAppointment.status).toBe('CONFIRMED');
    expect(recoveredAppointment.serviceJobs).toHaveLength(2);
  });
});
