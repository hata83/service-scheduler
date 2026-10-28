import { expect, test } from '@playwright/test';

import {
  apiContext,
  freshPhone,
  getCustomerSession,
  idempotencyKey,
  sendOtp,
  sessionCookie
} from '../support/api';

/**
 * End-to-end verification for customer appointment history:
 * - Lists customer appointments ordered by start time descending
 * - Retrieves itemized appointment details by id
 * - Verifies cross-customer access isolation
 * - Verifies globally unique keys across appointments and jobs
 */
test.describe('Customer appointment history', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

  test('customer can view appointment history list ordered by start time', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const listResp = await request.get('/api/v1/appointments', {
      headers: { Cookie: `jwt=${cookie}` }
    });

    expect(listResp.status()).toBe(200);
    const appointments = await listResp.json();
    expect(Array.isArray(appointments)).toBe(true);
    expect(appointments.length).toBeGreaterThan(0);

    for (const appt of appointments) {
      expect(appt.id).toBeTruthy();
      expect(appt.dealershipId).toBe(DEMO_DEALERSHIP_ID);
      expect(appt.status).toBeTruthy();
      expect(appt.startTime).toBeTruthy();
      expect(Array.isArray(appt.serviceJobs)).toBe(true);
    }

    // Verify descending order of appointment start times
    for (let i = 0; i < appointments.length - 1; i++) {
      const current = new Date(appointments[i].startTime).getTime();
      const next = new Date(appointments[i + 1].startTime).getTime();
      expect(current).toBeGreaterThanOrEqual(next);
    }
  });

  test('customer can view itemized appointment details by id', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    // List first to get a valid appointment ID
    const listResp = await request.get('/api/v1/appointments', {
      headers: { Cookie: `jwt=${cookie}` }
    });
    const appointments = await listResp.json();
    expect(appointments.length).toBeGreaterThan(0);
    const targetId = appointments[0].id;

    const detailResp = await request.get(`/api/v1/appointments/${targetId}`, {
      headers: { Cookie: `jwt=${cookie}` }
    });

    expect(detailResp.status()).toBe(200);
    const detail = await detailResp.json();
    expect(detail.id).toBe(targetId);
    expect(detail.serviceJobs.length).toBeGreaterThanOrEqual(1);
    for (const job of detail.serviceJobs) {
      expect(job.id).toBeTruthy();
      expect(job.serviceTypeId).toBeTruthy();
      expect(job.bayId).toBeTruthy();
      expect(job.startTime).toBeTruthy();
      expect(job.endTime).toBeTruthy();
    }

    // Verify cross-customer access isolation: another customer cannot access this appointment
    const otherPhone = freshPhone();
    const otherCode = await sendOtp(request, otherPhone, 'REGISTER');
    const otherRegister = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': idempotencyKey('reg-isolation', otherPhone) },
      data: { phone: otherPhone, firstName: 'Isolated', lastName: 'Customer', code: otherCode }
    });
    expect(otherRegister.status(), await otherRegister.text()).toBe(201);
    const otherCookie = sessionCookie(otherRegister);

    const unauthorizedResp = await request.get(`/api/v1/appointments/${targetId}`, {
      headers: { Cookie: `jwt=${otherCookie}` }
    });
    expect(unauthorizedResp.status()).toBe(404);
  });

  test('booking list items and itemized service jobs have globally unique keys', async ({
    playwright,
    baseURL
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const listResp = await request.get('/api/v1/appointments', {
      headers: { Cookie: `jwt=${cookie}` }
    });
    expect(listResp.status()).toBe(200);
    const appointments: Array<{
      id: string;
      status: string;
      serviceJobs: Array<{ id: string }>;
    }> = await listResp.json();
    expect(appointments.length).toBeGreaterThan(0);

    // Verify all booking list items have unique primary keys
    const apptIds = appointments.map((a) => a.id);
    const uniqueApptIds = new Set(apptIds);
    expect(uniqueApptIds.size).toBe(apptIds.length);

    // Verify all itemized service jobs across all appointments have globally unique keys
    const allJobs = appointments.flatMap((a) => a.serviceJobs);
    expect(allJobs.length).toBeGreaterThan(0);
    for (const job of allJobs) {
      expect(job.id).toBeTruthy();
    }
    const allJobIds = allJobs.map((j) => j.id);
    const uniqueJobIds = new Set(allJobIds);
    expect(uniqueJobIds.size).toBe(allJobIds.length);
  });
});
