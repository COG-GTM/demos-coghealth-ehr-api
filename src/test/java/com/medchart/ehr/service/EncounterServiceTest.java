package com.medchart.ehr.service;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
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

    private EncounterService encounterService;

    @BeforeEach
    void setUp() {
        encounterService = new EncounterService(encounterRepository);
    }

    @Test
    void providerCannotReadAnotherProvidersEncounter() {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(200L)));

        assertThrows(AccessDeniedException.class,
                () -> encounterService.findByIdForRequester(1L, provider(10L, 100L)));
    }

    @Test
    void providerCanReadOwnEncounter() {
        Encounter encounter = encounter(100L);
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter));

        assertEquals(Optional.of(encounter), encounterService.findByIdForRequester(1L, provider(10L, 100L)));
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
                .attendingProvider(Provider.builder().id(providerId).build())
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
