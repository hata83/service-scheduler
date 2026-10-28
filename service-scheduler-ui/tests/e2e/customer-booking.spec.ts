import { randomUUID } from "node:crypto";
import { APIResponse, expect, test } from "@playwright/test";

import { apiContext, freshVin, getCustomerSession } from "../support/api";

function getCandidateBookingDate(daysAhead = 2): string {
  const d = new Date();
  d.setDate(d.getDate() + daysAhead);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * End-to-end verification for customer appointment booking:
 * - Multi-job booking with two consecutive service types
 * - Automatic confirmation from dealership auto_confirm_appointments setting
 * - Duplicate idempotency key replay returning cached response
 * - Validation rejecting job duration exceeding base estimated minutes (HTTP 400)
 * - Validation rejecting overlapping service jobs within appointment (HTTP 400)
 * - Database exclusion constraint rejecting overlapping bookings for vehicle VIN (HTTP 409)
 */
test.describe("Customer appointment booking", () => {
  const DEMO_DEALERSHIP_ID = "00000000-0000-0000-0000-000000000001";

  test("multi-job appointment booking with two service types succeeds with auto-confirmed status", async ({
    playwright,
    baseURL,
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    // Fetch vehicle category and service types
    const catResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`,
    );
    expect(catResp.status()).toBe(200);
    const categories = await catResp.json();
    const sedanCat = categories[0];

    const stResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`,
    );
    expect(stResp.status()).toBe(200);
    const serviceTypes = await stResp.json();
    const expressService =
      serviceTypes.find((s: { code: string }) => s.code === "EXPRESS_LANE") ||
      serviceTypes[0];
    const standardService =
      serviceTypes.find(
        (s: { code: string }) => s.code === "STANDARD_SERVICE",
      ) || serviceTypes[1];

    const vin = freshVin();
    const key = randomUUID();
    const start1 = "2026-11-28T14:00:00Z";
    const end1 = "2026-11-28T14:30:00Z";
    const start2 = "2026-11-28T14:30:00Z";
    const end2 = "2026-11-28T15:30:00Z";

    const bookResp = await request.post("/api/v1/appointments", {
      headers: {
        Cookie: `jwt=${cookie}`,
        "X-Idempotency-Key": key,
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: sedanCat.id,
        vehicleVin: vin,
        serviceJobs: [
          {
            serviceTypeId: expressService.id,
            bayId: "33333333-0000-0000-0000-000000000001",
            startTime: start1,
            endTime: end1,
          },
          {
            serviceTypeId: standardService.id,
            bayId: "33333333-0000-0000-0000-000000000002",
            startTime: start2,
            endTime: end2,
          },
        ],
      },
    });

    expect(bookResp.status()).toBe(201);
    const appt = await bookResp.json();
    expect(appt.id).toBeTruthy();
    expect(appt.vehicleVin).toBe(vin);
    expect(appt.status).toBe("CONFIRMED");
    expect(appt.serviceJobs.length).toBe(2);

    try {
      // Idempotency replay check
      const replayResp = await request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${cookie}`,
          "X-Idempotency-Key": key,
        },
        data: {
          dealershipId: DEMO_DEALERSHIP_ID,
          vehicleCategoryId: sedanCat.id,
          vehicleVin: vin,
          serviceJobs: [
            {
              serviceTypeId: expressService.id,
              bayId: "33333333-0000-0000-0000-000000000001",
              startTime: start1,
              endTime: end1,
            },
          ],
        },
      });
      expect(replayResp.status()).toBe(201);
      const replayed = await replayResp.json();
      expect(replayed.id).toBe(appt.id);
    } finally {
      // Cleanup: cancel so the fixed date windows stay open for the next run,
      // even when a replay assertion fails.
      const cleanupResp = await request.delete(
        `/api/v1/appointments/${appt.id}`,
        {
          headers: { Cookie: `jwt=${cookie}` },
        },
      );
      expect(cleanupResp.status()).toBe(200);
    }
  });

  test("booking rejects service job duration exceeding base duration with 400 Bad Request", async ({
    playwright,
    baseURL,
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const catResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`,
    );
    const categories = await catResp.json();

    const stResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`,
    );
    const serviceTypes = await stResp.json();
    const expressService =
      serviceTypes.find((s: { code: string }) => s.code === "EXPRESS_LANE") ||
      serviceTypes[0];

    // Request 90 minutes for a 30-minute express service
    const bookResp = await request.post("/api/v1/appointments", {
      headers: {
        Cookie: `jwt=${cookie}`,
        "X-Idempotency-Key": randomUUID(),
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: categories[0].id,
        vehicleVin: "1HGCR2F83HA777002",
        serviceJobs: [
          {
            serviceTypeId: expressService.id,
            bayId: "33333333-0000-0000-0000-000000000001",
            startTime: "2026-11-28T16:00:00Z",
            endTime: "2026-11-28T17:30:00Z",
          },
        ],
      },
    });

    expect(bookResp.status()).toBe(400);
    const problem = await bookResp.json();
    expect(problem.status).toBe(400);
    expect(problem.detail).toContain("exceeds base duration");
  });

  test("booking rejects intra-appointment overlapping service jobs with 400 Bad Request", async ({
    playwright,
    baseURL,
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await getCustomerSession(request);

    const catResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`,
    );
    const categories = await catResp.json();

    const stResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/service-types`,
    );
    const serviceTypes = await stResp.json();

    const bookResp = await request.post("/api/v1/appointments", {
      headers: {
        Cookie: `jwt=${cookie}`,
        "X-Idempotency-Key": randomUUID(),
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: categories[0].id,
        vehicleVin: "1HGCR2F83HA777003",
        serviceJobs: [
          {
            serviceTypeId: serviceTypes[0].id,
            bayId: "33333333-0000-0000-0000-000000000001",
            startTime: "2026-11-28T17:00:00Z",
            endTime: "2026-11-28T17:30:00Z",
          },
          {
            serviceTypeId: serviceTypes[1].id,
            bayId: "33333333-0000-0000-0000-000000000002",
            startTime: "2026-11-28T17:15:00Z", // overlaps first job
            endTime: "2026-11-28T18:15:00Z",
          },
        ],
      },
    });

    expect(bookResp.status()).toBe(400);
    const problem = await bookResp.json();
    expect(problem.status).toBe(400);
    expect(problem.detail).toContain("overlap");
  });

  test("customer booking flow: login, select dealership, vehicle category, input VIN, add 2 services, query slots without stubs, happy case pass, overlap with existing booking rejects with 409 and cache evict, and recover successfully", async ({
    playwright,
    baseURL,
  }) => {
    const request = await apiContext(playwright, baseURL!);
    // 1. Customer login (verifies JWT session cookie)
    const cookie = await getCustomerSession(request);
    expect(cookie).toBeTruthy();

    // 2. Select dealership
    const dealersResp = await request.get("/api/v1/dealerships");
    expect(dealersResp.status()).toBe(200);
    const dealerships = await dealersResp.json();
    expect(dealerships.length).toBeGreaterThan(0);
    const dealership = dealerships[0];
    const dealershipId = dealership.id;

    // Evict any pre-seeded slot cache fixtures to guarantee real DB-backed compute without stubs
    const evictResp = await request.delete(
      `/api/v1/slots/cache?dealershipId=${dealershipId}`,
    );
    expect(evictResp.status()).toBe(204);

    // 3. Select vehicle category
    const catResp = await request.get(
      `/api/v1/dealerships/${dealershipId}/vehicle-categories`,
    );
    expect(catResp.status()).toBe(200);
    const categories = await catResp.json();
    expect(categories.length).toBeGreaterThan(0);
    const vehicleCategory = categories[0];

    // 4. Input VIN
    const testVin = freshVin();
    const testVin2 = freshVin();

    // 5. Add 2 service types
    const stResp = await request.get(
      `/api/v1/dealerships/${dealershipId}/service-types`,
    );
    expect(stResp.status()).toBe(200);
    const serviceTypes = await stResp.json();
    expect(serviceTypes.length).toBeGreaterThanOrEqual(2);
    const service1 = serviceTypes[0];
    const service2 = serviceTypes[1];

    // 6. Call real available slots for both service types on candidate date (within 7-day advance booking window)
    const targetDate = getCandidateBookingDate(2);
    const slots1Resp = await request.get(
      `/api/v1/slots/available?dealershipId=${dealershipId}&serviceTypeId=${service1.id}&date=${targetDate}`,
    );
    expect(slots1Resp.status()).toBe(200);
    const slots1 = await slots1Resp.json();
    const available1 = slots1.filter(
      (s: { available: boolean }) => s.available,
    );
    expect(available1.length).toBeGreaterThanOrEqual(1);

    const slots2Resp = await request.get(
      `/api/v1/slots/available?dealershipId=${dealershipId}&serviceTypeId=${service2.id}&date=${targetDate}`,
    );
    expect(slots2Resp.status()).toBe(200);
    const slots2 = await slots2Resp.json();
    const available2 = slots2.filter(
      (s: { available: boolean }) => s.available,
    );
    expect(available2.length).toBeGreaterThanOrEqual(1);

    // 7. Happy case: select 2 non-overlapping service jobs and submit booking
    const slot1 = available1[0];
    const slot2 =
      available2.find(
        (s: { startTime: string }) =>
          new Date(s.startTime).getTime() >= new Date(slot1.endTime).getTime(),
      ) || available2[available2.length - 1];

    // Every booking that commits is recorded at once and cancelled in the
    // finally block, so a failed assertion never leaves an active
    // appointment on a fixed window for the next run.
    const createdAppointments: string[] = [];
    try {
      const happyKey = randomUUID();
      const happyBookResp = await request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${cookie}`,
          "X-Idempotency-Key": happyKey,
        },
        data: {
          dealershipId,
          vehicleCategoryId: vehicleCategory.id,
          vehicleVin: testVin,
          serviceJobs: [
            {
              serviceTypeId: service1.id,
              bayId: "33333333-0000-0000-0000-000000000001",
              startTime: slot1.startTime,
              endTime: slot1.endTime,
            },
            {
              serviceTypeId: service2.id,
              bayId: "33333333-0000-0000-0000-000000000002",
              startTime: slot2.startTime,
              endTime: slot2.endTime,
            },
          ],
        },
      });

      expect(happyBookResp.status()).toBe(201);
      const happyAppointment = await happyBookResp.json();
      createdAppointments.push(happyAppointment.id);
      expect(happyAppointment.id).toBeTruthy();
      expect(happyAppointment.vehicleVin).toBe(testVin);
      expect(happyAppointment.status).toBe("CONFIRMED");
      expect(happyAppointment.serviceJobs).toHaveLength(2);

      // 8. Bad case A: Overlap with existing booking for SAME vehicle VIN -> PostgreSQL no_vehicle_overlap raises 409
      const overlapVinKey = randomUUID();
      const overlapVinResp = await request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${cookie}`,
          "X-Idempotency-Key": overlapVinKey,
        },
        data: {
          dealershipId,
          vehicleCategoryId: vehicleCategory.id,
          vehicleVin: testVin, // SAME vehicle VIN
          serviceJobs: [
            {
              serviceTypeId: service1.id,
              bayId: "33333333-0000-0000-0000-000000000001",
              startTime: slot1.startTime,
              endTime: slot1.endTime,
            },
          ],
        },
      });

      expect(overlapVinResp.status()).toBe(409);
      const vinProblem = await overlapVinResp.json();
      expect(vinProblem.status).toBe(409);
      expect(vinProblem.title).toBe("Vehicle schedule conflict");

      // 9. Bad case B: Overlap with existing booking for SAME bay -> PostgreSQL no_bay_overlap raises 409
      const overlapBayKey = randomUUID();
      const overlapBayResp = await request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${cookie}`,
          "X-Idempotency-Key": overlapBayKey,
        },
        data: {
          dealershipId,
          vehicleCategoryId: vehicleCategory.id,
          vehicleVin: testVin2, // DIFFERENT vehicle
          serviceJobs: [
            {
              serviceTypeId: service1.id,
              bayId: "33333333-0000-0000-0000-000000000001", // SAME bay and time as happy booking job 1
              startTime: slot1.startTime,
              endTime: slot1.endTime,
            },
          ],
        },
      });

      expect(overlapBayResp.status()).toBe(409);
      const bayProblem = await overlapBayResp.json();
      expect(bayProblem.status).toBe(409);
      expect(bayProblem.title).toBe("Workshop bay conflict");

      // 10. Recovery after collision eviction: re-fetch available slots and book a non-overlapping slot
      const refreshedSlotsResp = await request.get(
        `/api/v1/slots/available?dealershipId=${dealershipId}&serviceTypeId=${service2.id}&date=${targetDate}`,
      );
      expect(refreshedSlotsResp.status()).toBe(200);
      const refreshedSlots = await refreshedSlotsResp.json();
      const newAvailable = refreshedSlots.filter(
        (s: { available: boolean; startTime: string }) =>
          s.available &&
          new Date(s.startTime).getTime() > new Date(slot2.endTime).getTime(),
      );

      const recoverySlot =
        newAvailable.length > 0
          ? newAvailable[0]
          : refreshedSlots.find((s: { available: boolean }) => s.available);
      expect(recoverySlot).toBeTruthy();

      const recoveredKey = randomUUID();
      const recoveredBookResp = await request.post("/api/v1/appointments", {
        headers: {
          Cookie: `jwt=${cookie}`,
          "X-Idempotency-Key": recoveredKey,
        },
        data: {
          dealershipId,
          vehicleCategoryId: vehicleCategory.id,
          vehicleVin: testVin2,
          serviceJobs: [
            {
              serviceTypeId: service2.id,
              bayId: "33333333-0000-0000-0000-000000000002",
              startTime: recoverySlot.startTime,
              endTime: recoverySlot.endTime,
            },
          ],
        },
      });

      expect(recoveredBookResp.status()).toBe(201);
      const recoveredAppointment = await recoveredBookResp.json();
      createdAppointments.push(recoveredAppointment.id);
      expect(recoveredAppointment.id).toBeTruthy();
      expect(recoveredAppointment.status).toBe("CONFIRMED");
    } finally {
      // Cleanup: cancel every committed appointment so the probed windows
      // stay open for the next run. Each response is checked and reported,
      // and a failure never skips the remaining cancellations.
      for (const id of createdAppointments) {
        let cleanupResp: APIResponse;
        try {
          cleanupResp = await request.delete(`/api/v1/appointments/${id}`, {
            headers: { Cookie: `jwt=${cookie}` },
          });
        } catch (error) {
          console.warn(`[cleanup] cancel ${id} failed: ${error}`);
          continue;
        }
        if (cleanupResp.status() !== 200) {
          console.warn(
            `[cleanup] cancel ${id} answered ${cleanupResp.status()}`,
          );
        }
      }
    }
  });
});
