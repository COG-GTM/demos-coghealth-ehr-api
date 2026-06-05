-- COG-151: HIPAA-compliant batch patient data export with audit trail

CREATE TABLE data_exports (
    id BIGSERIAL PRIMARY KEY,
    export_reference VARCHAR(36) UNIQUE NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(100),
    reason VARCHAR(30) NOT NULL,
    reason_details VARCHAR(500),
    format VARCHAR(10) NOT NULL,
    patient_count INTEGER NOT NULL,
    de_identified BOOLEAN NOT NULL DEFAULT FALSE,
    file_path VARCHAR(500),
    file_size_bytes BIGINT,
    download_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ip_address VARCHAR(50)
);

CREATE INDEX idx_export_reference ON data_exports(export_reference);
CREATE INDEX idx_export_user ON data_exports(user_id);
CREATE INDEX idx_export_created ON data_exports(created_at);
CREATE INDEX idx_export_expires ON data_exports(expires_at);
