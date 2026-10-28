package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.repository.EncounterRepository;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EncounterController.class)
@Import({SecurityConfig.class, EncounterService.class})
class EncounterControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterRepository encounterRepository;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private PatientAccessLogger patientAccessLogger;

    @Test
    void unauthenticatedRequestsAreUnauthorizedForEncounterRoutes() throws Exception {
        mockMvc.perform(get("/v1/encounters/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/encounters/patient/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/encounters/date-range")
                        .param("startDate", "2000-01-01")
                        .param("endDate", "2030-12-31"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/encounters/1/cancel")).andExpect(status().isUnauthorized());
    }

    @Test
    void providerCannotReadAnotherProvidersEncounter() throws Exception {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(200L)));

        mockMvc.perform(get("/v1/encounters/1").with(user(provider(10L, 100L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCanReadOwnEncounter() throws Exception {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(100L)));

        mockMvc.perform(get("/v1/encounters/1").with(user(provider(10L, 100L))))
                .andExpect(status().isOk());
    }

    @Test
    void providerCannotListAnotherProvidersEncounters() throws Exception {
        mockMvc.perform(get("/v1/encounters/provider/200").with(user(provider(10L, 100L))))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCannotCancelAnotherProvidersEncounter() throws Exception {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(200L)));

        mockMvc.perform(post("/v1/encounters/1/cancel").with(user(provider(10L, 100L))))
                .andExpect(status().isForbidden());

        verify(encounterRepository, never()).save(any());
    }

    @Test
    void adminCanReadAnotherProvidersEncounter() throws Exception {
        when(encounterRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter(200L)));

        mockMvc.perform(get("/v1/encounters/1").with(user(admin())))
                .andExpect(status().isOk());
    }

    private Encounter encounter(Long providerId) {
        return Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2024-000001")
                .patient(Patient.builder().id(5L).build())
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
