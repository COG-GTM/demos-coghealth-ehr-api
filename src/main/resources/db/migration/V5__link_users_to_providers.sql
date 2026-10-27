ALTER TABLE users ADD COLUMN provider_id BIGINT REFERENCES providers(id);

CREATE INDEX idx_users_provider_id ON users(provider_id);
