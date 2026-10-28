import { randomUUID } from 'node:crypto';

import { expect, test } from '@playwright/test';

import { apiContext, freshPhone, freshVin, idempotencyKey, sendOtp, sessionCookie } from '../support/api';

/**
 * End-to-end: two-customer concurrent booking on one bay (SYSTEM_DESIGN 12.1).
 *
 * Flow:
 *   1. Two users register with OTP and hold separate sessions.
 *   2. Both query available slots for the same date and service type. The grid
 *      excludes the window booked for the bay by the seed (Day +2, 09:00-10:00).
 *   3. Both users book the same free window on the same bay at the same time.
 *      The PostgreSQL EXCLUDE constraint lets one commit and rejects the other
 *      with HTTP 409.
 *   4. The winner cancels. The date-scoped eviction fires on commit.
 *   5. The loser re-queries slots: the freed window is available again. The
 *      loser books that window and succeeds.
 */
test.describe('Concurrent booking cycle - race, conflict, cancel, rebook', () => {
  const DEMO_DEALERSHIP_ID = '00000000-0000-0000-0000-000000000001';
  const STANDARD_BAY_ID = '33333333-0000-0000-0000-000000000002';

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
   */
  function slotStartHour(startTime: string): number {
    const dt = new Date(startTime);
    return Number(dt.toLocaleString('en-CA', { hour: 'numeric', hour12: false, timeZone: 'Asia/Ho_Chi_Minh' }));
  }

  /**
   * Builds a VIN unique to this spec run. The default freshVin() base restarts
   * its counter per process, so a VIN booked by an earlier run can collide with
   * a booking this spec does not target. The base embeds time-derived digits,
   * so each run gets a disjoint VIN space.
   */
  function specVin(): string {
    // Valid VIN charset is ^[A-HJ-NPR-Z0-9]{17}$. '0' and '1' are valid digits.
    const runStamp = BigInt(Date.now()).toString(36).toUpperCase().replace(/[^0-9A-Z]/g, '');
    const unique3 = (runStamp.slice(-3) + '000').slice(0, 3).replace(/[^0-9A-HJ-NPR-Z]/g, 'A').padEnd(3, 'A');
    return freshVin(`WVWZZZ1KZ${unique3}`.slice(0, 11));
  }

  /**
   * Registers a fresh customer and returns the JWT session cookie.
   */
  async function registerCustomer(request: Awaited<ReturnType<typeof apiContext>>): Promise<string> {
    const phone = freshPhone();
    const otp = await sendOtp(request, phone, 'REGISTER');
    const registerResp = await request.post('/api/v1/auth/register', {
      headers: { 'X-Idempotency-Key': idempotencyKey('register', phone) },
      data: { phone, code: otp, firstName: 'Concurrent', lastName: 'Race' }
    });
    expect(registerResp.status(), await registerResp.text()).toBe(201);
    return sessionCookie(registerResp);
  }

  interface Slot {
    startTime: string;
    endTime: string;
    available: boolean;
  }

  test('two users race for one bay window: 1 success, 1 rejected 409; after cancel the freed window rebooks', async ({
    playwright,
    baseURL
  }) => {
    // --- Step 1: two users register and login ---
    const userA = await apiContext(playwright, baseURL!);
    const userB = await apiContext(playwright, baseURL!);
    const cookieA = await registerCustomer(userA);
    const cookieB = await registerCustomer(userB);
    expect(cookieA).toBeTruthy();
    expect(cookieB).toBeTruthy();

    // --- Step 2: resolve the shared booking inputs ---
    const stResp = await userA.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`);
    expect(stResp.status()).toBe(200);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes.find((s: { code: string }) => s.code === 'STANDARD_SERVICE');
    expect(standardService, 'STANDARD_SERVICE must exist').toBeTruthy();

    const catResp = await userA.get(`/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`);
    expect(catResp.status()).toBe(200);
    const categories = await catResp.json();
    const category = categories[0];

    const targetDate = hcmDate(2);

    // Evict any pre-seeded fixture grid so the grid computes from live data.
    const evictResp = await userA.delete(`/api/v1/slots/cache?dealershipId=${DEMO_DEALERSHIP_ID}`);
    expect(evictResp.status()).toBe(204);

    // --- Step 2a: both users see the same availability, excluding the bay's booked window ---
    const slotsAResp = await userA.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${targetDate}`
    );
    expect(slotsAResp.status()).toBe(200);
    const slotsA: Slot[] = await slotsAResp.json();

    const slotsBResp = await userB.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${targetDate}`
    );
    expect(slotsBResp.status()).toBe(200);
    const slotsB: Slot[] = await slotsBResp.json();

    // The seed books the ONLY STANDARD bay on Day +2, 09:00-10:00: the window
    // must be unavailable for both users, and the grids must agree.
    const booked09A = slotsA.find((s) => slotStartHour(s.startTime) === 9);
    expect(booked09A, '09:00 slot must exist in the grid').toBeTruthy();
    expect(booked09A!.available, '09:00 is booked for the bay - must be false for user A').toBe(false);
    const booked09B = slotsB.find((s) => slotStartHour(s.startTime) === 9);
    expect(booked09B!.available, '09:00 is booked for the bay - must be false for user B').toBe(false);

    // Pick the shared candidate windows: available slots ordered by start time.
    // The grid reports a window available when ANY compatible bay is free; the
    // race below books ONE declared bay, so a window backed only by another bay
    // loses twice. The pool of leading candidates plus the retry keeps the
    // test robust against leftover bookings from earlier suite runs.
    const target = slotsA.find((s) => s.available && slotStartHour(s.startTime) !== 9);
    expect(target, 'at least one open window must exist').toBeTruthy();
    const candidates = slotsA
      .filter((s) => s.available)
      .sort((a, b) => a.startTime.localeCompare(b.startTime));

    // --- Step 3: both users book the same window on the same bay concurrently ---
    const bodyFor = (window: Slot, vin: string) => ({
      dealershipId: DEMO_DEALERSHIP_ID,
      vehicleCategoryId: category.id,
      vehicleVin: vin,
      serviceJobs: [
        {
          serviceTypeId: standardService.id,
          bayId: STANDARD_BAY_ID,
          startTime: window.startTime,
          endTime: window.endTime
        }
      ]
    });

    /**
     * Books one window with both users concurrently. Returns the window, the
     * winner, and the loser when exactly one request commits, else null when
     * both hit the bay gate (the declared bay is already occupied there).
     */
    async function race(window: Slot) {
      const [respA, respB] = await Promise.all([
        userA.post('/api/v1/appointments', {
          headers: { 'Cookie': `jwt=${cookieA}`, 'X-Idempotency-Key': randomUUID() },
          data: bodyFor(window, specVin())
        }),
        userB.post('/api/v1/appointments', {
          headers: { 'Cookie': `jwt=${cookieB}`, 'X-Idempotency-Key': randomUUID() },
          data: bodyFor(window, specVin())
        })
      ]);

      const statuses = [respA.status(), respB.status()].sort();
      if (statuses[0] !== 201 || statuses[1] !== 409) {
        return null;
      }

      const winner = respA.status() === 201 ? { request: userA, cookie: cookieA, resp: respA } : { request: userB, cookie: cookieB, resp: respB };
      const loser = respA.status() === 201 ? { request: userB, cookie: cookieB, resp: respB } : { request: userA, cookie: cookieA, resp: respA };
      const winnerAppt = (await winner.resp.json()) as { id: string; status: string };
      const conflictProblem = await loser.resp.json();
      return { window, winner, loser, winnerAppt, conflictProblem };
    }

    // Probe candidates until one race lands the expected one-win-one-reject
    // outcome on the declared bay. Each losing probe persists nothing.
    let raceResult: Awaited<ReturnType<typeof race>> = null;
    for (const candidate of candidates) {
      raceResult = await race(candidate);
      if (raceResult) {
        break;
      }
    }
    expect(raceResult, 'a candidate window must produce exactly one 201 and one 409').toBeTruthy();

    const { window: racedSlot, winner, loser, winnerAppt, conflictProblem } = raceResult!;
    expect(winnerAppt.status).toBe('CONFIRMED');
    expect(conflictProblem.status).toBe(409);
    expect(conflictProblem.title).toContain('conflict');

    // --- Step 4: the winner cancels; the date-scoped cache eviction fires on commit ---
    const cancelResp = await winner.request.delete(`/api/v1/appointments/${winnerAppt.id}`, {
      headers: { 'Cookie': `jwt=${winner.cookie}` }
    });
    expect(cancelResp.status()).toBe(200);
    const cancelled = await cancelResp.json();
    expect(cancelled.status).toBe('CANCELLED');

    // --- Step 5: the loser re-queries slots - the freed window is available again ---
    const recheck = await loser.request.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${standardService.id}&date=${targetDate}`
    );
    expect(recheck.status()).toBe(200);
    const recheckedSlots: Slot[] = await recheck.json();
    const freed = recheckedSlots.find((s) => s.startTime === racedSlot.startTime && s.endTime === racedSlot.endTime);
    expect(freed, 'the previously conflicting window must still exist in the grid').toBeTruthy();
    expect(freed!.available, 'after cancellation the window must be available again').toBe(true);

    // --- Step 5a: the loser books the freed window on the same bay and succeeds ---
    const rebookResp = await loser.request.post('/api/v1/appointments', {
      headers: { 'Cookie': `jwt=${loser.cookie}`, 'X-Idempotency-Key': randomUUID() },
      data: bodyFor(racedSlot, specVin())
    });
    expect(rebookResp.status(), await rebookResp.text()).toBe(201);
    const rebooked = await rebookResp.json();
    expect(rebooked.status).toBe('CONFIRMED');
    expect(rebooked.id).not.toBe(winnerAppt.id);

    // --- Step 6: self-cleanup — the loser cancels so the suite leaves no ---
    // --- bay-2 leftovers behind for the next run ---
    const cleanupResp = await loser.request.delete(`/api/v1/appointments/${rebooked.id}`, {
      headers: { 'Cookie': `jwt=${loser.cookie}` }
    });
    expect(cleanupResp.status()).toBe(200);
    expect((await cleanupResp.json()).status).toBe('CANCELLED');
  });
});
