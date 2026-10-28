# Design: Manager Real-Time Schedule Board

## Data Sources

No schema migration is needed. The board reads existing tables:

| Data | Source |
|---|---|
| Day schedule blocks | `service_jobs` joined with `appointments`, `workshop_bays`, `vehicle_categories`, `profiles` |
| History list | `appointments` with join filters |
| Booking detail | `appointments` plus `service_jobs` (notes, `estimate_cost`, `final_cost`), customer `profiles`, `users`, `communication_logs` |
| Staff fan-out | `outbox_events` via the existing `NotificationDispatchEvent` pipeline |
| Staff notification list | `communication_logs` where `recipient` equals the staff phone or `dealership_id` is in scope |

## Role Scope and Authorization

The JWT already carries `role`, `phone`, and `dealershipId` claims (SYSTEM_DESIGN 11.1).

| Role | Scope |
|---|---|
| MANAGER | Own `dealershipId` claim only. No `dealershipId` query parameter allowed. |
| ADMIN | May pass `dealershipId` to choose the dealership. Without the parameter, reads all dealerships. |
| CUSTOMER | HTTP 403 Problem Details on every schedule endpoint. |

A staff helper in `common/security` resolves the scope from `AuthPrincipal`. Controllers receive the
scope and pass it to services as typed DTO values.

## Schedule Day Response

`GET /api/v1/schedule/day?date=YYYY-MM-DD` returns per-bay columns:

```json
{
  "dealershipId": "00000000-0000-0000-0000-000000000001",
  "date": "2026-10-28",
  "bays": [
    {
      "bayId": "33333333-3333-3333-3333-333333333301",
      "bayName": "Bay 1",
      "bayType": "QUICK_LUBE",
      "status": "ACTIVE",
      "jobs": [
        {
          "jobId": "99999999-0000-0000-0000-000000000001",
          "appointmentId": "11111111-2222-3333-4444-555555555555",
          "status": "CONFIRMED",
          "startTime": "2026-10-28T08:00:00+07:00",
          "endTime": "2026-10-28T08:30:00+07:00",
          "customerName": "Demo Customer",
          "customerPhone": "+84900000004",
          "vehicleVin": "1HGCR2F83HA000001",
          "serviceName": "EXPRESS_LANE"
        }
      ]
    }
  ]
}
```

Unassigned or standalone rows do not exist in this design. Every service job holds a bay, so the bay
columns cover the whole day. Appointments with several jobs appear under each bay.

## Staff SSE Fan-Out

The existing flow stages one outbox row and dispatches one event to the customer phone. The change
adds a second dispatch leg after commit:

1. `NotificationService.handleAfterCommit` also builds a staff event with the same payload.
2. A new registry fan-out iterates connected staff sessions of `event.dealershipId()`.
3. Each staff dispatch writes its own `communication_logs` row. The log keeps `customer_id` of the
   booking and sets `recipient` to the staff phone.
4. `GET /api/v1/notifications/my` reads logs for a staff phone. A MANAGER receives events for its
   dealership. An ADMIN receives events for all dealerships.

The registry must know the role and dealership of each connection. `register` gains a
`StaffSession(dealershipId, phone)` map next to the existing phone map. The single-stream rule evicts
a staff connection exactly like a customer connection.

Event names on the manager board:

| Event | Trigger | Effect on board |
|---|---|---|
| `appointment.requested` | Booking created while auto-confirm is off | New REQUESTED block appears |
| `appointment.confirmed` | Booking created while auto-confirm is on, or manager confirms | New or updated CONFIRMED block |
| `appointment.cancelled` | Customer or system cancels | Block is removed |

The payload stays identical for customer and staff dispatches. The frontend refetches the affected
date grid or patches one block by `appointmentId`.

## History and Detail

`GET /api/v1/schedule/history` parameters: `status`, `from`, `to`, `bayId`, `customerPhone`,
`dealershipId` (ADMIN only), `page`, `size`. Default order is start time descending. Each item keeps
the unique `id` key rule from `appointment-history`.

`GET /api/v1/appointments/{id}` keeps the customer contract unchanged. For staff the response row
returns one extra object `staffView`:

```json
{
  "customerId": "00000000-0000-0000-0000-000000000005",
  "customerName": "Demo Customer",
  "customerPhone": "+84900000004",
  "vehicleVin": "1HGCR2F83HA000001",
  "vehicleCategory": "SEDAN",
  "jobs": [
    {
      "jobId": "99999999-0000-0000-0000-000000000001",
      "serviceName": "STANDARD_SERVICE",
      "bayName": "Bay 2",
      "status": "PENDING",
      "notes": ["Customer asked for tire check"],
      "estimateCost": 120.00,
      "finalCost": null
    }
  ],
  "communicationLogs": [
    { "eventType": "appointment.confirmed", "dispatchedAt": "2026-10-27T19:30:00+07:00" }
  ]
}
```

Feedback data lives in `service_jobs` notes plus the `estimate_cost` and `final_cost` columns. There
is no feedback table (user decision 2026-10-28, naturalized requirement D1).

## Package Plan

| Package | Work |
|---|---|
| `dev.hata.scheduler.scheduling` | `ScheduleController`, `ScheduleService`, schedule DTOs, history DTOs |
| `dev.hata.scheduler.appointment` | Staff detail enrichment in the appointment read path |
| `dev.hata.scheduler.notification` | Staff fan-out, registry staff map, staff log query |
| `dev.hata.scheduler.common.security` | Staff scope resolver |

ArchUnit rules hold: controllers reach services only, services take typed DTOs, and no feature
package imports another feature package.

## Diagram and Test Plan

- Archify sequence diagram `manager-schedule-board` in `docs/diagrams/src/system/`. The flow runs
  from customer booking to the staff board, including two concurrent bookings at the same time on
  different bays.
- Playwright spec `manager-realtime-board.spec.ts` covers: two concurrent bookings at the same hour
  on different bays shown without refresh, cancellation drop without refresh, SSE notification text,
  and the notified list fetched through `GET /api/v1/notifications/my`.
- Bruno files under `src/test/bruno/api/schedule/` cover the day view, history filters, staff detail,
  staff notifications, and role rejections.
