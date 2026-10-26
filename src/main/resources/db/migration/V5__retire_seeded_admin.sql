-- Retire the administrator account seeded by V2. Its password was published in
-- the repository, so every database migrated from this project shared the same
-- admin login.
--
-- An untouched seed row is deleted (user_roles rows go with it via ON DELETE
-- CASCADE). A row that was customised but still carries the V2 hash keeps its
-- id, roles and other data, but is disabled, its password replaced with a value
-- that can never match, and its username/email moved to archival values so the
-- original identity can be reused by the bootstrap administrator.
--
-- Create the first administrator with the one-time bootstrap instead: set
-- BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD / BOOTSTRAP_ADMIN_EMAIL
-- on first start (see AdminBootstrap).
DELETE FROM users
WHERE username = 'admin'
  AND password = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.'
  AND email = 'admin@medchart.com'
  AND first_name = 'System'
  AND last_name = 'Administrator'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles r
      WHERE r.user_id = users.id AND r.role <> 'ADMIN'
  );

UPDATE users
SET enabled = FALSE,
    password = '{retired}seeded-admin-credential',
    updated_at = CURRENT_TIMESTAMP
WHERE password = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';

-- Archival identities are only applied when free, so a pre-existing account with
-- the same name cannot make this migration fail.
UPDATE users
SET username = 'retired-seed-admin-' || id
WHERE password = '{retired}seeded-admin-credential'
  AND NOT EXISTS (SELECT 1 FROM users o WHERE o.username = 'retired-seed-admin-' || users.id);

UPDATE users
SET email = 'retired-seed-admin-' || id || '@invalid'
WHERE password = '{retired}seeded-admin-credential'
  AND NOT EXISTS (SELECT 1 FROM users o WHERE o.email = 'retired-seed-admin-' || users.id || '@invalid');
