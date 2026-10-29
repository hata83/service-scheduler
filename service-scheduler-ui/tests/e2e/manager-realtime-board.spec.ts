import { randomInt, randomUUID } from "node:crypto";

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
 * End-to-end: the manager schedule board updates in real time
 * (change manager-schedule-realtime).
 *
 * Flow:
 *   1. The manager logs in and opens the staff SSE stream.
 *   2. Two customers book at the same hour on two different bays. Both
 *      bookings commit, because the bay exclusion constraint only guards one
 *      bay per window.
 *   3. Both events reach the manager stream, so the board shows every
 *      booking without a refresh. The day view lists both blocks.
 *   4. One customer cancels. The cancelled event reaches the manager stream
 *      and the day view drops that block.
 *   5. The manager notification list holds every staff delivery, so the API
 *      answer matches the stream.
 */

/** One SSE event received on the manager stream. */
interface SseEvent {
  event: string;
  data: string;
}

/** The manager stream reader with its collected events. */
interface ManagerStream {
  events: SseEvent[];
  connected: Promise<void>;
  close: () => void;
}

/**
 * Parses one raw SSE block into an event. Comment lines such as the heartbeat
 * carry no event name and are skipped.
 */
function parseSseBlock(raw: string): SseEvent | null {
  let event = "message";
  const data: string[] = [];
  for (const line of raw.split("\n")) {
    if (line.startsWith(":")) {
      continue;
    }
    if (line.startsWith("event:")) {
      event = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      data.push(line.slice("data:".length).trim());
    }
  }
  if (data.length === 0 && event === "message") {
    return null;
  }
  return { event, data: data.join("\n") };
}

/**
 * Opens the SSE stream with the session cookie and collects every event.
 */
function openManagerStream(baseURL: string, cookie: string): ManagerStream {
  const events: SseEvent[] = [];
  const abort = new AbortController();

  const responsePromise = fetch(`${baseURL}/api/v1/notifications/stream`, {
    headers: {
      Cookie: `jwt=${cookie}`,
      Accept: "text/event-stream",
    },
    signal: abort.signal,
  });

  // The connected promise settles when the response headers arrive. The pump
  // promise reads the body until the client closes the stream.
  const connected = responsePromise.then((response) => {
    if (!response.ok || !response.body) {
      throw new Error(`stream answered ${response.status}`);
    }
  });

  const pump = responsePromise
    .then(async (response) => {
      if (!response.body) {
        return;
      }
      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = "";
      for (;;) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        let boundary = buffer.indexOf("\n\n");
        while (boundary >= 0) {
          const block = buffer.slice(0, boundary);
          buffer = buffer.slice(boundary + 2);
          const parsed = parseSseBlock(block);
          if (parsed) {
            events.push(parsed);
          }
          boundary = buffer.indexOf("\n\n");
        }
      }
    })
    .catch(() => {
      // The abort on close ends the read loop. The assertion reports the
      // missing event instead of the closed socket.
    });

  void pump;

  return {
    events,
    connected,
    close: () => abort.abort(),
  };
}

/**
 * Waits until the stream holds one event with the given name.
 */
async function waitForEvent(
  stream: ManagerStream,
  name: string,
  timeoutMs = 15000,
): Promise<SseEvent | null> {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const found = stream.events.find((event) => event.event === name);
    if (found) {
      return found;
    }
    if (Date.now() > deadline) {
      return null;
    }
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
}

test.describe("Manager real-time schedule board", () => {
  const DEMO_DEALERSHIP_ID = "00000000-0000-0000-0000-000000000001";
  const MANAGER_PHONE = "+84900000002";
  const STANDARD_SERVICE_ID = "22222222-0000-0000-0000-000000000002";
  const BAY_TWO_ID = "33333333-0000-0000-0000-000000000002";
  const BAY_THREE_ID = "33333333-0000-0000-0000-000000000003";

  type ApiContext = Awaited<ReturnType<typeof apiContext>>;

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
   * Returns the ISO instant of one hour in the afternoon of the given date in
   * Ho Chi Minh City timezone. The minute offset keeps every run on its own
   * window, so a leftover booking of an aborted run rarely blocks the next.
   */
  function hcmInstant(date: string, hour: number, minute: number): string {
    const [yyyy, mm, dd] = date.split("-").map(Number);
    const utcHour = hour - 7;
    return new Date(Date.UTC(yyyy, mm - 1, dd, utcHour, minute, 0)).toISOString();
  }

  /**
   * Logs a staff user in with the OTP flow and returns the session cookie.
   */
  async function staffSession(
    request: ApiContext,
    phone: string,
  ): Promise<string> {
    const code = await sendOtp(request, phone, "LOGIN");
    const verifyResp = await request.post("/api/v1/auth/otp/verify", {
      headers: {
        "X-Idempotency-Key": idempotencyKey("otp-verify-staff", phone),
      },
      data: { phone, code },
    });
    expect(verifyResp.status(), await verifyResp.text()).toBe(200);
    return sessionCookie(verifyResp);
  }

  /**
   * Registers a fresh customer and returns the session cookie.
   */
  async function registerCustomer(request: ApiContext): Promise<string> {
    const phone = freshPhone();
    const otp = await sendOtp(request, phone, "REGISTER");
    const registerResp = await request.post("/api/v1/auth/register", {
      headers: { "X-Idempotency-Key": idempotencyKey("register", phone) },
      data: { phone, code: otp, firstName: "Board", lastName: "Viewer" },
    });
    expect(registerResp.status(), await registerResp.text()).toBe(201);
    return sessionCookie(registerResp);
  }

  /**
   * Books one service job on the given bay and window.
   */
  async function bookJob(
    request: ApiContext,
    cookie: string,
    bayId: string,
    startTime: string,
    endTime: string,
  ): Promise<APIResponse> {
    const categoryResp = await request.get(
      `/api/v1/dealerships/${DEMO_DEALERSHIP_ID}/vehicle-categories`,
    );
    expect(categoryResp.status()).toBe(200);
    const categories = await categoryResp.json();
    const category = categories[0];
    expect(category, "a vehicle category must exist").toBeTruthy();

    return request.post("/api/v1/appointments", {
      headers: {
        Cookie: `jwt=${cookie}`,
        "X-Idempotency-Key": randomUUID(),
      },
      data: {
        dealershipId: DEMO_DEALERSHIP_ID,
        vehicleCategoryId: category.id,
        vehicleVin: freshVin(),
        serviceJobs: [
          {
            serviceTypeId: STANDARD_SERVICE_ID,
            bayId,
            startTime,
            endTime,
          },
        ],
      },
    });
  }

  /**
   * Cancels a recorded appointment. The deletion is best effort, because the
   * flow under test may already have cancelled it on the happy path.
   */
  async function cancelLeftover(leftover: Leftover): Promise<void> {
    let resp: APIResponse;
    try {
      resp = await leftover.request.delete(`/api/v1/appointments/${leftover.id}`, {
        headers: { Cookie: `jwt=${leftover.cookie}` },
      });
    } catch (error) {
      console.warn(`[cleanup] cancel ${leftover.id} failed: ${error}`);
      return;
    }
    if (resp.status() !== 200) {
      console.warn(`[cleanup] cancel ${leftover.id} answered ${resp.status()}`);
    }
  }

  test("two concurrent bookings on different bays reach the manager board in real time", async ({
    playwright,
    baseURL,
  }) => {
    test.setTimeout(90000);
    const manager = await apiContext(playwright, baseURL!);
    const customerA = await apiContext(playwright, baseURL!);
    const customerB = await apiContext(playwright, baseURL!);

    const managerCookie = await staffSession(manager, MANAGER_PHONE);
    const cookieA = await registerCustomer(customerA);
    const cookieB = await registerCustomer(customerB);

    // --- Step 1: the manager opens the staff stream ---
    const stream = openManagerStream(baseURL!, managerCookie);
    await stream.connected;

    const leftovers: Leftover[] = [];
    try {
      // --- Step 2: two bookings at the same hour on two different bays ---
      const targetDate = hcmDate(5);
      const minuteOffset = randomInt(0, 30);
      const startTime = hcmInstant(targetDate, 14, minuteOffset);
      const endTime = hcmInstant(targetDate, 15, minuteOffset);

      const [respA, respB] = await Promise.all([
        bookJob(customerA, cookieA, BAY_TWO_ID, startTime, endTime),
        bookJob(customerB, cookieB, BAY_THREE_ID, startTime, endTime),
      ]);

      expect(respA.status(), await respA.text()).toBe(201);
      expect(respB.status(), await respB.text()).toBe(201);

      const appointmentA = (await respA.json()) as { id: string; status: string };
      const appointmentB = (await respB.json()) as { id: string; status: string };
      leftovers.push(
        { id: appointmentA.id, request: customerA, cookie: cookieA },
        { id: appointmentB.id, request: customerB, cookie: cookieB },
      );
      expect(appointmentA.id).not.toBe(appointmentB.id);

      // --- Step 3: both events reach the manager stream ---
      const confirmedA = await waitForEvent(stream, "appointment.confirmed");
      expect(
        confirmedA,
        `the manager stream must receive the first booking, saw ${JSON.stringify(stream.events)}`,
      ).toBeTruthy();
      const confirmedB = await waitForEvent(
        stream,
        "appointment.confirmed",
        15000,
      );
      expect(confirmedB, "the manager stream must receive the second booking").toBeTruthy();

      const receivedIds = stream.events
        .filter((event) => event.event === "appointment.confirmed")
        .map((event) => {
          const payload = JSON.parse(event.data) as { appointmentId: string };
          return payload.appointmentId;
        });
      expect(receivedIds).toContain(appointmentA.id);
      expect(receivedIds).toContain(appointmentB.id);

      // --- Step 4: the day view lists both bookings without a refresh ---
      const dayResp = await manager.get(
        `/api/v1/schedule/day?date=${targetDate}`,
        { headers: { Cookie: `jwt=${managerCookie}` } },
      );
      expect(dayResp.status(), await dayResp.text()).toBe(200);
      const day = await dayResp.json();
      expect(day.dealershipId).toBe(DEMO_DEALERSHIP_ID);
      expect(day.bays).toBeTruthy();

      const blocks = (day.bays as { bayId: string; jobs: { appointmentId: string }[] }[])
        .flatMap((bay) => bay.jobs)
        .map((job) => job.appointmentId);
      expect(blocks).toContain(appointmentA.id);
      expect(blocks).toContain(appointmentB.id);

      // --- Step 5: a cancellation reaches the stream and drops the block ---
      const cancelResp = await customerA.delete(
        `/api/v1/appointments/${appointmentA.id}`,
        { headers: { Cookie: `jwt=${cookieA}` } },
      );
      expect(cancelResp.status(), await cancelResp.text()).toBe(200);
      const cancelledIndex = leftovers.findIndex((l) => l.id === appointmentA.id);
      if (cancelledIndex >= 0) {
        leftovers.splice(cancelledIndex, 1);
      }

      const cancelledEvent = await waitForEvent(stream, "appointment.cancelled");
      expect(
        cancelledEvent,
        "the manager stream must receive the cancellation",
      ).toBeTruthy();
      const cancelledPayload = JSON.parse(cancelledEvent!.data) as {
        appointmentId: string;
        status: string;
      };
      expect(cancelledPayload.appointmentId).toBe(appointmentA.id);
      expect(cancelledPayload.status).toBe("CANCELLED");

      const dayAfterResp = await manager.get(
        `/api/v1/schedule/day?date=${targetDate}`,
        { headers: { Cookie: `jwt=${managerCookie}` } },
      );
      expect(dayAfterResp.status()).toBe(200);
      const dayAfter = await dayAfterResp.json();
      const blocksAfter = (
        dayAfter.bays as { bayId: string; jobs: { appointmentId: string }[] }[]
      )
        .flatMap((bay) => bay.jobs)
        .map((job) => job.appointmentId);
      expect(blocksAfter).not.toContain(appointmentA.id);
      expect(blocksAfter).toContain(appointmentB.id);

      // --- Step 6: the notification list API holds every staff delivery ---
      const notificationsResp = await manager.get("/api/v1/notifications/my", {
        headers: { Cookie: `jwt=${managerCookie}` },
      });
      expect(notificationsResp.status(), await notificationsResp.text()).toBe(200);
      const notifications = (await notificationsResp.json()) as {
        eventType: string;
        recipient: string;
        appointmentId: string | null;
      }[];
      expect(Array.isArray(notifications)).toBe(true);
      expect(
        notifications.some(
          (record) =>
            record.eventType === "appointment.confirmed" &&
            record.appointmentId === appointmentA.id,
        ),
        "the notification list must carry the booking delivery",
      ).toBe(true);
      expect(
        notifications.some(
          (record) =>
            record.eventType === "appointment.cancelled" &&
            record.appointmentId === appointmentA.id,
        ),
        "the notification list must carry the cancellation delivery",
      ).toBe(true);
      expect(
        notifications.some((record) => record.recipient === MANAGER_PHONE),
        "the staff delivery must name the manager recipient",
      ).toBe(true);
    } finally {
      stream.close();
      for (const leftover of leftovers) {
        await cancelLeftover(leftover);
      }
    }
  });

  test("the customer role cannot read the manager schedule", async ({
    playwright,
    baseURL,
  }) => {
    const request = await apiContext(playwright, baseURL!);
    const cookie = await registerCustomer(request);

    const dayResp = await request.get("/api/v1/schedule/day?date=2026-11-20", {
      headers: { Cookie: `jwt=${cookie}` },
    });
    expect(dayResp.status()).toBe(403);

    const historyResp = await request.get("/api/v1/schedule/history", {
      headers: { Cookie: `jwt=${cookie}` },
    });
    expect(historyResp.status()).toBe(403);
  });
});
