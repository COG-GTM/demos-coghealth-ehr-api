package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EncounterController.class)
@Import(SecurityConfig.class)
class EncounterControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void encounterReadAndMutationRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/v1/encounters/5"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/encounters/5/cancel"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/encounters/patient/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inaccessibleEncounterAndStateChangeReturnNotFound() throws Exception {
        User providerA = providerA();
        when(encounterService.findByIdForRequester(5L, providerA)).thenReturn(Optional.empty());
        when(encounterService.cancelEncounter(5L, providerA)).thenReturn(false);

        mockMvc.perform(get("/v1/encounters/5").with(user(providerA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/encounters/5/cancel").with(user(providerA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerCannotRequestAnotherProvidersEncounterList() throws Exception {
        User providerA = providerA();
        when(encounterService.findByProviderId(2L, providerA))
                .thenThrow(new AccessDeniedException("Cannot access another provider's encounters"));

        mockMvc.perform(get("/v1/encounters/provider/2").with(user(providerA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerCanReadAnEncounterReturnedByTheService() throws Exception {
        User providerA = providerA();
        when(encounterService.findByIdForRequester(5L, providerA))
                .thenReturn(Optional.of(Encounter.builder().id(5L).build()));

        mockMvc.perform(get("/v1/encounters/5").with(user(providerA)))
                .andExpect(status().isOk());
    }

    private User providerA() {
        return User.builder()
                .id(10L)
                .username("provider-a")
                .providerId(1L)
                .roles(Set.of(User.Role.PROVIDER))
                .build();
    }
}
