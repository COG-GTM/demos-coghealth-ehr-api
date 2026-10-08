-- V4__provider_schedule_and_hr_mapping.sql
-- Provider <-> HR identity, OR block schedule, HR cost center -> department mapping (hr-pto-sync interface)

CREATE TABLE provider_hr_identity (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    hr_employee_id VARCHAR(20) NOT NULL,
    npi VARCHAR(10),
    hr_last_name VARCHAR(100),
    hr_first_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_provider_hr_identity_emp ON provider_hr_identity(hr_employee_id);

CREATE TABLE or_block (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    department_id INTEGER NOT NULL,
    weekday VARCHAR(3) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    room VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_or_block_provider ON or_block(provider_id);

CREATE TABLE department_cost_center_map (
    id BIGSERIAL PRIMARY KEY,
    cost_center VARCHAR(20) NOT NULL,
    department_id INTEGER NOT NULL,
    department_name VARCHAR(100),
    effective_from DATE NOT NULL,
    effective_to DATE
);

CREATE INDEX idx_dept_cc_map_cc ON department_cost_center_map(cost_center);

-- Perioperative surgeons
INSERT INTO providers (npi, first_name, last_name, credentials, provider_type, specialty, department, email, phone_office, active, created_at, updated_at, version) VALUES
('1932405817', 'Elena', 'Marquez', 'MD', 'PHYSICIAN', 'General Surgery', 'Main OR - Tower', 'elena.marquez@medchart.local', '555-0210', true, '2017-09-05 09:00:00', '2026-01-12 10:00:00', 0),
('1750389264', 'Samuel', 'Okafor', 'MD', 'PHYSICIAN', 'Cardiothoracic Surgery', 'Cardiac OR', 'samuel.okafor@medchart.local', '555-0211', true, '2019-02-18 09:00:00', '2026-01-12 10:00:00', 0),
('1487296350', 'Priya', 'Raman', 'MD', 'PHYSICIAN', 'Orthopedic Surgery', 'Ambulatory Surgery - North', 'priya.raman@medchart.local', '555-0212', true, '2016-07-11 09:00:00', '2026-01-12 10:00:00', 0),
('1619072483', 'Daniel', 'Whitfield', 'MD', 'PHYSICIAN', 'Gastroenterology', 'Endoscopy Suite', 'daniel.whitfield@medchart.local', '555-0213', true, '2020-10-01 09:00:00', '2026-01-12 10:00:00', 0);

-- HR identities (Workday employee ids). E119901 is a second Workday record for Marquez carried over from the 2021 HR migration; NPI was never populated on it.
INSERT INTO provider_hr_identity (provider_id, hr_employee_id, npi, hr_last_name, hr_first_name) VALUES
((SELECT id FROM providers WHERE npi = '1932405817'), 'E104422', '1932405817', 'Marquez', 'Elena'),
((SELECT id FROM providers WHERE npi = '1932405817'), 'E119901', NULL, 'MARQUEZ', 'ELENA'),
((SELECT id FROM providers WHERE npi = '1750389264'), 'E108913', '1750389264', 'Okafor', 'Samuel'),
((SELECT id FROM providers WHERE npi = '1487296350'), 'E101177', '1487296350', 'Raman', 'Priya'),
((SELECT id FROM providers WHERE npi = '1619072483'), 'E109640', '1619072483', 'Whitfield', 'Daniel');

-- OR block schedule
INSERT INTO or_block (provider_id, department_id, weekday, start_time, end_time, room) VALUES
((SELECT id FROM providers WHERE npi = '1932405817'), 2130, 'TUE', '07:00', '15:00', 'OR-4'),
((SELECT id FROM providers WHERE npi = '1750389264'), 2132, 'WED', '07:30', '13:30', 'COR-2'),
((SELECT id FROM providers WHERE npi = '1487296350'), 2131, 'THU', '07:00', '12:00', 'ASC-1'),
((SELECT id FROM providers WHERE npi = '1619072483'), 2140, 'MON', '08:00', '12:00', 'ENDO-3');

-- HR cost center -> EHR department
INSERT INTO department_cost_center_map (cost_center, department_id, department_name, effective_from, effective_to) VALUES
('CC-200-5410', 2130, 'Main OR - Tower', '2019-01-01', NULL),
('CC-200-5411', 2131, 'Ambulatory Surgery - North', '2019-01-01', NULL),
('CC-200-5412', 2132, 'Cardiac OR', '2019-01-01', NULL),
('CC-200-5420', 2140, 'Endoscopy Suite', '2019-01-01', NULL);
