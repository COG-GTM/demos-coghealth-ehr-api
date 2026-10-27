package com.medchart.ehr.service;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EncounterServiceTest {

    @Mock
    private EncounterRepository encounterRepository;

    @InjectMocks
    private EncounterService encounterService;

    private User providerA;
    private User admin;
    private Encounter encounterB;

    @BeforeEach
    void setUp() {
        providerA = User.builder()
                .id(10L)
                .providerId(1L)
                .roles(Set.of(User.Role.PROVIDER))
                .build();
        admin = User.builder()
                .id(11L)
                .roles(Set.of(User.Role.ADMIN))
                .build();
        Provider providerB = Provider.builder().id(2L).build();
        encounterB = Encounter.builder()
                .id(5L)
                .attendingProvider(providerB)
                .status(EncounterStatus.IN_PROGRESS)
                .notes("existing notes")
                .build();
    }

    @Test
    void findByIdForRequesterOnlyReturnsOwnedEncountersUnlessRequesterIsAdmin() {
        when(encounterRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(encounterB));

        assertTrue(encounterService.findByIdForRequester(5L, providerA).isEmpty());

        providerA.setProviderId(2L);
        assertTrue(encounterService.findByIdForRequester(5L, providerA).isPresent());

        providerA.setProviderId(1L);
        assertTrue(encounterService.findByIdForRequester(5L, admin).isPresent());

        User unlinkedProvider = User.builder()
                .roles(Set.of(User.Role.PROVIDER))
                .build();
        assertTrue(encounterService.findByIdForRequester(5L, unlinkedProvider).isEmpty());
    }

    @Test
    void cancelAndCompleteDoNotMutateOrSaveAnotherProvidersEncounter() {
        when(encounterRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(encounterB));

        assertFalse(encounterService.cancelEncounter(5L, providerA));
        assertEquals(EncounterStatus.IN_PROGRESS, encounterB.getStatus());
        assertEquals("existing notes", encounterB.getNotes());

        assertFalse(encounterService.completeEncounter(5L, "unauthorized notes", providerA));
        assertEquals(EncounterStatus.IN_PROGRESS, encounterB.getStatus());
        assertEquals("existing notes", encounterB.getNotes());
        verify(encounterRepository, never()).save(any(Encounter.class));
    }

    @Test
    void findByPatientIdScopesNonAdminQueriesToOwnProvider() {
        Encounter encounterA = Encounter.builder()
                .id(6L)
                .attendingProvider(Provider.builder().id(1L).build())
                .build();
        when(encounterRepository.findByPatientIdAndAttendingProviderId(7L, 1L))
                .thenReturn(Collections.singletonList(encounterA));

        assertEquals(1, encounterService.findByPatientId(7L, providerA).size());

        verify(encounterRepository).findByPatientIdAndAttendingProviderId(7L, 1L);
        verify(encounterRepository, never()).findByPatientId(7L);
    }

    @Test
    void findByProviderIdRejectsRequestsForAnotherProvider() {
        assertThrows(AccessDeniedException.class, () -> encounterService.findByProviderId(2L, providerA));
    }
}
