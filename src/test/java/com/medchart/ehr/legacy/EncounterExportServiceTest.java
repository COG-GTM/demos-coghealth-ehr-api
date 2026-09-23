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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EncounterExportServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private EncounterExportService service;

    @BeforeEach
    void setUp() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    }

    @Test
    void patientHistoryExportMasksSsn() {
        when(query.getSingleResult()).thenReturn(
            new Object[]{"MRN001", "Ada", "Lovelace", "123-45-6789", LocalDate.of(1980, 1, 2)});
        when(query.getResultList()).thenReturn(List.of());

        String export = new String(service.exportPatientEncounterHistory(7L), StandardCharsets.UTF_8);

        assertThat(export).contains("SSN: ***-**-6789");
        assertThat(export).doesNotContain("123-45-6789");
    }

    @Test
    void dateRangeExportRejectsUnboundedRange() {
        assertThatThrownBy(() -> service.exportEncountersForDateRange(
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31)))
            .isInstanceOf(ExportRequestException.class)
            .hasMessageContaining("Date range");
    }

    @Test
    void dateRangeExportRejectsInvertedRange() {
        assertThatThrownBy(() -> service.exportEncountersForDateRange(
                LocalDate.of(2024, 2, 10), LocalDate.of(2024, 2, 1)))
            .isInstanceOf(ExportRequestException.class)
            .hasMessageContaining("endDate");
    }

    @Test
    void dateRangeExportRejectsOversizedResultSet() {
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i <= EncounterExportService.MAX_EXPORT_ROWS; i++) {
            rows.add(encounterRow(i));
        }
        when(query.getResultList()).thenReturn(rows);

        assertThatThrownBy(() -> service.exportEncountersForDateRange(
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)))
            .isInstanceOf(ExportRequestException.class)
            .hasMessageContaining("maximum");
    }

    @Test
    void dateRangeExportReturnsCsvWithinLimits() {
        when(query.getResultList()).thenReturn(List.of(encounterRow(1)));

        String csv = new String(service.exportEncountersForDateRange(
            LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10)), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("EncounterId,EncounterNumber,PatientMRN");
        assertThat(csv).contains("MRN001");
    }

    private Object[] encounterRow(int id) {
        return new Object[]{id, "ENC" + id, "OFFICE", "FINISHED", "2024-01-05T10:00",
            "MRN001", "Ada", "Lovelace", "1980-01-02"};
    }
}
