-- Retire the administrator account seeded by V2. Its password was published in
-- the repository, so every database migrated from this project shared the same
-- admin login. Only the untouched seed row (still carrying the V2 hash) is
-- removed; user_roles rows go with it via ON DELETE CASCADE.
--
-- Create the first administrator with the one-time bootstrap instead: set
-- BOOTSTRAP_ADMIN_USERNAME / BOOTSTRAP_ADMIN_PASSWORD / BOOTSTRAP_ADMIN_EMAIL
-- on first start (see AdminBootstrap).
DELETE FROM users
WHERE username = 'admin'
  AND password = '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.';
