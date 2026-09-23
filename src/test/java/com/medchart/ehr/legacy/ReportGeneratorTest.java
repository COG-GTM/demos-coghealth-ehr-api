package com.medchart.ehr.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportGeneratorTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private ReportGenerator reportGenerator;

    @BeforeEach
    void setUp() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void dailyReportMasksSsnAndInsuranceMemberId() throws Exception {
        when(query.getResultList()).thenReturn(Collections.singletonList(rosterRow()));

        String csv = new String(reportGenerator.generateDailyReport(), StandardCharsets.UTF_8);

        assertThat(csv).contains("***-**-6789");
        assertThat(csv).contains("****4321");
        assertThat(csv).doesNotContain("123-45-6789");
        assertThat(csv).doesNotContain("MEM-0000-4321");
    }

    @Test
    void dailyReportDeletesTheTemporaryPhiFile() throws Exception {
        when(query.getResultList()).thenReturn(Collections.singletonList(rosterRow()));

        reportGenerator.generateDailyReport();

        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"));
        try (var files = Files.list(tempDir)) {
            assertThat(files.map(p -> p.getFileName().toString()))
                .noneMatch(name -> name.startsWith("patient_roster_"));
        }
    }

    @Test
    void encounterSummaryRejectsUnboundedRange() {
        assertThatThrownBy(() -> reportGenerator.generateEncounterSummary(
                LocalDateTime.of(1, 1, 1, 0, 0), LocalDateTime.of(9999, 12, 31, 0, 0)))
            .isInstanceOf(ExportRequestException.class)
            .hasMessageContaining("Date range");
    }

    private Object[] rosterRow() {
        return new Object[]{1L, "MRN001", "123-45-6789", "Ada", "Lovelace", "1980-01-02",
            "555-0100", "555-0101", "ada@example.com", "1 Main St", "Springfield", "IL", "62701",
            "Acme Health", "MEM-0000-4321"};
    }
}
