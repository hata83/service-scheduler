import { randomUUID } from 'node:crypto';
import { expect, test } from '@playwright/test';

import { apiContext, freshVin, getCustomerSession } from '../support/api';

/**
 * End-to-end verification for appointment lifecycle state machine:
 * - Allows cancellation prior to check-in (from REQUESTED or CONFIRMED status)
 * - Rejects cancellation after check-in with HTTP 422 Problem Details
 * - Rejects cancellation on already-cancelled appointments with HTTP 400
 */
test.describe('Appointment state machine cancellation', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

  test('customer can cancel appointment before check-in and subsequent cancel is rejected', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    // Create an appointment first
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
        vehicleVin: freshVin(),
        serviceJobs: [
          {
            serviceTypeId: serviceTypes[0].id,
            bayId: '33333333-0000-0000-0000-000000000001',
            startTime: '2026-11-29T10:00:00Z',
            endTime: '2026-11-29T10:30:00Z'
          }
        ]
      }
    });
    expect(bookResp.status()).toBe(201);
    const appt = await bookResp.json();
    const apptId = appt.id;

    // Cancel appointment before check-in
    const cancelResp = await request.delete(`/api/v1/appointments/${apptId}`, {
      headers: { 'Cookie': `jwt=${cookie}` }
    });
    expect(cancelResp.status()).toBe(200);
    const cancelBody = await cancelResp.json();
    expect(cancelBody.status).toBe('CANCELLED');

    // Attempt second cancellation on terminal state -> 400 Bad Request
    const repeatCancelResp = await request.delete(`/api/v1/appointments/${apptId}`, {
      headers: { 'Cookie': `jwt=${cookie}` }
    });
    expect(repeatCancelResp.status()).toBe(400);
  });

  test('cancellation is rejected with 422 Problem Details for post-check-in appointment', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    // Target the checked-in appointment
    const targetId = '44444444-0000-0000-0000-000000000003';
    const cancelResp = await request.delete(`/api/v1/appointments/${targetId}`, {
      headers: { 'Cookie': `jwt=${cookie}` }
    });

    expect(cancelResp.status()).toBe(422);
    const problem = await cancelResp.json();
    expect(problem.status).toBe(422);
    expect(problem.detail).toContain('Cancellation permitted prior to check-in only');
  });
});
