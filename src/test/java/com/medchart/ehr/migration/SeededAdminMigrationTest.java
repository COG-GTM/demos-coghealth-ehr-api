package com.medchart.ehr.migration;

import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the auth migrations (V2 creates and seeds users, V5 retires the seed) on H2.
 * V1/V3 use PostgreSQL-only syntax, so only the users-table migrations are copied in.
 */
class SeededAdminMigrationTest {

    private static final String SEEDED_HASH = "$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2uheWG/igi.";
    private static final String V2 = "V2__add_user_authentication.sql";
    private static final String V5 = "V5__retire_seeded_admin.sql";

    @TempDir
    Path migrations;

    private final JdbcTemplate jdbc = new JdbcTemplate(h2());

    private static JdbcDataSource h2() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        return ds;
    }

    private void migrate(String... scripts) throws IOException {
        for (String script : scripts) {
            try (InputStream in = getClass().getResourceAsStream("/db/migration/" + script)) {
                Files.copy(in, migrations.resolve(script));
            }
        }
        Flyway.configure()
                .dataSource(jdbc.getDataSource())
                .locations("filesystem:" + migrations)
                .load()
                .migrate();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void freshDatabaseHasNoSeededAdmin() throws IOException {
        migrate(V2, V5);

        assertThat(count("SELECT COUNT(*) FROM users WHERE username = 'admin'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE role = 'ADMIN'")).isZero();
    }

    @Test
    void removesSeededAdminFromAlreadyMigratedDatabase() throws IOException {
        migrate(V2);
        assertThat(count("SELECT COUNT(*) FROM users WHERE password = ?", SEEDED_HASH)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE role = 'ADMIN'")).isEqualTo(1);

        migrate(V5);

        assertThat(count("SELECT COUNT(*) FROM users WHERE password = ?", SEEDED_HASH)).isZero();
        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE role = 'ADMIN'")).isZero();
    }

    @Test
    void keepsAdminWhosePasswordWasRotated() throws IOException {
        migrate(V2);
        jdbc.update("UPDATE users SET password = 'rotated-hash' WHERE username = 'admin'");

        migrate(V5);

        assertThat(count("SELECT COUNT(*) FROM users WHERE username = 'admin'")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM user_roles WHERE role = 'ADMIN'")).isEqualTo(1);
    }
}
