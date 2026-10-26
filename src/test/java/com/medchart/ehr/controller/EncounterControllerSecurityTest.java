package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.service.EncounterNotFoundException;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.InvalidEncounterTransitionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EncounterController.class)
@Import(SecurityConfig.class)
class EncounterControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousCancelIsUnauthorized() throws Exception {
        mvc.perform(post("/v1/encounters/42/cancel")).andExpect(status().isUnauthorized());
        verifyNoInteractions(encounterService);
    }

    @Test
    void anonymousCompleteAndPutAreUnauthorized() throws Exception {
        mvc.perform(post("/v1/encounters/42/complete").content("Mallory was here"))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v1/encounters/42")).andExpect(status().isUnauthorized());
        verifyNoInteractions(encounterService);
    }

    @Test
    void invalidBearerTokenIsUnauthorized() throws Exception {
        mvc.perform(post("/v1/encounters/42/cancel").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(encounterService);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanCheckIn() throws Exception {
        mvc.perform(post("/v1/encounters/42/check-in")).andExpect(status().isOk());
        verify(encounterService).checkIn(42L);
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCannotStartOrComplete() throws Exception {
        mvc.perform(post("/v1/encounters/42/start")).andExpect(status().isForbidden());
        mvc.perform(post("/v1/encounters/42/complete").content("note")).andExpect(status().isForbidden());
        verify(encounterService, never()).startEncounter(anyLong());
        verify(encounterService, never()).completeEncounter(anyLong(), any());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    void unknownRoleIsForbidden() throws Exception {
        mvc.perform(post("/v1/encounters/42/cancel")).andExpect(status().isForbidden());
        verifyNoInteractions(encounterService);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void cancellingCompletedEncounterIsConflict() throws Exception {
        doThrow(new InvalidEncounterTransitionException(EncounterStatus.COMPLETED, EncounterStatus.CANCELLED))
                .when(encounterService).cancelEncounter(42L);
        mvc.perform(post("/v1/encounters/42/cancel")).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void unknownEncounterIsNotFound() throws Exception {
        doThrow(new EncounterNotFoundException(99L)).when(encounterService).markNoShow(99L);
        mvc.perform(post("/v1/encounters/99/no-show")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void providerCanComplete() throws Exception {
        mvc.perform(post("/v1/encounters/42/complete").content("Follow up")).andExpect(status().isOk());
        verify(encounterService).completeEncounter(eq(42L), eq("Follow up"));
    }

    @Test
    @WithMockUser(roles = "STAFF")
    void putPassesOnlySuppliedFieldNames() throws Exception {
        mvc.perform(put("/v1/encounters/42").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"room\":null,\"department\":\"Cardiology\"}"))
                .andExpect(status().isOk());
        verify(encounterService).update(eq(42L),
                argThat(e -> e.getRoom() == null && "Cardiology".equals(e.getDepartment())),
                eq(Set.of("room", "department")));
    }
}
