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
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterExportServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private EncounterExportService service;

    @BeforeEach
    void setUp() {
        service = new EncounterExportService();
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
    }

    @Test
    void patientHistoryMasksSsn() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        lenient().when(query.setParameter(anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        lenient().when(query.setFirstResult(anyInt())).thenReturn(query);
        lenient().when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getSingleResult())
                .thenReturn(new Object[]{"MRN001", "Jane", "Doe", "123-45-6789", "1980-01-01"});
        when(query.getResultList()).thenReturn(List.of());

        String output = new String(service.exportPatientEncounterHistory(1L, 0, 50), StandardCharsets.UTF_8);

        assertThat(output).doesNotContain("123-45-6789");
        assertThat(output).doesNotContain("456789");
        assertThat(output).contains("SSN: ***-**-6789");
    }

    @Test
    void encounterRangeExportIsPaginated() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
        when(query.setFirstResult(anyInt())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        service.exportEncountersForDateRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 10), 2, 50);

        verify(query).setFirstResult(100);
        verify(query).setMaxResults(50);
    }

    @Test
    void unboundedDateRangeIsRejected() {
        assertThatThrownBy(() -> service.exportEncountersForDateRange(
                LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), 0, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overflowingOffsetIsRejected() {
        assertThatThrownBy(() -> service.exportPatientEncounterHistory(1L, 5_000_000, 500))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oversizedPageIsRejected() {
        assertThatThrownBy(() -> service.exportPatientEncounterHistory(1L, 0, 100_000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
