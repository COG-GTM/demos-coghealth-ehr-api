package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.dto.ProviderCreateRequest;
import com.medchart.ehr.dto.ProviderResponse;
import com.medchart.ehr.dto.ProviderUpdateRequest;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProviderController.class)
@Import(SecurityConfig.class)
class ProviderControllerSecurityTest {

    private static final String OVERWRITE_BODY = "{\"id\":999,\"version\":0,\"npi\":\"0000000000\","
            + "\"createdAt\":\"2020-01-01T00:00:00\"}";
    private static final String CREATE_BODY = "{\"npi\":\"1112223334\",\"firstName\":\"Bob\","
            + "\"lastName\":\"Lee\",\"providerType\":\"PHARMACIST\",\"id\":5,\"version\":3,"
            + "\"active\":false,\"terminationDate\":\"2020-01-01\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderService providerService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    private static ProviderResponse alice() {
        return ProviderResponse.builder().id(12L).npi("1234567890").firstName("Alice").lastName("Nguyen")
                .providerType(ProviderType.PHYSICIAN).active(true).build();
    }

    @Test
    void anonymousWritesAreRejectedWith401() throws Exception {
        mockMvc.perform(put("/v1/providers/12").contentType(MediaType.APPLICATION_JSON).content(OVERWRITE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/providers/12"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    void invalidBearerTokenIsTreatedAsAnonymous() throws Exception {
        when(jwtTokenProvider.getUsernameFromToken("forged")).thenThrow(new IllegalArgumentException("bad token"));

        mockMvc.perform(put("/v1/providers/12").header("Authorization", "Bearer forged")
                        .contentType(MediaType.APPLICATION_JSON).content(OVERWRITE_BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(providerService);
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonAdminWritesAreForbidden() throws Exception {
        mockMvc.perform(put("/v1/providers/12").contentType(MediaType.APPLICATION_JSON).content(OVERWRITE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/providers/12"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(providerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdateBindsOnlyAllowlistedFields() throws Exception {
        when(providerService.update(eq(12L), any())).thenReturn(Optional.of(alice()));

        mockMvc.perform(put("/v1/providers/12").contentType(MediaType.APPLICATION_JSON).content(OVERWRITE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.version").doesNotExist());

        ArgumentCaptor<ProviderUpdateRequest> captor = ArgumentCaptor.forClass(ProviderUpdateRequest.class);
        verify(providerService).update(eq(12L), captor.capture());
        assertThat(captor.getValue().getNpi()).isEqualTo("0000000000");
        assertThat(captor.getValue().getFirstName()).isNull();
        assertThat(captor.getValue().getLicenses()).isNull();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreateIgnoresServerManagedFields() throws Exception {
        when(providerService.create(any())).thenReturn(alice());

        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<ProviderCreateRequest> captor = ArgumentCaptor.forClass(ProviderCreateRequest.class);
        verify(providerService).create(captor.capture());
        assertThat(captor.getValue().getNpi()).isEqualTo("1112223334");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void invalidPayloadsAreRejectedWith400() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"lastName\":\"Lee\",\"providerType\":\"PHARMACIST\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/v1/providers/12").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"npi\":\"12AB\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/v1/providers/12").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastName\":\"   \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(providerService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateNpiMapsTo409() throws Exception {
        when(providerService.update(eq(12L), any())).thenThrow(new DuplicateNpiException());

        mockMvc.perform(put("/v1/providers/12").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"npi\":\"9999999999\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateOfUnknownProviderReturns404() throws Exception {
        when(providerService.update(eq(404L), any())).thenReturn(Optional.empty());

        mockMvc.perform(put("/v1/providers/404").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void readsStayOpenAndDoNotExposeVersion() throws Exception {
        when(providerService.findById(12L)).thenReturn(Optional.of(alice()));

        mockMvc.perform(get("/v1/providers/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.npi").value("1234567890"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }
}
