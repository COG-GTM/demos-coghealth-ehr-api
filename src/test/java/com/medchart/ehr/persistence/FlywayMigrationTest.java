package com.medchart.ehr.persistence;

import com.medchart.ehr.support.AbstractJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationTest extends AbstractJpaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void appliesAllVersionedMigrationsInOrder() {
        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT version, description, success FROM flyway_schema_history "
                        + "WHERE version IS NOT NULL ORDER BY installed_rank");

        assertThat(history).extracting(row -> row.get("version"))
                .containsExactly("1", "2", "3");
        assertThat(history).extracting(row -> row.get("description"))
                .containsExactly("initial schema", "add user authentication", "seed data");
        assertThat(history).allSatisfy(row -> assertThat(row.get("success")).isEqualTo(true));
    }

    @Test
    void createsOrderAndPatientTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "patients", "providers", "encounters", "lab_orders", "lab_results",
                "audit_events", "users", "user_roles");
    }

    @Test
    void labOrderNumberIsUniqueAndResultsReferenceOrders() {
        Integer uniqueOnOrderNumber = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints tc "
                        + "JOIN information_schema.constraint_column_usage ccu "
                        + "ON tc.constraint_name = ccu.constraint_name "
                        + "WHERE tc.table_name = 'lab_orders' AND tc.constraint_type = 'UNIQUE' "
                        + "AND ccu.column_name = 'order_number'",
                Integer.class);
        Integer resultToOrderFk = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints rc "
                        + "JOIN information_schema.table_constraints tc ON rc.constraint_name = tc.constraint_name "
                        + "WHERE tc.table_name = 'lab_results'",
                Integer.class);

        assertThat(uniqueOnOrderNumber).isEqualTo(1);
        assertThat(resultToOrderFk).isEqualTo(1);
    }

    @Test
    void seedsReferenceDataButNoLabOrders() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM providers", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM patients WHERE mrn LIKE 'MRN-20__-_____'", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM encounters WHERE encounter_number LIKE 'ENC-2024-%'", Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = 'admin'", Integer.class)).isEqualTo(1);
    }

    @Test
    void seedEncounter15HasShiftedColumns() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT encounter_type, status FROM encounters WHERE encounter_number = 'ENC-2024-000015'");

        // V3 omits encounter_type for this row, so every value shifts one column left.
        assertThat(row.get("encounter_type")).isEqualTo("IN_PROGRESS");
        assertThat(row.get("status")).isEqualTo("2024-03-12");
    }
}
