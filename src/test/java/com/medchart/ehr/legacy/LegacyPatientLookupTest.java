package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyPatientLookupTest {

    private static final String MRN = "MRN-998877";

    @Mock
    private EntityManager entityManager;

    @Mock
    private PatientAccessLogger patientAccessLogger;

    @Mock
    private Query query;

    private LegacyPatientLookup lookup;

    @BeforeEach
    void setUp() {
        lookup = new LegacyPatientLookup();
        ReflectionTestUtils.setField(lookup, "entityManager", entityManager);
        ReflectionTestUtils.setField(lookup, "patientAccessLogger", patientAccessLogger);
        lenient().when(entityManager.createNativeQuery(any(String.class), eq(Patient.class))).thenReturn(query);
        lenient().when(query.setParameter(anyInt(), any())).thenReturn(query);
    }

    @Test
    void notFoundRecordsAuditEventWithoutRawMrn() {
        when(query.getSingleResult()).thenThrow(new NoResultException());

        assertThat(lookup.findPatientByMrn(MRN)).isNull();

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(patientAccessLogger).logFailedAccess(
            eq(null), eq("LEGACY_LOOKUP"), eq(null), eq(AuditAction.READ), eq("Patient"),
            reason.capture(), eq(null));
        assertThat(reason.getValue()).doesNotContain(MRN).contains("****8877");
    }

    @Test
    void databaseErrorIsPropagatedAndAudited() {
        when(query.getSingleResult()).thenThrow(new PersistenceException("connection reset"));

        assertThatThrownBy(() -> lookup.findPatientByMrn(MRN))
            .isInstanceOf(PersistenceException.class);

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(patientAccessLogger).logFailedAccess(
            any(), any(), any(), eq(AuditAction.READ), eq("Patient"), reason.capture(), any());
        assertThat(reason.getValue()).doesNotContain(MRN).contains("PersistenceException");
    }

    @Test
    void successfulLookupDoesNotAudit() {
        Patient patient = new Patient();
        when(query.getSingleResult()).thenReturn(patient);

        assertThat(lookup.findPatientByMrn(MRN)).isSameAs(patient);
        verifyNoInteractions(patientAccessLogger);
    }

    @Test
    void maskingKeepsOnlyLastFourCharacters() {
        assertThat(LegacyPatientLookup.maskMrn(null)).isEqualTo("[absent]");
        assertThat(LegacyPatientLookup.maskMrn("")).isEqualTo("[absent]");
        assertThat(LegacyPatientLookup.maskMrn("1234")).isEqualTo("****");
        assertThat(LegacyPatientLookup.maskMrn("MRN-123456")).isEqualTo("****3456");
    }
}
