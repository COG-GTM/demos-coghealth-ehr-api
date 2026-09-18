DROP TABLE IF EXISTS encounters;
DROP TABLE IF EXISTS patients;

CREATE TABLE patients (
    id BIGINT PRIMARY KEY,
    mrn VARCHAR(20) NOT NULL,
    ssn VARCHAR(11),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    email VARCHAR(100),
    phone_home VARCHAR(20),
    phone_mobile VARCHAR(20),
    street1 VARCHAR(200),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(20),
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE encounters (
    id BIGINT PRIMARY KEY,
    encounter_number VARCHAR(30) NOT NULL,
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    provider_id BIGINT,
    attending_provider_id BIGINT,
    encounter_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    encounter_date DATE,
    encounter_date_time TIMESTAMP NOT NULL
);
