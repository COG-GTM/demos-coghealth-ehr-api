package com.medchart.ehr.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PatientAccessLoggerTest {

    @Mock
    private AuditEventRepository repository;
    @InjectMocks
    private PatientAccessLogger logger;

    private AuditEvent saved() {
        ArgumentCaptor<AuditEvent> c = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).save(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("BR-AUD-07: PHI access records who, role, patient, MRN, action, reason, IP, session and time")
    void brAud07_accessRecorded() {
        logger.logAccess(11L, "PHYSICIAN", 42L, "MRN-1", AuditAction.READ, "Patient", "Treatment", "10.0.0.1", "S-1");

        AuditEvent e = saved();
        assertEquals("11", e.getUserId());
        assertEquals("PHYSICIAN", e.getUserName());
        assertEquals(42L, e.getPatientId());
        assertEquals("MRN-1", e.getPatientMrn());
        assertEquals(AuditAction.READ, e.getAction());
        assertEquals("Patient", e.getResourceType());
        assertEquals("Treatment", e.getDescription());
        assertEquals("10.0.0.1", e.getIpAddress());
        assertEquals("S-1", e.getSessionId());
        assertNotNull(e.getTimestamp());
        assertTrue(e.getSuccess());
    }

    @Test
    @DisplayName("BR-AUD-08: denied access is recorded as a failed event with the denial reason")
    void brAud08_failedAccessRecorded() {
        logger.logFailedAccess(11L, "NURSE", 42L, AuditAction.ACCESS_DENIED, "Patient", "Not on care team", "10.0.0.1");

        AuditEvent e = saved();
        assertFalse(e.getSuccess());
        assertEquals(AuditAction.ACCESS_DENIED, e.getAction());
        assertEquals("Not on care team", e.getDescription());
        assertEquals(42L, e.getPatientId());
        assertNull(e.getPatientMrn());
        assertNotNull(e.getTimestamp());
    }

    @Test
    @DisplayName("BR-AUD-09: bulk access is recorded once with the record count appended")
    void brAud09_bulkAccessRecorded() {
        logger.logBulkAccess(11L, "ANALYST", AuditAction.EXPORT, "Encounter", 250, "Quality report", "10.0.0.1");

        AuditEvent e = saved();
        assertEquals("Quality report [BULK: 250 records]", e.getDescription());
        assertEquals(AuditAction.EXPORT, e.getAction());
        assertNull(e.getPatientId());
        assertTrue(e.getSuccess());
    }

    @Test
    @DisplayName("BR-AUD-09: a zero-record bulk access is still audited (boundary)")
    void brAud09_zeroRecordBulk() {
        logger.logBulkAccess(11L, "ANALYST", AuditAction.SEARCH, "Patient", 0, "Search", null);
        assertEquals("Search [BULK: 0 records]", saved().getDescription());
    }

    @Test
    @DisplayName("BR-AUD-10: access reports echo patient and period; aggregate counts are placeholder zeros (stub)")
    void brAud10_accessReportStub() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 31, 23, 59);

        Map<String, Object> r = logger.generateAccessReport(42L, start, end);

        assertEquals(42L, r.get("patientId"));
        assertEquals(start, r.get("startDate"));
        assertEquals(end, r.get("endDate"));
        assertNotNull(r.get("generatedAt"));
        assertEquals(0, r.get("totalAccesses"));
        assertEquals(0, r.get("uniqueUsers"));
        assertEquals(Map.of(), r.get("accessByType"));
    }
}
