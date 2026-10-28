package com.medchart.ehr.service;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock
    private EncounterRepository encounterRepository;

    @Mock
    private PatientAccessLogger patientAccessLogger;

    private EncounterService encounterService;

    @BeforeEach
    void setUp() {
        encounterService = new EncounterService(encounterRepository, patientAccessLogger);
    }

    @Test
    void providerCannotReadAnotherProvidersEncounter() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(200L)));

        assertThrows(AccessDeniedException.class,
                () -> encounterService.findByIdForRequester(1L, provider(10L, 100L)));
        verifyNoInteractions(patientAccessLogger);
    }

    @Test
    void providerCanReadOwnEncounter() {
        Encounter encounter = encounter(100L);
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        assertEquals(Optional.of(encounter), encounterService.findByIdForRequester(1L, provider(10L, 100L)));
        verify(patientAccessLogger).logAccess(
                10L, "PROVIDER", 5L, null, AuditAction.READ, "Encounter", "View encounter", null, null);
    }

    @Test
    void adminCanReadAnotherProvidersEncounter() {
        Encounter encounter = encounter(200L);
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        assertEquals(Optional.of(encounter), encounterService.findByIdForRequester(1L, admin()));
    }

    @Test
    void missingEncounterReturnsEmpty() {
        when(encounterRepository.findByIdWithDetails(404L)).thenReturn(Optional.empty());

        assertFalse(encounterService.findByIdForRequester(404L, provider(10L, 100L)).isPresent());
    }

    @Test
    void patientListsUseProviderScopedQueriesForProvidersAndExistingQueriesForAdmins() {
        User requester = provider(10L, 100L);
        encounterService.findByPatientId(5L, requester);
        encounterService.findByPatientId(5L, PageRequest.of(0, 10), requester);
        encounterService.findByPatientId(5L, admin());
        encounterService.findByPatientId(5L, PageRequest.of(0, 10), admin());

        verify(encounterRepository).findByPatientIdAndAttendingProviderId(5L, 100L);
        verify(encounterRepository).findByPatientIdAndAttendingProviderId(5L, 100L, PageRequest.of(0, 10));
        verify(encounterRepository).findByPatientId(5L);
        verify(encounterRepository).findByPatientId(5L, PageRequest.of(0, 10));
    }

    @Test
    void authorizedPatientListsAreAuditedOncePerList() {
        User requester = provider(10L, 100L);
        PageRequest pageable = PageRequest.of(0, 10);
        when(encounterRepository.findByPatientIdAndAttendingProviderId(5L, 100L))
                .thenReturn(java.util.Collections.emptyList());
        when(encounterRepository.findByPatientIdAndAttendingProviderId(5L, 100L, pageable))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.Collections.emptyList(), pageable, 0));

        encounterService.findByPatientId(5L, requester);
        encounterService.findByPatientId(5L, pageable, requester);

        verify(patientAccessLogger, times(2)).logAccess(
                10L, "PROVIDER", 5L, null, AuditAction.READ, "Encounter", "List patient encounters", null, null);
    }

    @Test
    void statusAndDateRangeListsUseProviderScopedQueriesForProvidersAndExistingQueriesForAdmins() {
        User requester = provider(10L, 100L);
        LocalDate start = LocalDate.of(2024, 1, 1);
        LocalDate end = LocalDate.of(2024, 1, 31);
        encounterService.findByStatus(EncounterStatus.SCHEDULED, requester);
        encounterService.findByStatus(EncounterStatus.SCHEDULED, admin());
        encounterService.findByDateRange(start, end, requester);
        encounterService.findByDateRange(start, end, admin());

        verify(encounterRepository).findByStatusAndAttendingProviderId(EncounterStatus.SCHEDULED, 100L);
        verify(encounterRepository).findByStatus(EncounterStatus.SCHEDULED);
        verify(encounterRepository).findByDateRangeAndProvider(
                start.atStartOfDay(), end.plusDays(1).atStartOfDay(), 100L);
        verify(encounterRepository).findByDateRange(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
    }

    @Test
    void unlinkedProviderGetsEmptyListsAndPage() {
        User requester = provider(10L, null);

        assertTrue(encounterService.findByPatientId(5L, requester).isEmpty());
        Page<Encounter> page = encounterService.findByPatientId(5L, PageRequest.of(0, 10), requester);
        assertTrue(page.isEmpty());
        assertTrue(encounterService.findByStatus(EncounterStatus.SCHEDULED, requester).isEmpty());
        assertTrue(encounterService.findByDateRange(LocalDate.now(), LocalDate.now(), requester).isEmpty());

        verifyNoInteractions(encounterRepository);
    }

    @Test
    void providerCannotListAnotherProvidersEncounters() {
        assertThrows(AccessDeniedException.class,
                () -> encounterService.findByProviderId(200L, provider(10L, 100L)));
    }

    @Test
    void providerCannotCancelAnotherProvidersEncounter() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(200L)));

        assertThrows(AccessDeniedException.class,
                () -> encounterService.cancelEncounter(1L, provider(10L, 100L)));
        verify(encounterRepository, never()).save(any());
    }

    @Test
    void providerCannotReassignOwnEncounterToAnotherPatient() {
        Encounter existing = encounter(100L);
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(existing));
        Encounter requested = encounter(100L);
        requested.setPatient(Patient.builder().id(6L).build());

        assertThrows(AccessDeniedException.class,
                () -> encounterService.update(1L, requested, provider(10L, 100L)));

        verify(encounterRepository, never()).save(any());
    }

    @Test
    void providerUpdateWithoutPatientKeepsExistingPatient() {
        Encounter existing = encounter(100L);
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(existing));
        Encounter requested = Encounter.builder()
                .attendingProvider(Provider.builder().id(100L).build())
                .build();
        when(encounterRepository.save(requested)).thenReturn(requested);

        Optional<Encounter> updated = encounterService.update(1L, requested, provider(10L, 100L));

        assertSame(existing.getPatient(), updated.orElseThrow().getPatient());
        verify(encounterRepository).save(requested);
    }

    @Test
    void adminCanCancelEncounterWithoutAttendingProvider() {
        Encounter unassigned = encounter(null);
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(unassigned));
        when(encounterRepository.save(unassigned)).thenReturn(unassigned);

        assertTrue(encounterService.cancelEncounter(1L, admin()));

        assertEquals(EncounterStatus.CANCELLED, unassigned.getStatus());
        verify(encounterRepository).save(unassigned);
    }

    @Test
    void providerCannotCancelEncounterWithoutAttendingProvider() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(null)));

        assertThrows(AccessDeniedException.class,
                () -> encounterService.cancelEncounter(1L, provider(10L, 100L)));

        verify(encounterRepository, never()).save(any());
        verifyNoInteractions(patientAccessLogger);
    }

    @Test
    void providerCannotCreateEncounterAssignedToAnotherProvider() {
        Encounter encounter = encounter(200L);

        assertThrows(AccessDeniedException.class,
                () -> encounterService.create(encounter, provider(10L, 100L)));
        verify(encounterRepository, never()).save(any());
    }

    @Test
    void missingMutationTargetReturnsFalse() {
        when(encounterRepository.findByIdWithDetails(404L)).thenReturn(Optional.empty());

        assertFalse(encounterService.cancelEncounter(404L, provider(10L, 100L)));
        verify(encounterRepository, never()).save(any());
    }

    private Encounter encounter(Long providerId) {
        return Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2024-000001")
                .patient(Patient.builder().id(5L).build())
                .attendingProvider(providerId == null ? null : Provider.builder().id(providerId).build())
                .build();
    }

    private User provider(Long userId, Long providerId) {
        return User.builder()
                .id(userId)
                .username("provider-" + userId)
                .roles(Set.of(User.Role.PROVIDER))
                .providerId(providerId)
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .build();
    }

    private User admin() {
        return User.builder()
                .id(1L)
                .username("admin")
                .roles(Set.of(User.Role.ADMIN))
                .enabled(true)
                .accountNonLocked(true)
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .build();
    }
}
