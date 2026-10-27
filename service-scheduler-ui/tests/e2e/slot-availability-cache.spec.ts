import { expect, test } from '@playwright/test';

import { apiContext, freshPhone, idempotencyKey, sendOtp } from '../support/api';

/**
 * End-to-end: slot availability with dual-resource gate.
 *
 * Full customer flow:
 *   1. Login with OTP
 *   2. List active dealerships
 *   3. List vehicle categories for the selected dealership
 *   4. List service types for the selected dealership
 *   5. Choose a service type and date
 *   6. Query available slots
 *   7. Assert that time windows occupied by seeded bookings are unavailable
 *   8. Assert that other time windows are available
 *
 * The seed data (R__seed_schedule_and_appointments.sql) places CONFIRMED
 * service jobs in Bay 2 (STANDARD) as follows:
 *   Day +1: 08:00–09:00  STANDARD_SERVICE  technician 00000000-...-0004
 *   Day +2: 09:00–10:00  STANDARD_SERVICE  technician 00000000-...-0004
 *   Day +3: 10:00–11:00  STANDARD_SERVICE  technician 00000000-...-0004 (bay 3)
 *
 * The demo dealership has only one STANDARD bay (Bay 2).
 * STANDARD_SERVICE requires bay type STANDARD and technician skill ENGINE ≥ JUNIOR.
 * When Bay 2 is booked and the single technician is assigned, no other resource
 * is available — so the booked window must appear as available=false.
 */
test.describe('Slot availability — full customer flow with booking exclusion', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';

  /**
   * Returns the calendar date N days from now in Ho Chi Minh City timezone
   * as a YYYY-MM-DD string.
   */
  function hcmDate(daysFromNow: number): string {
    const now = new Date();
    const hcmDateStr = now.toLocaleDateString('en-CA', { timeZone: 'Asia/Ho_Chi_Minh' });
    const [yyyy, mm, dd] = hcmDateStr.split('-').map(Number);
    const target = new Date(Date.UTC(yyyy, mm - 1, dd + daysFromNow));
    return target.toISOString().split('T')[0];
  }

  /**
   * Parses slot startTime to the local hour in Ho Chi Minh City.
   * E.g. "2026-10-28T08:00:00+07:00" → 8
   */
  function slotStartHour(startTime: string): number {
    const dt = new Date(startTime);
    return Number(dt.toLocaleString('en-CA', { hour: 'numeric', hour12: false, timeZone: 'Asia/Ho_Chi_Minh' }));
  }

  test('full flow: login → dealership → vehicle category → service type → slots exclude booked window', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    // --- Step 1: Register and login as a new customer ---
    const phone = freshPhone();
    const registerOtp = await sendOtp(request, phone, 'REGISTER');

    const registerResp = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': idempotencyKey('register', phone) },
      data: { phone, code: registerOtp, firstName: 'Slot', lastName: 'Test' }
    });
    expect(registerResp.status(), await registerResp.text()).toBe(201);

    // --- Step 2: List active dealerships ---
    const dealershipsResp = await request.get('/api/v1/dealerships');
    expect(dealershipsResp.status()).toBe(200);
    const dealerships = await dealershipsResp.json();
    expect(Array.isArray(dealerships)).toBe(true);
    expect(dealerships.length).toBeGreaterThan(0);

    const dealership = dealerships.find((d: { id: string }) => d.id === DEMO_DEALERSHIP_ID);
    expect(dealership, 'demo dealership must be active').toBeTruthy();

    // --- Step 3: List vehicle categories for the selected dealership ---
    const categoriesResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);
    expect(categoriesResp.status()).toBe(200);
    const categories = await categoriesResp.json();
    expect(Array.isArray(categories)).toBe(true);
    expect(categories.length).toBeGreaterThan(0);

    // Select Toyota Camry SEDAN — matches seeded cost matrix
    const sedan = categories.find((c: { type: string }) => c.type === 'SEDAN');
    expect(sedan, 'SEDAN vehicle category must exist').toBeTruthy();

    // --- Step 4: List service types for the selected dealership ---
    const serviceTypesResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    expect(serviceTypesResp.status()).toBe(200);
    const serviceTypes = await serviceTypesResp.json();
    expect(Array.isArray(serviceTypes)).toBe(true);

    // Select STANDARD_SERVICE — seeded bay 2 (STANDARD), tech skill ENGINE >= JUNIOR
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE');
    expect(standardService, 'STANDARD_SERVICE must exist').toBeTruthy();
    expect(standardService.estimatedMinutes).toBe(60);

    // --- Step 5: Choose Day+1 (has seeded 08:00-09:00 booking on the only STANDARD bay) ---
    const tomorrow = hcmDate(1);

    // Clear any preloaded fixture grid so this query exercises the DB-backed gate,
    // not a cached fixture grid.
    const clearResp = await request.delete(
      `/api/v1/slots/cache?dealershipId=${DEMO_DEALERSHIP_ID}`
    );
    expect(clearResp.status(), await clearResp.text()).toBe(204);

    // --- Step 6: Query available slots ---
    const slotsResp = await request.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${tomorrow}`
    );
    expect(slotsResp.status(), await slotsResp.text()).toBe(200);
    const slots = await slotsResp.json();

    // Response must be an array of slot objects.
    expect(Array.isArray(slots)).toBe(true);
    expect(slots.length).toBeGreaterThan(0);

    for (const slot of slots) {
      expect(slot).toHaveProperty('startTime');
      expect(slot).toHaveProperty('endTime');
      expect(typeof slot.available).toBe('boolean');

      // Every slot end must be after its start.
      const start = new Date(slot.startTime);
      const end = new Date(slot.endTime);
      expect(end.getTime()).toBeGreaterThan(start.getTime());

      // Each slot must span exactly the service duration (60 min = 3600 s).
      const durationSeconds = (end.getTime() - start.getTime()) / 1000;
      expect(durationSeconds).toBe(standardService.estimatedMinutes * 60);
    }

    // --- Step 7: Assert that the seeded 08:00 window is unavailable ---
    // The seed puts a service_job on Bay 2 (the only STANDARD bay) from 08:00 to 09:00
    // AND assigns the only qualified technician to that job.
    // Therefore available=false must appear for the 08:00 window.
    const slot08 = slots.find((s: { startTime: string }) => slotStartHour(s.startTime) === 8);
    expect(slot08, '08:00 slot must exist in the grid').toBeTruthy();
    expect(slot08.available, '08:00 is occupied by the seeded booking — must be false').toBe(false);

    // --- Step 8: Assert that at least one other window is available ---
    const openSlot = slots.find((s: { available: boolean; startTime: string }) =>
      s.available && slotStartHour(s.startTime) !== 8
    );
    expect(openSlot, 'at least one slot outside the booked window must be available').toBeTruthy();
  });

  test('Day+2 booked at 09:00 — that window is unavailable, others are open', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE');
    expect(standardService).toBeTruthy();

    const day2 = hcmDate(2);
    await request.delete(`/api/v1/slots/cache?dealershipId=${DEMO_DEALERSHIP_ID}`);
    const slotsResp = await request.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${day2}`
    );
    expect(slotsResp.status()).toBe(200);
    const slots = await slotsResp.json();
    expect(Array.isArray(slots)).toBe(true);

    // Seed: Day+2 Bay 2 booked 09:00–10:00, technician also busy.
    const slot09 = slots.find((s: { startTime: string }) => slotStartHour(s.startTime) === 9);
    expect(slot09, '09:00 slot must exist').toBeTruthy();
    expect(slot09.available, '09:00 is occupied on Day+2 — must be false').toBe(false);

    // Other slots must be available (08:00 before the booking, 10:00+ after).
    const openSlot = slots.find((s: { available: boolean; startTime: string }) =>
      s.available && slotStartHour(s.startTime) !== 9
    );
    expect(openSlot, 'slots outside 09:00 on Day+2 must be available').toBeTruthy();
  });

  test('date beyond max advance booking window returns HTTP 400 problem details', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes[0];

    // maxAdvanceBookingDays = 7 for demo dealership; request day 8.
    const tooFar = hcmDate(8);
    const slotsResp = await request.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${tooFar}`
    );

    expect(slotsResp.status()).toBe(400);
    const problem = await slotsResp.json();
    expect(problem.status).toBe(400);
    expect(problem.title).toBe('Slot date out of range');
  });

  test('missing query parameters returns HTTP 400 problem details', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);
    const response = await request.get('/api/v1/slots/available');

    expect(response.status()).toBe(400);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const problem = await response.json();
    expect(problem.status).toBe(400);
  });

  test('past date returns empty slot list without error', async ({ playwright, baseURL }) => {
    const request = await apiContext(playwright, baseURL!);

    const stResp = await request.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes[0];

    const yesterday = hcmDate(-1);
    const slotsResp = await request.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${yesterday}`
    );

    expect(slotsResp.status()).toBe(200);
    const slots = await slotsResp.json();
    expect(Array.isArray(slots)).toBe(true);
    expect(slots.length).toBe(0);
  });
});
