-- V3__chronic_conditions_tables.sql
-- Database tables for chronic conditions, medication adherence, and diabetes management
-- Matches JPA entities in domain/chronic/

-- Chronic condition tracking for disease management programs
CREATE TABLE chronic_conditions (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    condition_type VARCHAR(50) NOT NULL,
    icd10_code VARCHAR(20),
    diagnosis_date DATE,
    severity VARCHAR(20),
    status VARCHAR(20),
    notes VARCHAR(2000),
    last_review_date DATE,
    next_review_date DATE,
    managing_provider_id BIGINT REFERENCES providers(id),
    enrolled_in_program BOOLEAN NOT NULL DEFAULT FALSE,
    program_enrollment_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chronic_condition_patient ON chronic_conditions(patient_id);
CREATE INDEX idx_chronic_condition_type ON chronic_conditions(condition_type);
CREATE INDEX idx_chronic_condition_status ON chronic_conditions(status);

-- Medication adherence tracking for chronic conditions
CREATE TABLE medication_adherence (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    medication_order_id BIGINT NOT NULL REFERENCES medication_orders(id),
    chronic_condition_id BIGINT REFERENCES chronic_conditions(id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    pdc_score DECIMAL(5,4),
    days_supply INTEGER,
    days_covered INTEGER,
    refills_on_time INTEGER,
    refills_late INTEGER,
    refills_missed INTEGER,
    adherence_status VARCHAR(30),
    last_fill_date DATE,
    next_fill_due DATE,
    pharmacy_npi VARCHAR(255),
    pharmacy_name VARCHAR(255),
    alert_sent BOOLEAN NOT NULL DEFAULT FALSE,
    alert_sent_date TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_med_adherence_patient ON medication_adherence(patient_id);
CREATE INDEX idx_med_adherence_order ON medication_adherence(medication_order_id);
CREATE INDEX idx_med_adherence_condition ON medication_adherence(chronic_condition_id);
CREATE INDEX idx_med_adherence_status ON medication_adherence(adherence_status);

-- Diabetes-specific management tracking
CREATE TABLE diabetes_management (
    id BIGSERIAL PRIMARY KEY,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    chronic_condition_id BIGINT NOT NULL REFERENCES chronic_conditions(id),
    last_hba1c_value DECIMAL(4,1),
    last_hba1c_date DATE,
    target_hba1c DECIMAL(4,1),
    hba1c_control_status VARCHAR(20),
    uses_cgm BOOLEAN NOT NULL DEFAULT FALSE,
    cgm_device_type VARCHAR(255),
    avg_daily_glucose DECIMAL(5,1),
    time_in_range_percent DECIMAL(5,2),
    on_insulin BOOLEAN NOT NULL DEFAULT FALSE,
    insulin_regimen VARCHAR(255),
    uses_insulin_pump BOOLEAN NOT NULL DEFAULT FALSE,
    pump_type VARCHAR(255),
    last_eye_exam_date DATE,
    last_foot_exam_date DATE,
    last_nephropathy_screen_date DATE,
    has_retinopathy BOOLEAN,
    has_neuropathy BOOLEAN,
    has_nephropathy BOOLEAN,
    statin_prescribed BOOLEAN NOT NULL DEFAULT FALSE,
    ace_arb_prescribed BOOLEAN NOT NULL DEFAULT FALSE,
    last_lipid_panel_date DATE,
    last_bp_reading VARCHAR(255),
    last_bp_date DATE,
    completed_dsme BOOLEAN NOT NULL DEFAULT FALSE,
    dsme_completion_date DATE,
    has_nutritionist BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_diabetes_mgmt_patient ON diabetes_management(patient_id);
CREATE INDEX idx_diabetes_mgmt_condition ON diabetes_management(chronic_condition_id);
