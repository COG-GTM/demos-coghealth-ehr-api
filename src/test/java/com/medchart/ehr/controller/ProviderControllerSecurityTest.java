package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.ProviderDTO;
import com.medchart.ehr.service.DuplicateNpiException;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProviderController.class)
@Import(SecurityConfig.class)
class ProviderControllerSecurityTest {

    private static final String VALID_BODY = "{"
            + "\"npi\":\"1234567890\","
            + "\"firstName\":\"Alice\","
            + "\"lastName\":\"Chen\","
            + "\"providerType\":\"PHYSICIAN\","
            + "\"email\":\"alice.chen@example.org\""
            + "}";

    private static final String BODY_WITH_SERVER_FIELDS = "{"
            + "\"id\":4,"
            + "\"version\":0,"
            + "\"active\":false,"
            + "\"npi\":\"9999999999\","
            + "\"firstName\":\"Mallory\","
            + "\"lastName\":\"Chen\","
            + "\"providerType\":\"PHYSICIAN\""
            + "}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderService providerService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousReadIsUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/providers")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/providers/4")).andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    void anonymousPutIsUnauthorized() throws Exception {
        mockMvc.perform(put("/v1/providers/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    void anonymousPostWithIdIsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(BODY_WITH_SERVER_FIELDS))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    void anonymousDeleteIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/v1/providers/4")).andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    void forgedBearerTokenIsUnauthorized() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken("forged")).thenThrow(new IllegalArgumentException("bad token"));

        mockMvc.perform(delete("/v1/providers/4").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void authenticatedUserCanRead() throws Exception {
        when(providerService.findAll()).thenReturn(List.of(Provider.builder().id(4L).lastName("Chen").build()));

        mockMvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(4));
    }

    @Test
    @WithMockUser(roles = {"PROVIDER", "STAFF"})
    void nonAdminCannotWrite() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/providers/4")).andExpect(status().isForbidden());
        verify(providerService, never()).create(any());
        verify(providerService, never()).update(anyLong(), any());
        verify(providerService, never()).deactivate(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPostWithServerManagedFieldsIsRejected() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(BODY_WITH_SERVER_FIELDS))
                .andExpect(status().isBadRequest());
        verify(providerService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPutWithServerManagedFieldsIsRejected() throws Exception {
        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(BODY_WITH_SERVER_FIELDS))
                .andExpect(status().isBadRequest());
        verify(providerService, never()).update(anyLong(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPostWithInvalidNpiIsRejected() throws Exception {
        String body = VALID_BODY.replace("1234567890", "12345");
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verify(providerService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanCreate() throws Exception {
        when(providerService.create(any())).thenReturn(Provider.builder().id(42L).npi("1234567890").build());

        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42));

        ArgumentCaptor<ProviderDTO> captor = ArgumentCaptor.forClass(ProviderDTO.class);
        verify(providerService).create(captor.capture());
        assertThat(captor.getValue().getNpi()).isEqualTo("1234567890");
        assertThat(captor.getValue().getLastName()).isEqualTo("Chen");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreateWithDuplicateNpiIsConflict() throws Exception {
        when(providerService.create(any())).thenThrow(new DuplicateNpiException());

        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanUpdateAndDeactivate() throws Exception {
        when(providerService.update(eq(4L), any())).thenReturn(Optional.of(Provider.builder().id(4L).build()));
        when(providerService.deactivate(4L)).thenReturn(true);

        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/v1/providers/4")).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownProviderReturnsNotFound() throws Exception {
        when(providerService.update(eq(99L), any())).thenReturn(Optional.empty());
        when(providerService.deactivate(99L)).thenReturn(false);

        mockMvc.perform(put("/v1/providers/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/v1/providers/99")).andExpect(status().isNotFound());
    }
}
