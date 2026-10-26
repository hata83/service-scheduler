-- V2: demo seed (design D4) — 1 dealership and 4 accounts with fixed IDs and phones.
-- Users insert first because dealerships.created_by references users(id);
-- staff and customer rows receive their dealership_id by back-fill afterwards.

INSERT INTO users (id, phone, role) VALUES
  ('00000000-0000-0000-0000-000000000002', '+84900000001', 'ADMIN'),
  ('00000000-0000-0000-0000-000000000003', '+84900000002', 'MANAGER'),
  ('00000000-0000-0000-0000-000000000004', '+84900000003', 'TECHNICIAN'),
  ('00000000-0000-0000-0000-000000000005', '+84900000004', 'CUSTOMER');

INSERT INTO dealerships (
  id, name, address, city, country, features, created_by, updated_by
) VALUES (
  '00000000-0000-0000-0000-000000000001',
  'Hata Demo Service Center',
  '1 Le Loi',
  'Ho Chi Minh City',
  'Vietnam',
  '{"rateLimits":{"otpSendPerPhonePurpose":5,"otpSendPerPhonePurposeWindowSeconds":600,"otpSendPerIp":50,"otpSendPerIpWindowSeconds":60,"otpVerifyPerIp":50,"otpVerifyPerIpWindowSeconds":60,"registerPerIp":20,"registerPerIpWindowSeconds":60}}'::jsonb,
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000002'
);

UPDATE users
   SET dealership_id = '00000000-0000-0000-0000-000000000001'
 WHERE role IN ('MANAGER', 'TECHNICIAN');

INSERT INTO technicians (
  user_id, dealership_id, employee_id, first_name, last_name, created_by, updated_by
) VALUES (
  '00000000-0000-0000-0000-000000000004',
  '00000000-0000-0000-0000-000000000001',
  'TEC-0001',
  'Demo',
  'Technician',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000002'
);

INSERT INTO technician_skills (
  technician_id, skill, level, created_by, updated_by
) VALUES (
  '00000000-0000-0000-0000-000000000004',
  'ENGINE',
  'TECHNICIAN',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000002'
);

INSERT INTO customers (
  user_id, dealership_id, first_name, last_name, created_by, updated_by
) VALUES (
  '00000000-0000-0000-0000-000000000005',
  NULL,
  'Demo',
  'Customer',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000002'
);
