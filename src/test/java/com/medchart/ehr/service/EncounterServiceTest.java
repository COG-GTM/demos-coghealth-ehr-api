package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EncounterServiceTest {

    private EncounterRepository repository;
    private AuditService auditService;
    private EncounterService service;

    @BeforeEach
    void setUp() {
        repository = mock(EncounterRepository.class);
        auditService = mock(AuditService.class);
        when(repository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new EncounterService(repository, auditService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("dr.alice", null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Encounter stored(EncounterStatus status) {
        Patient bob = new Patient();
        bob.setId(7L);
        Encounter enc = Encounter.builder()
                .id(42L)
                .encounterNumber("ENC-2026-000042")
                .patient(bob)
                .encounterType(EncounterType.OUTPATIENT)
                .status(status)
                .encounterDateTime(LocalDateTime.of(2026, 10, 25, 9, 0))
                .notes("Original clinical note")
                .createdBy("dr.alice")
                .version(3L)
                .build();
        when(repository.findById(42L)).thenReturn(Optional.of(enc));
        return enc;
    }

    @Test
    void happyPathFollowsStateMachine() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        service.checkIn(42L);
        assertEquals(EncounterStatus.CHECKED_IN, enc.getStatus());
        service.startEncounter(42L);
        assertEquals(EncounterStatus.IN_PROGRESS, enc.getStatus());
        service.completeEncounter(42L, "Follow up in 2 weeks");
        assertEquals(EncounterStatus.COMPLETED, enc.getStatus());
        assertEquals("Follow up in 2 weeks", enc.getNotes());
    }

    @Test
    void cancellingCompletedEncounterIsRejectedAndRowUnchanged() {
        Encounter enc = stored(EncounterStatus.COMPLETED);
        assertThrows(InvalidEncounterTransitionException.class, () -> service.cancelEncounter(42L));
        assertEquals(EncounterStatus.COMPLETED, enc.getStatus());
        verify(repository, never()).save(any());

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(audit.capture());
        assertEquals("dr.alice", audit.getValue().getUserId());
        assertEquals(42L, audit.getValue().getResourceId());
        assertEquals(7L, audit.getValue().getPatientId());
        assertFalse(audit.getValue().getSuccess());
    }

    @Test
    void completeOnCompletedEncounterDoesNotOverwriteNotes() {
        Encounter enc = stored(EncounterStatus.COMPLETED);
        assertThrows(InvalidEncounterTransitionException.class,
                () -> service.completeEncounter(42L, "Mallory was here"));
        assertEquals("Original clinical note", enc.getNotes());
    }

    @Test
    void completeRequiresInProgress() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        assertThrows(InvalidEncounterTransitionException.class, () -> service.completeEncounter(42L, "x"));
        assertEquals(EncounterStatus.SCHEDULED, enc.getStatus());
        assertEquals("Original clinical note", enc.getNotes());
    }

    @Test
    void oversizedNotesRejected() {
        stored(EncounterStatus.IN_PROGRESS);
        assertThrows(IllegalArgumentException.class, () -> service.completeEncounter(42L, "x".repeat(1001)));
    }

    @Test
    void unknownEncounterThrowsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EncounterNotFoundException.class, () -> service.checkIn(99L));
    }

    @ParameterizedTest
    @EnumSource(value = EncounterStatus.class, names = {"COMPLETED", "CANCELLED", "NO_SHOW"})
    void terminalStatesAllowNoTransitions(EncounterStatus terminal) {
        for (EncounterStatus target : EncounterStatus.values()) {
            assertFalse(EncounterService.canTransition(terminal, target), terminal + " -> " + target);
        }
    }

    @Test
    void transitionTable() {
        assertTrue(EncounterService.canTransition(EncounterStatus.SCHEDULED, EncounterStatus.CHECKED_IN));
        assertTrue(EncounterService.canTransition(EncounterStatus.SCHEDULED, EncounterStatus.CANCELLED));
        assertTrue(EncounterService.canTransition(EncounterStatus.SCHEDULED, EncounterStatus.NO_SHOW));
        assertTrue(EncounterService.canTransition(EncounterStatus.CHECKED_IN, EncounterStatus.IN_PROGRESS));
        assertTrue(EncounterService.canTransition(EncounterStatus.CHECKED_IN, EncounterStatus.CANCELLED));
        assertTrue(EncounterService.canTransition(EncounterStatus.CHECKED_IN, EncounterStatus.NO_SHOW));
        assertTrue(EncounterService.canTransition(EncounterStatus.IN_PROGRESS, EncounterStatus.COMPLETED));
        assertFalse(EncounterService.canTransition(EncounterStatus.SCHEDULED, EncounterStatus.IN_PROGRESS));
        assertFalse(EncounterService.canTransition(EncounterStatus.SCHEDULED, EncounterStatus.COMPLETED));
        assertFalse(EncounterService.canTransition(EncounterStatus.IN_PROGRESS, EncounterStatus.CANCELLED));
        assertFalse(EncounterService.canTransition(EncounterStatus.IN_PROGRESS, EncounterStatus.CHECKED_IN));
    }

    @Test
    void updateCopiesOnlyEditableFields() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        Patient other = new Patient();
        other.setId(999L);
        Encounter body = Encounter.builder()
                .patient(other)
                .encounterNumber("ENC-HACKED")
                .createdBy("mallory")
                .encounterDateTime(LocalDateTime.of(2020, 1, 1, 0, 0))
                .status(EncounterStatus.COMPLETED)
                .notes("overwritten")
                .department("Cardiology")
                .room("12B")
                .priority(EncounterPriority.URGENT)
                .chiefComplaint("Chest pain")
                .version(3L)
                .build();

        Encounter saved = service.update(42L, body);

        assertEquals(7L, saved.getPatient().getId());
        assertEquals("ENC-2026-000042", saved.getEncounterNumber());
        assertEquals("dr.alice", saved.getCreatedBy());
        assertEquals(LocalDateTime.of(2026, 10, 25, 9, 0), saved.getEncounterDateTime());
        assertEquals(EncounterStatus.SCHEDULED, saved.getStatus());
        assertEquals("Original clinical note", saved.getNotes());
        assertEquals(EncounterType.OUTPATIENT, saved.getEncounterType());
        assertEquals("Cardiology", saved.getDepartment());
        assertEquals("12B", saved.getRoom());
        assertEquals(EncounterPriority.URGENT, saved.getPriority());
        assertEquals("Chest pain", saved.getChiefComplaint());
        assertEquals(enc, saved);
    }

    @Test
    void updateRejectsStaleVersion() {
        stored(EncounterStatus.SCHEDULED);
        Encounter body = Encounter.builder().department("X").version(2L).build();
        assertThrows(InvalidEncounterTransitionException.class, () -> service.update(42L, body));
        verify(repository, never()).save(any());
    }

    @Test
    void updateRejectsClosedEncounter() {
        Encounter enc = stored(EncounterStatus.COMPLETED);
        Encounter body = Encounter.builder().department("X").build();
        assertThrows(InvalidEncounterTransitionException.class, () -> service.update(42L, body));
        assertEquals(null, enc.getDepartment());
        verify(repository, never()).save(any());
    }
    @Test
    void partialUpdateKeepsOmittedFields() {
        Encounter enc = stored(EncounterStatus.SCHEDULED);
        enc.setDepartment("Cardiology");
        enc.setChiefComplaint("Chest pain");

        Encounter saved = service.update(42L, Encounter.builder().room("12B").build());

        assertEquals("12B", saved.getRoom());
        assertEquals("Cardiology", saved.getDepartment());
        assertEquals("Chest pain", saved.getChiefComplaint());
    }

    @Test
    void createIgnoresClientSuppliedIdentityAndNotes() {
        Patient bob = new Patient();
        bob.setId(7L);
        Encounter body = Encounter.builder()
                .id(42L)
                .version(3L)
                .patient(bob)
                .encounterType(EncounterType.OUTPATIENT)
                .status(EncounterStatus.COMPLETED)
                .notes("injected note")
                .createdBy("mallory")
                .build();

        Encounter saved = service.create(body);

        assertEquals(null, saved.getId());
        assertEquals(null, saved.getVersion());
        assertEquals(null, saved.getNotes());
        assertEquals("dr.alice", saved.getCreatedBy());
        assertEquals(EncounterStatus.SCHEDULED, saved.getStatus());
    }
}
