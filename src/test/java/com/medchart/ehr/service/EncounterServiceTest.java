package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterCreateRequest;
import com.medchart.ehr.dto.EncounterUpdateRequest;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.repository.ProviderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EncounterServiceTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 10, 27, 9, 30);

    private EncounterRepository encounterRepository;
    private PatientRepository patientRepository;
    private ProviderRepository providerRepository;
    private AuditService auditService;
    private EncounterService service;

    private Patient alice;
    private Provider drSmith;
    private Provider drJones;

    @BeforeEach
    void setUp() {
        encounterRepository = mock(EncounterRepository.class);
        patientRepository = mock(PatientRepository.class);
        providerRepository = mock(ProviderRepository.class);
        auditService = mock(AuditService.class);
        service = new EncounterService(encounterRepository, patientRepository, providerRepository, auditService);
        when(encounterRepository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));

        alice = new Patient();
        alice.setId(1L);
        drSmith = new Provider();
        drSmith.setId(10L);
        drJones = new Provider();
        drJones.setId(11L);
        when(patientRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(providerRepository.findById(10L)).thenReturn(Optional.of(drSmith));
        when(providerRepository.findById(11L)).thenReturn(Optional.of(drJones));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Encounter storedEncounter() {
        return Encounter.builder()
                .id(42L)
                .encounterNumber("ENC-2026-000042")
                .patient(alice)
                .attendingProvider(drSmith)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(EncounterStatus.CHECKED_IN)
                .encounterDateTime(WHEN)
                .department("Cardiology")
                .room("12")
                .bed("B")
                .chiefComplaint("Chest pain")
                .priority(EncounterPriority.URGENT)
                .visitType("NEW")
                .notes("Original notes")
                .createdBy("dr.smith")
                .version(3L)
                .build();
    }

    @Test
    void createBuildsNewEntityWithServerOwnedFields() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("nurse.kim", null, AuthorityUtils.createAuthorityList("ROLE_STAFF")));
        EncounterCreateRequest request = EncounterCreateRequest.builder()
                .patientId(1L)
                .attendingProviderId(10L)
                .encounterType(EncounterType.OUTPATIENT)
                .encounterDateTime(WHEN)
                .room("4")
                .chiefComplaint("Cough")
                .build();

        Encounter saved = service.create(request);

        assertNull(saved.getId());
        assertNull(saved.getVersion());
        assertSame(alice, saved.getPatient());
        assertSame(drSmith, saved.getAttendingProvider());
        assertEquals(EncounterStatus.SCHEDULED, saved.getStatus());
        assertTrue(saved.getEncounterNumber().startsWith("ENC-"));
        assertEquals("nurse.kim", saved.getCreatedBy());
        assertEquals("4", saved.getRoom());
        assertEquals("Cough", saved.getChiefComplaint());
        assertTrue(saved.getDiagnoses().isEmpty());

        AuditEvent audit = capturedAudit();
        assertEquals(AuditAction.CREATE, audit.getAction());
        assertEquals("Encounter", audit.getResourceType());
        assertEquals(1L, audit.getPatientId());
        assertEquals("nurse.kim", audit.getUserId());
    }

    @Test
    void createLeavesCreatedByEmptyForAnonymousCaller() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        EncounterCreateRequest request = EncounterCreateRequest.builder()
                .patientId(1L).encounterType(EncounterType.OUTPATIENT).encounterDateTime(WHEN).build();

        assertNull(service.create(request).getCreatedBy());
    }

    @Test
    void createRejectsUnknownPatientAndProvider() {
        EncounterCreateRequest unknownPatient = EncounterCreateRequest.builder()
                .patientId(99L).encounterType(EncounterType.OUTPATIENT).encounterDateTime(WHEN).build();
        EncounterCreateRequest unknownProvider = EncounterCreateRequest.builder()
                .patientId(1L).attendingProviderId(99L).encounterType(EncounterType.OUTPATIENT).encounterDateTime(WHEN).build();

        assertThrows(InvalidEncounterRequestException.class, () -> service.create(unknownPatient));
        assertThrows(InvalidEncounterRequestException.class, () -> service.create(unknownProvider));
        verify(encounterRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateCopiesOnlyProvidedFieldsOntoStoredEntity() {
        Encounter stored = storedEncounter();
        when(encounterRepository.findById(42L)).thenReturn(Optional.of(stored));
        EncounterUpdateRequest request = new EncounterUpdateRequest();
        request.setChiefComplaint("Chest pain, resolved");
        request.setAttendingProviderId(11L);

        Encounter result = service.update(42L, request).orElseThrow();

        assertSame(stored, result);
        assertEquals("Chest pain, resolved", result.getChiefComplaint());
        assertSame(drJones, result.getAttendingProvider());
        assertSame(alice, result.getPatient());
        assertEquals("dr.smith", result.getCreatedBy());
        assertEquals("ENC-2026-000042", result.getEncounterNumber());
        assertEquals(EncounterStatus.CHECKED_IN, result.getStatus());
        assertEquals(3L, result.getVersion());
        assertEquals("12", result.getRoom());
        assertEquals("B", result.getBed());
        assertEquals("Cardiology", result.getDepartment());
        assertEquals("Original notes", result.getNotes());
        assertEquals(EncounterType.OFFICE_VISIT, result.getEncounterType());
        assertEquals(WHEN, result.getEncounterDateTime());
        verify(encounterRepository).save(stored);

        AuditEvent audit = capturedAudit();
        assertEquals(AuditAction.UPDATE, audit.getAction());
        assertEquals(42L, audit.getResourceId());
        assertEquals(1L, audit.getPatientId());
        assertEquals("anonymous", audit.getUserId());
    }

    @Test
    void auditIsPublishedOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(EncounterCreateRequest.builder()
                    .patientId(1L).encounterType(EncounterType.OUTPATIENT).encounterDateTime(WHEN).build());
            verifyNoInteractions(auditService);

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertEquals(AuditAction.CREATE, capturedAudit().getAction());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void auditIsDroppedWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.create(EncounterCreateRequest.builder()
                    .patientId(1L).encounterType(EncounterType.OUTPATIENT).encounterDateTime(WHEN).build());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verifyNoInteractions(auditService);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private AuditEvent capturedAudit() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    @Test
    void updateClearsFieldsSentAsExplicitNull() {
        Encounter stored = storedEncounter();
        when(encounterRepository.findById(42L)).thenReturn(Optional.of(stored));
        EncounterUpdateRequest request = new EncounterUpdateRequest();
        request.setBed(null);
        request.setAttendingProviderId(null);

        Encounter result = service.update(42L, request).orElseThrow();

        assertNull(result.getBed());
        assertNull(result.getAttendingProvider());
        assertEquals("12", result.getRoom());
    }

    @Test
    void updateRejectsUnknownProvider() {
        when(encounterRepository.findById(42L)).thenReturn(Optional.of(storedEncounter()));
        EncounterUpdateRequest request = new EncounterUpdateRequest();
        request.setAttendingProviderId(99L);

        assertThrows(InvalidEncounterRequestException.class, () -> service.update(42L, request));
        verify(encounterRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void updateOfMissingEncounterReturnsEmpty() {
        when(encounterRepository.findById(7L)).thenReturn(Optional.empty());

        assertTrue(service.update(7L, new EncounterUpdateRequest()).isEmpty());
        verify(encounterRepository, never()).save(any());
        verifyNoInteractions(auditService);
    }
}
