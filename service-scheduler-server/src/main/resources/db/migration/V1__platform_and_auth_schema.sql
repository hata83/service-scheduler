-- V1: Phase 1 platform + auth schema (design D4, SYSTEM_DESIGN §4.2).
-- PostgreSQL-only DDL. The application generates UUIDs, so PKs carry no DB default.
-- users is global; profiles centralizes personal info for all 4 roles;
-- employees models ADMIN (global, dealership_id IS NULL) and MANAGER/TECHNICIAN (local, dealership_id IS NOT NULL);
-- technicians extends employees with career level; technician_skills holds multi-skill with level.

CREATE TABLE users (
  id              UUID PRIMARY KEY,
  phone           VARCHAR(30) UNIQUE NOT NULL,
  email           VARCHAR(255),
  type            VARCHAR(20) NOT NULL CHECK (type IN ('CUSTOMER','EMPLOYEE')),
  is_active       BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by      UUID REFERENCES users(id),
  updated_by      UUID REFERENCES users(id)
);

CREATE TABLE profiles (
  user_id        UUID PRIMARY KEY REFERENCES users(id),
  first_name     VARCHAR(100) NOT NULL,
  last_name      VARCHAR(100) NOT NULL,
  locale         VARCHAR(10) NOT NULL DEFAULT 'en_US',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by     UUID REFERENCES users(id),
  updated_by     UUID REFERENCES users(id)
);

CREATE TABLE dealerships (
  id                           UUID PRIMARY KEY,
  name                         VARCHAR(255) NOT NULL,
  address                      VARCHAR(500),
  city                         VARCHAR(100),
  country                      VARCHAR(100),
  timezone                     VARCHAR(50) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
  features                     JSONB DEFAULT '{}'::jsonb,
  slot_interval_minutes        INT NOT NULL DEFAULT 30 CHECK (slot_interval_minutes IN (15,30,45,60,90,120)),
  open_time                    TIME NOT NULL DEFAULT '08:00',
  close_time                   TIME NOT NULL DEFAULT '17:30',
  lunch_start                  TIME,
  lunch_end                    TIME,
  max_advance_booking_days     INT NOT NULL DEFAULT 7,
  dealership_cache_ttl_seconds INT NOT NULL DEFAULT 259200,
  slot_cache_ttl_seconds       INT NOT NULL DEFAULT 3600,
  auto_confirm_appointments    BOOLEAN NOT NULL DEFAULT FALSE,
  is_active                    BOOLEAN NOT NULL DEFAULT TRUE,
  created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by                   UUID NOT NULL REFERENCES users(id),
  updated_by                   UUID NOT NULL REFERENCES users(id)
);

CREATE TABLE employees (
  user_id        UUID PRIMARY KEY REFERENCES profiles(user_id),
  employee_id    VARCHAR(50) UNIQUE NOT NULL,
  role           VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN','MANAGER','TECHNICIAN')),
  dealership_id  UUID REFERENCES dealerships(id),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by     UUID REFERENCES users(id),
  updated_by     UUID REFERENCES users(id),
  CONSTRAINT chk_employee_dealership_scope CHECK (
    (role = 'ADMIN' AND dealership_id IS NULL) OR
    (role IN ('MANAGER','TECHNICIAN') AND dealership_id IS NOT NULL)
  )
);

CREATE TABLE technicians (
  user_id        UUID PRIMARY KEY REFERENCES employees(user_id),
  level          VARCHAR(20) NOT NULL CHECK (level IN ('TRAINEE','JUNIOR','TECHNICIAN','SENIOR','MASTER')),
  is_available   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by     UUID REFERENCES users(id),
  updated_by     UUID REFERENCES users(id)
);

CREATE TABLE technician_skills (
  technician_id  UUID NOT NULL REFERENCES technicians(user_id),
  skill          VARCHAR(30) NOT NULL CHECK (skill IN (
                   'ENGINE','TRANSMISSION','ELECTRICAL_GENERAL','EV_SYSTEMS',
                   'ADAS_CALIBRATION','DIAGNOSTICIAN','BRAKES','ALIGNMENT',
                   'BODYWORK','WARRANTY_SPECIALIST'
                 )),
  level          VARCHAR(20) NOT NULL CHECK (level IN ('TRAINEE','JUNIOR','TECHNICIAN','SENIOR','MASTER')),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by     UUID REFERENCES users(id),
  updated_by     UUID REFERENCES users(id),
  PRIMARY KEY (technician_id, skill)
);
