-- V3__lab_result_import.sql
-- Support for bulk lab result import from external reference labs

CREATE TABLE lab_result_imports (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    file_size_bytes BIGINT,
    source_lab VARCHAR(100),
    status VARCHAR(30) NOT NULL,
    total_results INTEGER NOT NULL DEFAULT 0,
    matched_count INTEGER NOT NULL DEFAULT 0,
    unmatched_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    error_message VARCHAR(1000),
    imported_by VARCHAR(100),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_import_status ON lab_result_imports(status);
CREATE INDEX idx_import_date ON lab_result_imports(created_at);

CREATE TABLE unmatched_lab_results (
    id BIGSERIAL PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES lab_result_imports(id),
    patient_identifier VARCHAR(50),
    patient_first_name VARCHAR(100),
    patient_last_name VARCHAR(100),
    order_number VARCHAR(30),
    test_code VARCHAR(20) NOT NULL,
    test_name VARCHAR(200),
    value VARCHAR(100),
    numeric_value DECIMAL(10,4),
    unit VARCHAR(50),
    reference_range VARCHAR(100),
    flag VARCHAR(20),
    result_status VARCHAR(20) NOT NULL,
    result_date_time TIMESTAMP,
    performing_lab VARCHAR(100),
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    review_notes VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_unmatched_import ON unmatched_lab_results(import_id);
CREATE INDEX idx_unmatched_review ON unmatched_lab_results(review_status);
CREATE INDEX idx_unmatched_patient ON unmatched_lab_results(patient_identifier);
