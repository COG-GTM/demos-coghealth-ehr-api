package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncounterExportServiceHipaaTest {

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private EncounterExportService encounterExportService;

    @Test
    void exportPatientEncounterHistory_containsSsnInPlainText() {
        // Mock the patient query
        Query patientQuery = mock(Query.class);
        when(entityManager.createNativeQuery(
                "SELECT mrn, first_name, last_name, ssn, date_of_birth FROM patients WHERE id = ?1"))
                .thenReturn(patientQuery);
        when(patientQuery.setParameter(1, 1L)).thenReturn(patientQuery);
        // Patient data: [mrn, first_name, last_name, ssn, date_of_birth]
        Object[] patientData = new Object[]{"MRN001", "John", "Doe", "123-45-6789", "1990-01-15"};
        when(patientQuery.getSingleResult()).thenReturn(patientData);

        // Mock the encounters query
        Query encounterQuery = mock(Query.class);
        when(entityManager.createNativeQuery(
                "SELECT * FROM encounters WHERE patient_id = ?1 ORDER BY encounter_date_time DESC"))
                .thenReturn(encounterQuery);
        when(encounterQuery.setParameter(1, 1L)).thenReturn(encounterQuery);
        when(encounterQuery.getResultList()).thenReturn(Collections.emptyList());

        byte[] result = encounterExportService.exportPatientEncounterHistory(1L);
        String output = new String(result);

        // Documents the HIPAA issue: SSN is exported unmasked in plain text
        assertThat(output).contains("SSN:");
        assertThat(output).contains("123-45-6789");
    }
}
