package com.medchart.ehr.persistence;

import com.medchart.ehr.domain.order.LabOrder;
import com.medchart.ehr.domain.order.LabResult;
import com.medchart.ehr.domain.order.OrderPriority;
import com.medchart.ehr.domain.order.OrderStatus;
import com.medchart.ehr.domain.order.ResultFlag;
import com.medchart.ehr.domain.order.ResultStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.support.AbstractJpaTest;
import org.hibernate.exception.SQLGrammarException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.PersistenceException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LabOrder.icd10Code is mapped by Spring's naming strategy to column "icd10code", but V1 creates
 * "icd10_code". Every JPA read or write of LabOrder therefore fails on the current code; these tests
 * pin that down and cover what does work (LabResult rows and the lab_orders table itself via SQL).
 */
class LabOrderPersistenceTest extends AbstractJpaTest {

    private static final String MISSING_COLUMN = "column \"icd10code\" of relation \"lab_orders\" does not exist";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistingLabOrderFailsOnUnmappedIcd10Column() {
        LabOrder order = LabOrder.builder()
                .orderNumber("LAB-RT-0001")
                .patient(em.find(Patient.class, 1L))
                .orderingProvider(em.find(Provider.class, 1L))
                .orderDateTime(LocalDateTime.of(2024, 4, 1, 9, 0))
                .status(OrderStatus.PENDING)
                .priority(OrderPriority.ROUTINE)
                .testCode("CMP")
                .testName("Comprehensive metabolic panel")
                .build();

        assertThatThrownBy(() -> em.persistAndFlush(order))
                .isInstanceOf(PersistenceException.class)
                .hasCauseInstanceOf(SQLGrammarException.class)
                .satisfies(e -> assertThat(rootMessage(e)).contains(MISSING_COLUMN));
    }

    @Test
    void loadingLabOrderFailsOnUnmappedIcd10Column() {
        long orderId = insertOrder("LAB-RT-0002", "PENDING");
        em.clear();

        assertThatThrownBy(() -> em.find(LabOrder.class, orderId))
                .isInstanceOf(PersistenceException.class)
                .hasCauseInstanceOf(SQLGrammarException.class)
                .satisfies(e -> assertThat(rootMessage(e)).containsPattern("column \\w+\\.icd10code does not exist"));
    }

    @Test
    void labResultsRoundTripAgainstSqlInsertedOrder() {
        long orderId = insertOrder("LAB-RT-0003", "COMPLETED");
        LabResult glucose = result("2345-7", "Glucose", "180", new BigDecimal("180.0000"), ResultFlag.HIGH);
        glucose.setLabOrder(em.getEntityManager().getReference(LabOrder.class, orderId));

        Long resultId = em.persistAndFlush(glucose).getId();
        em.clear();

        LabResult reloaded = em.find(LabResult.class, resultId);
        assertThat(reloaded.getResultCode()).isEqualTo("2345-7");
        assertThat(reloaded.getNumericValue()).isEqualByComparingTo("180");
        assertThat(reloaded.getFlag()).isEqualTo(ResultFlag.HIGH);
        assertThat(reloaded.getStatus()).isEqualTo(ResultStatus.FINAL);
        assertThat(reloaded.isAbnormal()).isTrue();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isZero();

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT lab_order_id, flag, status FROM lab_results WHERE id = ?", resultId);
        assertThat(((Number) row.get("lab_order_id")).longValue()).isEqualTo(orderId);
        assertThat(row).containsEntry("flag", "HIGH").containsEntry("status", "FINAL");
    }

    @Test
    void labOrderStatusLifecycleAtTheSqlLevel() {
        long orderId = insertOrder("LAB-RT-0004", "PENDING");

        jdbc.update("UPDATE lab_orders SET status = 'IN_PROGRESS', version = version + 1 WHERE id = ?", orderId);
        jdbc.update("UPDATE lab_orders SET status = 'CANCELLED', version = version + 1 WHERE id = ?", orderId);

        Map<String, Object> row = jdbc.queryForMap("SELECT status, version, fasting, stat FROM lab_orders WHERE id = ?", orderId);
        assertThat(row).containsEntry("status", "CANCELLED").containsEntry("fasting", false).containsEntry("stat", false);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(2L);
    }

    @Test
    void labOrderTableRejectsDuplicateOrderNumbers() {
        insertOrder("LAB-RT-0005", "PENDING");

        assertThatThrownBy(() -> insertOrder("LAB-RT-0005", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void labOrderTableRequiresPatient() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO lab_orders (order_number, ordering_provider_id, "
                + "order_date_time, status, priority, test_code, test_name) "
                + "VALUES ('LAB-RT-0006', 1, now(), 'PENDING', 'ROUTINE', 'CBC', 'CBC')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void resultAbnormalFlagSemantics() {
        assertThat(LabResult.builder().flag(null).build().isAbnormal()).isFalse();
        assertThat(LabResult.builder().flag(ResultFlag.NORMAL).build().isAbnormal()).isFalse();
        for (ResultFlag flag : ResultFlag.values()) {
            if (flag != ResultFlag.NORMAL) {
                assertThat(LabResult.builder().flag(flag).build().isAbnormal()).as(flag.name()).isTrue();
            }
        }
    }

    @Test
    void addResultLinksBothSides() {
        LabOrder order = new LabOrder();
        LabResult result = new LabResult();

        order.addResult(result);

        assertThat(order.getResults()).containsExactly(result);
        assertThat(result.getLabOrder()).isSameAs(order);
        assertThat(LabOrder.builder().build().getFasting()).isFalse();
        assertThat(LabOrder.builder().build().getStat()).isFalse();
    }

    private long insertOrder(String orderNumber, String status) {
        jdbc.update("INSERT INTO lab_orders (order_number, patient_id, encounter_id, ordering_provider_id, "
                        + "order_date_time, status, priority, test_code, test_name, icd10_code) "
                        + "VALUES (?, 1, 1, 1, '2024-04-01 09:00:00', ?, 'ROUTINE', 'CMP', "
                        + "'Comprehensive metabolic panel', 'Z00.00')",
                orderNumber, status);
        return jdbc.queryForObject("SELECT id FROM lab_orders WHERE order_number = ?", Long.class, orderNumber);
    }

    private static LabResult result(String code, String name, String value, BigDecimal numeric, ResultFlag flag) {
        return LabResult.builder()
                .resultCode(code)
                .resultName(name)
                .value(value)
                .numericValue(numeric)
                .unit("mg/dL")
                .flag(flag)
                .status(ResultStatus.FINAL)
                .resultDateTime(LocalDateTime.of(2024, 4, 1, 12, 0))
                .build();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
