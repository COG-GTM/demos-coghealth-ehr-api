-- Bulk PHI exports now require the dedicated PHI_EXPORT role. Grant it to
-- existing administrators so the export endpoints stay reachable.
INSERT INTO user_roles (user_id, role)
SELECT user_id, 'PHI_EXPORT' FROM user_roles WHERE role = 'ADMIN'
ON CONFLICT (user_id, role) DO NOTHING;
