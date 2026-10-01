-- Link application users to the provider they practice as, so patient access
-- can be authorized against the provider's care relationships.
ALTER TABLE users ADD COLUMN provider_id BIGINT REFERENCES providers(id);

CREATE INDEX idx_users_provider_id ON users(provider_id);
