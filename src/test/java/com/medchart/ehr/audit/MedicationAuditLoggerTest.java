package com.medchart.ehr.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MedicationAuditLoggerTest {

    @Mock
    private AuditEventRepository repository;
    @InjectMocks
    private MedicationAuditLogger logger;

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("BR-AUD-11: prescribing a controlled substance is audited as CREATE with its DEA schedule")
    void brAud11_controlledPrescription() {
        logger.logPrescriptionCreated(11L, "PHYSICIAN", 42L, "MRN-1", 900L, "Oxycodone", true, "II", "10.0.0.1");

        AuditEvent e = saved();
        assertEquals(AuditAction.CREATE, e.getAction());
        assertEquals("MedicationOrder", e.getResourceType());
        assertEquals(900L, e.getResourceId());
        assertEquals("Prescribed Oxycodone (Schedule II) for patient MRN-1", e.getDescription());
        assertTrue(e.getSuccess());
        assertNotNull(e.getTimestamp());
    }

    @Test
    @DisplayName("BR-AUD-11: non-controlled prescriptions are audited as CREATE without a schedule")
    void brAud11_nonControlledPrescription() {
        logger.logPrescriptionCreated(11L, "PHYSICIAN", 42L, "MRN-1", 901L, "Metformin", false, null, "10.0.0.1");
        AuditEvent e = saved();
        assertEquals(AuditAction.CREATE, e.getAction());
        assertEquals("Prescribed Metformin for patient MRN-1", e.getDescription());
    }

    @Test
    @DisplayName("BR-AUD-12: prescription modifications are audited as UPDATE with old and new values")
    void brAud12_modification() {
        logger.logPrescriptionModified(11L, "PHYSICIAN", 42L, "MRN-1", 901L, "Metformin", "dose", "500mg", "1000mg", "ip");
        AuditEvent e = saved();
        assertEquals(AuditAction.UPDATE, e.getAction());
        assertEquals("Modified Metformin for patient MRN-1: dose changed from '500mg' to '1000mg'", e.getDescription());
    }

    @Test
    @DisplayName("BR-AUD-12: prescription cancellations are audited as DELETE with the reason")
    void brAud12_cancellation() {
        logger.logPrescriptionCancelled(11L, "PHYSICIAN", 42L, "MRN-1", 901L, "Metformin", "Duplicate", "ip");
        AuditEvent e = saved();
        assertEquals(AuditAction.DELETE, e.getAction());
        assertEquals("Cancelled Metformin for patient MRN-1 - Reason: Duplicate", e.getDescription());
        assertEquals(901L, e.getResourceId());
    }

    @ParameterizedTest(name = "status {0} -> success {1}")
    @CsvSource({"SUCCESS,true", "accepted,true", "REJECTED,false", "PENDING,false", "'',false"})
    @DisplayName("BR-AUD-13: e-prescribe transmissions are EXPORT events, successful only when SUCCESS/ACCEPTED")
    void brAud13_eprescribe(String status, boolean success) {
        logger.logEprescribeTransaction(11L, "PHYSICIAN", 42L, "MRN-1", 901L, "NEWRX", "0000000", status, "ip");
        AuditEvent e = saved();
        assertEquals(AuditAction.EXPORT, e.getAction());
        assertEquals(success, e.getSuccess());
        assertTrue(e.getDescription().startsWith("E-prescribe NEWRX to pharmacy 0000000 for patient MRN-1"));
    }

    @Test
    @DisplayName("BR-AUD-13: a null e-prescribe status is not a success")
    void brAud13_nullStatus() {
        logger.logEprescribeTransaction(11L, "PHYSICIAN", 42L, "MRN-1", 901L, "NEWRX", "0000000", null, "ip");
        assertEquals(false, saved().getSuccess());
    }

    @Test
    @DisplayName("BR-AUD-14: PDMP checks are audited as Patient READ with the state queried")
    void brAud14_pdmp() {
        logger.logPdmpCheck(11L, "PHYSICIAN", 42L, "MRN-1", "NY", "ip");
        AuditEvent e = saved();
        assertEquals(AuditAction.READ, e.getAction());
        assertEquals("Patient", e.getResourceType());
        assertEquals("PDMP check for patient MRN-1 in state NY", e.getDescription());
    }

    @Test
    @DisplayName("BR-AUD-14: medication history access is audited as MedicationHistory READ with source and count")
    void brAud14_medicationHistory() {
        logger.logMedicationHistoryAccess(11L, "PHARMACIST", 42L, "MRN-1", "SURESCRIPTS", 3, "ip");
        AuditEvent e = saved();
        assertEquals(AuditAction.READ, e.getAction());
        assertEquals("MedicationHistory", e.getResourceType());
        assertEquals("Accessed medication history for patient MRN-1 from SURESCRIPTS (3 records)", e.getDescription());
    }
}
