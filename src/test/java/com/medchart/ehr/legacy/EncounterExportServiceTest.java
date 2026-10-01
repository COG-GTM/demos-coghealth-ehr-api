package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.BulkExportAuditor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EncounterExportServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private BulkExportAuditor bulkExportAuditor;

    @InjectMocks
    private EncounterExportService encounterExportService;

    private Query queryReturning(Object... rows) {
        Query query = mock(Query.class);
        when(query.getResultList()).thenReturn(Arrays.asList(rows));
        return query;
    }

    @Test
    void dateRangeExportAuditsRecordCount() {
        Object[] row = {1L, "ENC-1", "OFFICE", "FINISHED", null, "MRN1", "Ada", "Lovelace", null};
        Query query = queryReturning((Object) row);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        encounterExportService.exportEncountersForDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        verify(bulkExportAuditor).recordExport(eq("Encounter"), isNull(), eq(1), anyString());
    }

    @Test
    void patientHistoryExportAuditsPatientId() {
        Query patientQuery = mock(Query.class);
        when(patientQuery.getSingleResult())
                .thenReturn(new Object[]{"MRN1", "Ada", "Lovelace", "999-99-9999", "1815-12-10"});
        Query encounterQuery = mock(Query.class);
        when(encounterQuery.getResultList()).thenReturn(Collections.emptyList());
        when(entityManager.createNativeQuery(anyString())).thenReturn(patientQuery, encounterQuery);

        encounterExportService.exportPatientEncounterHistory(7L);

        verify(bulkExportAuditor).recordExport(eq("Encounter"), eq(7L), eq(0), anyString());
    }

    @Test
    void failedPatientHistoryExportIsAudited() {
        Query patientQuery = mock(Query.class);
        when(patientQuery.getSingleResult()).thenThrow(new NoResultException("no patient"));
        when(entityManager.createNativeQuery(anyString())).thenReturn(patientQuery);

        assertThatThrownBy(() -> encounterExportService.exportPatientEncounterHistory(7L))
                .isInstanceOf(NoResultException.class);

        verify(bulkExportAuditor).recordFailedExport(eq("Encounter"), eq(7L), anyString(), any());
        verify(bulkExportAuditor, org.mockito.Mockito.never())
                .recordExport(anyString(), any(), anyInt(), anyString());
    }
}
