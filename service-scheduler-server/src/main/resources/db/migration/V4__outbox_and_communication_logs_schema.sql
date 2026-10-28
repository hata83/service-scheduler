-- V3: Outbox events and communication logs schema (SYSTEM_DESIGN §4.2.14, §4.2.15).

CREATE TABLE outbox_events (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type      VARCHAR(50) NOT NULL,
  aggregate_type  VARCHAR(50) NOT NULL,
  aggregate_id    UUID NOT NULL,
  payload         JSONB NOT NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING','SENT','FAILED')),
  retry_count     INT NOT NULL DEFAULT 0,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  sent_at         TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';

CREATE TABLE communication_logs (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  dealership_id   UUID NOT NULL REFERENCES dealerships(id),
  appointment_id  UUID REFERENCES appointments(id) ON DELETE SET NULL,
  customer_id     UUID REFERENCES profiles(user_id) ON DELETE SET NULL,
  channel         VARCHAR(30) NOT NULL,
  recipient       VARCHAR(255) NOT NULL,
  event_type      VARCHAR(100) NOT NULL,
  message_payload JSONB NOT NULL,
  delivery_status VARCHAR(30) NOT NULL DEFAULT 'DELIVERED',
  error_message   TEXT,
  dispatched_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_comm_logs_appointment ON communication_logs (appointment_id);
CREATE INDEX idx_comm_logs_dealership_dispatched ON communication_logs (dealership_id, dispatched_at DESC);
