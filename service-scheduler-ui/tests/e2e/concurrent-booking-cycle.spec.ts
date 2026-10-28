import { randomUUID } from "node:crypto";

import { APIResponse, expect, test } from "@playwright/test";

import {
  apiContext,
  freshPhone,
  freshVin,
  idempotencyKey,
  sendOtp,
  sessionCookie,
} from "../support/api";

/**
 * End-to-end: two-customer concurrent booking on one bay (SYSTEM_DESIGN 12.1).
 *
 * Flow:
 *   1. Two users register with OTP and hold separate sessions.
 *   2. Both query available slots for the same date and service type. The grid
 *      excludes the window that the seeded booking holds for the bay and the
 *      eligible technician.
 *   3. Both users book the same free window on the same bay at the same time.
 *      The PostgreSQL EXCLUDE constraint lets one commit and rejects the other
 *      with HTTP 409.
 *   4. A further booking on the occupied bay window answers the exact bay
 *      conflict problem, which isolates the bay gate from other gates.
 *   5. The winner cancels. The date-scoped eviction fires on commit.
 *   6. The loser re-queries slots: the freed window is available again. The
 *      loser books that window and succeeds.
 *
 * Every booking response is recorded at once and cancelled in a finally
 * block, so a failed assertion never leaves an active appointment behind.
 */
test.describe("Concurrent booking cycle - race, conflict, cancel, rebook", () => {
  const DEMO_DEALERSHIP_ID = "00000000-0000-0000-0000-000000000001";
  const STANDARD_BAY_ID = "33333333-0000-0000-0000-000000000002";

  type ApiContext = Awaited<ReturnType<typeof apiContext>>;

  /** One appointment that a finally block must still cancel. */
  interface Leftover {
    id: string;
    request: ApiContext;
    cookie: string;
  }

  /**
   * Returns the calendar date N days from now in Ho Chi Minh City timezone
   * as a YYYY-MM-DD string.
   */
  function hcmDate(daysFromNow: number): string {
    const now = new Date();
    const hcmDateStr = now.toLocaleDateString("en-CA", {
      timeZone: "Asia/Ho_Chi_Minh",
    });
    const [yyyy, mm, dd] = hcmDateStr.split("-").map(Number);
    const target = new Date(Date.UTC(yyyy, mm - 1, dd + daysFromNow));
    return target.toISOString().split("T")[0];
  }

  /**
   * Parses slot startTime to the local hour in Ho Chi Minh City.
   */
  function slotStartHour(startTime: string): number {
    const dt = new Date(startTime);
    return Number(
      dt.toLocaleString("en-CA", {
        hour: "numeric",
        hour12: false,
        timeZone: "Asia/Ho_Chi_Minh",
      }),
    );
  }

  /**
   * Registers a fresh customer and returns the JWT session cookie.
   */
  async function registerCustomer(request: ApiContext): Promise<string> {
    const phone = freshPhone();
    const otp = await sendOtp(request, phone, "REGISTER");
    const registerResp = await request.post("/api/v1/auth/register", {
      headers: { "X-Idempotency-Key": idempotencyKey("register", phone) },
      data: { phone, code: otp, firstName: "Concurrent", lastName: "Race" },
    });
    expect(registerResp.status(), await registerResp.text()).toBe(201);
    return sessionCookie(registerResp);
  }

  async function fetchSlots(
    request: ApiContext,
    serviceTypeId: string,
    date: string,
  ): Promise<Slot[]> {
    const resp = await request.get(
      `/api/v1/slots/available?dealershipId=${DEMO_DEALERSHIP_ID}&serviceTypeId=${serviceTypeId}&date=${date}`,
    );
    expect(resp.status(), await resp.text()).toBe(200);
    return resp.json();
  }

  /**
   * Cancels a recorded appointment. The deletion is best effort, because the
   * flow under test may already have cancelled it on the happy path.
   */
  async function cancelLeftover(leftover: Leftover): Promise<void> {
    let resp: APIResponse;
    try {
      resp = await leftover.request.delete(
        `/api/v1/appointments/${leftover.id}`,
        {
          headers: { Cookie: `jwt=${leftover.cookie}` },
        },
      );
    } catch (error) {
      // Report without throwing: a cleanup failure must not mask the test
      // result, but it must be visible in the output.
      console.warn(`[cleanup] cancel ${leftover.id} failed: ${error}`);
      return;
    }
    if (resp.status() !== 200) {
      console.warn(`[cleanup] cancel ${leftover.id} answered ${resp.status()}`);
    }
  }

  interface Slot {
    startTime: string;
    endTime: string;
    available: boolean;
  }

  test("two users race for one bay window: 1 success, 1 rejected 409; after cancel the freed window rebooks", async ({
    playwright,
    baseURL,
  }) => {
    // --- Step 1: two users register and login ---
    const userA = await apiContext(playwright, baseURL!);
    const userB = await apiContext(playwright, baseURL!);
    const cookieA = await registerCustomer(userA);
    const cookieB = await registerCustomer(userB);
    expect(cookieA).toBeTruthy();
    expect(cookieB).toBeTruthy();

    // --- Step 2: resolve the shared booking inputs ---
    const stResp = await userA.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`,
    );
    expect(stResp.status()).toBe(200);
    const serviceTypes = await stResp.json();
    const standardService = serviceTypes.find(
      (s: { code: string }) => s.code === "STANDARD_SERVICE",
    );
    expect(standardService, "STANDARD_SERVICE must exist").toBeTruthy();

    const catResp = await userA.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`,
    );
    expect(catResp.status()).toBe(200);
    const categories = await catResp.json();
    const category = categories[0];

    // Evict any pre-seeded fixture grid so the grid computes from live data.
    const evictResp = await userA.delete(
      `/api/v1/slots/cache?dealershipId=${DEMO_DEALERSHIP_ID}`,
    );
    expect(evictResp.status()).toBe(204);

    // --- Step 2a: find the date the seed books at 09:00 and confirm both ---
    // users see that window unavailable. The scan reads the live grids, so it
    // stays correct even when the seed date drifts past a local midnight.
    const seedDate = (await (async () => {
      for (const daysAhead of [2, 1, 3, 4, 5, 6, 7]) {
        const date = hcmDate(daysAhead);
        const grid = await fetchSlots(userA, standardService.id, date);
        const at09 = grid.find((s) => slotStartHour(s.startTime) === 9);
        if (at09 && !at09.available) {
          return date;
        }
      }
      return null;
    })()) as string | null;
    expect(
      seedDate,
      "the seed must hold a 09:00 booking within the advance window",
    ).toBeTruthy();
    const targetDate = seedDate!;

    const slotsA = await fetchSlots(userA, standardService.id, targetDate);
    const slotsB = await fetchSlots(userB, standardService.id, targetDate);

    const booked09A = slotsA.find((s) => slotStartHour(s.startTime) === 9);
    expect(booked09A, "09:00 slot must exist in the grid").toBeTruthy();
    expect(
      booked09A!.available,
      "09:00 is booked by the seed - must be false for user A",
    ).toBe(false);
    const booked09B = slotsB.find((s) => slotStartHour(s.startTime) === 9);
    expect(
      booked09B!.available,
      "09:00 is booked by the seed - must be false for user B",
    ).toBe(false);

    // Pick the shared candidate windows: available slots ordered by start time.
    // The grid reports a window available when ANY compatible bay is free; the
    // race below books ONE declared bay, so a window backed only by another bay
    // loses twice. The pool of leading candidates plus the retry keeps the
    // test robust against leftover bookings from earlier suite runs.
    const candidates = slotsA
      .filter((s) => s.available && slotStartHour(s.startTime) !== 9)
      .sort((a, b) => a.startTime.localeCompare(b.startTime));
    expect(
      candidates.length,
      "at least one open window outside 09:00 must exist",
    ).toBeGreaterThan(0);

    // --- Steps 3 to 6 run under a cleanup guard. Every booking that commits
    // lands in `leftovers` before anything else can fail. ---
    const leftovers: Leftover[] = [];
    try {
      const bodyFor = (window: Slot, vin: string) => ({
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: category.id,
        vehicleVin: vin,
        serviceJobs: [
          {
            serviceTypeId: standardService.id,
            bayId: STANDARD_BAY_ID,
            startTime: window.startTime,
            endTime: window.endTime,
          },
        ],
      });

      /**
       * Books one window with both users concurrently. Returns the window, the
       * winner, and the loser when exactly one request commits, else null when
       * both hit the bay gate (the declared bay is already occupied there).
       * A winning probe response is recorded for cleanup at once.
       */
      async function race(window: Slot) {
        const [respA, respB] = await Promise.all([
          userA.post("/api/v1/appointments", {
            headers: {
              Cookie: `jwt=${cookieA}`,
              "X-Idempotency-Key": randomUUID(),
            },
            data: bodyFor(window, freshVin()),
          }),
          userB.post("/api/v1/appointments", {
            headers: {
              Cookie: `jwt=${cookieB}`,
              "X-Idempotency-Key": randomUUID(),
            },
            data: bodyFor(window, freshVin()),
          }),
        ]);

        let winnerCtx: {
          request: ApiContext;
          cookie: string;
          appt: { id: string; status: string };
        } | null = null;
        let loserProblem: { status: number; title: string } | null = null;

        for (const [resp, request, cookie] of [
          [respA, userA, cookieA] as const,
          [respB, userB, cookieB] as const,
        ]) {
          const status = resp.status();
          if (status === 201) {
            const appt = (await resp.json()) as { id: string; status: string };
            leftovers.push({ id: appt.id, request, cookie });
            winnerCtx = { request, cookie, appt };
          } else if (status === 409) {
            loserProblem = await resp.json();
          } else {
            // A probe that answers neither 201 nor 409 misses the expected
            // race pair. The stray 201 (if any) stays in leftovers for the
            // cleanup guard.
            return null;
          }
        }

        if (!winnerCtx || !loserProblem) {
          return null;
        }

        const winner = { request: winnerCtx.request, cookie: winnerCtx.cookie };
        const loser =
          winnerCtx.request === userA
            ? { request: userB, cookie: cookieB }
            : { request: userA, cookie: cookieA };
        return {
          window,
          winner,
          loser,
          winnerAppt: winnerCtx.appt,
          conflictProblem: loserProblem,
        };
      }

      // Probe candidates until one race lands the expected one-win-one-reject
      // outcome on the declared bay. A probe without the expected pair leaves
      // no active appointment: its 201 (if any) is cancelled by the guard.
      let raceResult: Awaited<ReturnType<typeof race>> = null;
      for (const candidate of candidates) {
        raceResult = await race(candidate);
        if (raceResult) {
          break;
        }
      }
      expect(
        raceResult,
        "a candidate window must produce exactly one 201 and one 409",
      ).toBeTruthy();

      const {
        window: racedSlot,
        winner,
        loser,
        winnerAppt,
        conflictProblem,
      } = raceResult!;
      expect(winnerAppt.status).toBe("CONFIRMED");
      expect(conflictProblem.status).toBe(409);
      expect(conflictProblem.title).toContain("conflict");

      // --- Step 4: a third booking on the same occupied bay window must name
      // the bay conflict. This isolates the bay gate from the technician and
      // vehicle gates that also guard the grid. ---
      const bayProbeResp = await loser.request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${loser.cookie}`,
          "X-Idempotency-Key": randomUUID(),
        },
        data: bodyFor(racedSlot, freshVin()),
      });
      expect(bayProbeResp.status()).toBe(409);
      const bayProbeProblem = await bayProbeResp.json();
      expect(bayProbeProblem.title).toBe("Workshop bay conflict");

      // --- Step 5: the winner cancels; the date-scoped cache eviction fires
      // on commit ---
      const cancelResp = await winner.request.delete(
        `/api/v1/appointments/${winnerAppt.id}`,
        {
          headers: { Cookie: `jwt=${winner.cookie}` },
        },
      );
      expect(cancelResp.status()).toBe(200);
      const cancelled = await cancelResp.json();
      expect(cancelled.status).toBe("CANCELLED");
      const cancelledIndex = leftovers.findIndex((l) => l.id === winnerAppt.id);
      if (cancelledIndex >= 0) {
        leftovers.splice(cancelledIndex, 1);
      }

      // --- Step 6: the loser re-queries slots - the freed window is available
      // again ---
      const recheck = await fetchSlots(
        loser.request,
        standardService.id,
        targetDate,
      );
      const freed = recheck.find(
        (s) =>
          s.startTime === racedSlot.startTime &&
          s.endTime === racedSlot.endTime,
      );
      expect(
        freed,
        "the previously conflicting window must still exist in the grid",
      ).toBeTruthy();
      expect(
        freed!.available,
        "after cancellation the window must be available again",
      ).toBe(true);

      // --- Step 7: the loser books the freed window on the same bay and
      // succeeds ---
      const rebookResp = await loser.request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${loser.cookie}`,
          "X-Idempotency-Key": randomUUID(),
        },
        data: bodyFor(racedSlot, freshVin()),
      });
      expect(rebookResp.status(), await rebookResp.text()).toBe(201);
      const rebooked = (await rebookResp.json()) as {
        id: string;
        status: string;
      };
      expect(rebooked.status).toBe("CONFIRMED");
      expect(rebooked.id).not.toBe(winnerAppt.id);
      if (!leftovers.some((l) => l.id === rebooked.id)) {
        leftovers.push({
          id: rebooked.id,
          request: loser.request,
          cookie: loser.cookie,
        });
      }

      // --- Step 8: self-cleanup on the happy path: the rebooked appointment
      // is cancelled so the suite leaves no bay-2 leftovers behind ---
      const cleanupResp = await loser.request.delete(
        `/api/v1/appointments/${rebooked.id}`,
        {
          headers: { Cookie: `jwt=${loser.cookie}` },
        },
      );
      expect(cleanupResp.status()).toBe(200);
      expect((await cleanupResp.json()).status).toBe("CANCELLED");
      const rebookIndex = leftovers.findIndex((l) => l.id === rebooked.id);
      if (rebookIndex >= 0) {
        leftovers.splice(rebookIndex, 1);
      }
    } finally {
      for (const leftover of leftovers) {
        await cancelLeftover(leftover);
      }
    }
  });
});
