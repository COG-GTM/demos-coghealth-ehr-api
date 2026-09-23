package com.medchart.ehr.legacy;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

class EncounterExportServiceTest {

    @TempDir
    Path exportDir;

    private EntityManager entityManager;
    private AuditService auditService;
    private EncounterExportService service;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        auditService = mock(AuditService.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        List<Object[]> rows = Collections.singletonList(new Object[]{
            1L, "MRN001", "Ada", "Lovelace", "1815-12-10", "ada@example.com",
            "555-0100", "555-0101", "1 Main St", "Boston", "MA", "02101"});
        when(query.getResultList()).thenReturn(rows);
        service = new EncounterExportService(entityManager, auditService, exportDir.toString());
    }

    @Test
    void writesExportInsideConfiguredDirectory() {
        Path written = service.exportAllPatientsToFile("patients.csv");

        assertEquals(exportDir.resolve("patients.csv"), written);
        assertTrue(Files.exists(written));
    }

    @Test
    void rejectsParentDirectoryTraversal() throws Exception {
        Path outside = exportDir.getParent().resolve("pwned.csv");
        Files.deleteIfExists(outside);

        assertThrows(IllegalArgumentException.class,
            () -> service.exportAllPatientsToFile("../pwned.csv"));

        assertFalse(Files.exists(outside));
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsAbsolutePathsAndNestedNames() {
        assertThrows(IllegalArgumentException.class,
            () -> service.exportAllPatientsToFile("/etc/cron.d/backdoor"));
        assertThrows(IllegalArgumentException.class,
            () -> service.exportAllPatientsToFile("sub/patients.csv"));
        assertThrows(IllegalArgumentException.class,
            () -> service.exportAllPatientsToFile(null));
    }

    @Test
    void auditsBulkExport() {
        service.exportAllPatientsToFile("patients.csv");

        var captor = forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        assertEquals(AuditAction.EXPORT, captor.getValue().getAction());
        assertEquals("PatientBulkExport", captor.getValue().getResourceType());
    }
}
