-- R: demo confirmed appointments fixtures on next days 1, 2, and 3 (repeatable migration).
-- Seeds sample appointments in CONFIRMED status with service jobs and technician assignments.
-- Dynamically targets Day +1, Day +2, and Day +3 relative to (now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date.

INSERT INTO appointments (
  id, dealership_id, customer_id, vehicle_category_id, vehicle_vin,
  start_time, end_time, status, estimate_cost, total_cost,
  idempotency_key, version, created_by, updated_by
) VALUES
  (
    '44444444-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000005',
    '11111111-0000-0000-0000-000000000001',
    '1HGCR2F83HA000001',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '1 day' + (TIME '08:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '1 day' + (TIME '09:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    'CONFIRMED',
    150.00,
    150.00,
    '44444444-0000-0000-0000-100000000001',
    0,
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  ),
  (
    '44444444-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000005',
    '11111111-0000-0000-0000-000000000002',
    '1HGCR2F83HA000002',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '2 days' + (TIME '09:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '2 days' + (TIME '10:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    'CONFIRMED',
    180.00,
    180.00,
    '44444444-0000-0000-0000-100000000002',
    0,
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  ),
  (
    '44444444-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000005',
    '11111111-0000-0000-0000-000000000004',
    '1HGCR2F83HA000003',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '3 days' + (TIME '10:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '3 days' + (TIME '11:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    'CONFIRMED',
    160.00,
    160.00,
    '44444444-0000-0000-0000-100000000003',
    0,
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  )
ON CONFLICT (id) DO UPDATE SET
  start_time = EXCLUDED.start_time,
  end_time = EXCLUDED.end_time;

INSERT INTO service_jobs (
  id, appointment_id, service_type_id, bay_id,
  start_time, end_time, status, base_minutes, estimate_cost, final_cost,
  created_by, updated_by
) VALUES
  (
    '66666666-0000-0000-0000-000000000001',
    '44444444-0000-0000-0000-000000000001',
    '22222222-0000-0000-0000-000000000002',
    '33333333-0000-0000-0000-000000000002',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '1 day' + (TIME '08:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '1 day' + (TIME '09:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    'PENDING',
    60,
    150.00,
    150.00,
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  ),
  (
    '66666666-0000-0000-0000-000000000002',
    '44444444-0000-0000-0000-000000000002',
    '22222222-0000-0000-0000-000000000002',
    '33333333-0000-0000-0000-000000000002',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '2 days' + (TIME '09:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '2 days' + (TIME '10:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    'PENDING',
    60,
    180.00,
    180.00,
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  ),
  (
    '66666666-0000-0000-0000-000000000003',
    '44444444-0000-0000-0000-000000000003',
    '22222222-0000-0000-0000-000000000002',
    '33333333-0000-0000-0000-000000000003',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '3 days' + (TIME '10:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '3 days' + (TIME '11:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    'PENDING',
    60,
    160.00,
    160.00,
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  )
ON CONFLICT (id) DO UPDATE SET
  start_time = EXCLUDED.start_time,
  end_time = EXCLUDED.end_time;

INSERT INTO job_technician_assignments (
  id, job_id, technician_id, role,
  assigned_start_time, assigned_end_time,
  created_by, updated_by
) VALUES
  (
    '77777777-0000-0000-0000-000000000001',
    '66666666-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000004',
    'PRIMARY_TECH',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '1 day' + (TIME '08:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '1 day' + (TIME '09:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  ),
  (
    '77777777-0000-0000-0000-000000000002',
    '66666666-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000004',
    'PRIMARY_TECH',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '2 days' + (TIME '09:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '2 days' + (TIME '10:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  ),
  (
    '77777777-0000-0000-0000-000000000003',
    '66666666-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000004',
    'PRIMARY_TECH',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '3 days' + (TIME '10:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    ((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date + INTERVAL '3 days' + (TIME '11:00:00' - TIME '00:00:00')) AT TIME ZONE 'Asia/Ho_Chi_Minh',
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000002'
  )
ON CONFLICT (id) DO UPDATE SET
  assigned_start_time = EXCLUDED.assigned_start_time,
  assigned_end_time = EXCLUDED.assigned_end_time;
