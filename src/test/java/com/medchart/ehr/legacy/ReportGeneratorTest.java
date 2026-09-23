package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportGeneratorTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private ReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        reportGenerator = new ReportGenerator();
        ReflectionTestUtils.setField(reportGenerator, "entityManager", entityManager);
    }

    @Test
    void patientRosterContainsNoSsnOrMemberId() throws Exception {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.<Object[]>of(new Object[]{
                1L, "MRN001", "Jane", "Doe", "1980-01-01", "555-0100", "555-0101",
                "jane@example.com", "1 Main St", "Boston", "MA", "02101", "Acme Health"}));

        String path = reportGenerator.generatePatientRoster(0, 50);
        String csv = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        Files.deleteIfExists(Path.of(path));

        assertThat(csv).doesNotContain("SSN");
        assertThat(csv).doesNotContain("MemberID");
        assertThat(csv).contains("MRN001");
        verify(query).setMaxResults(50);
    }

    @Test
    void rosterQuerySelectsNoSsn() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        String path = reportGenerator.generatePatientRoster(0, 50);
        assertThat(path).isNotBlank();

        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertThat(sql.getValue()).doesNotContain("ssn");
        assertThat(sql.getValue()).doesNotContain("member_id");
    }

    @Test
    void concurrentRostersGetDistinctFiles() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        String first = reportGenerator.generatePatientRoster(0, 50);
        String second = reportGenerator.generatePatientRoster(1, 50);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void encounterSummaryRejectsUnboundedRange() {
        assertThatThrownBy(() -> reportGenerator.generateEncounterSummary(
                LocalDateTime.of(1, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 0, 0), 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dailyReportRejectsOversizedPage() {
        assertThatThrownBy(() -> reportGenerator.generateDailyReport(0, 100_000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
