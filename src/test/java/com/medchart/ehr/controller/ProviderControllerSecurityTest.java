package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.ProviderDTO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProviderController.class)
@Import(SecurityConfig.class)
class ProviderControllerSecurityTest {

    private static final String BODY = "{\"npi\":\"1234567890\",\"firstName\":\"Alice\",\"lastName\":\"Chen\","
            + "\"providerType\":\"PHYSICIAN\",\"active\":false,\"version\":7}";
    private static final String BODY_WITH_ID = "{\"id\":4,\"npi\":\"1234567890\",\"firstName\":\"Alice\","
            + "\"lastName\":\"Chen\",\"providerType\":\"PHYSICIAN\",\"version\":0}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProviderService providerService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void anonymousWritesAreRejected() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/providers/4"))
                .andExpect(status().isUnauthorized());

        verify(providerService, never()).create(any());
        verify(providerService, never()).update(anyLong(), any());
        verify(providerService, never()).deactivate(anyLong());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonAdminWritesAreForbidden() throws Exception {
        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/providers/4"))
                .andExpect(status().isForbidden());

        verify(providerService, never()).update(anyLong(), any());
        verify(providerService, never()).deactivate(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsBodyId() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(BODY_WITH_ID))
                .andExpect(status().isBadRequest());

        verify(providerService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreateIgnoresServerManagedFields() throws Exception {
        when(providerService.create(any())).thenReturn(new Provider());

        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<ProviderDTO> captor = ArgumentCaptor.forClass(ProviderDTO.class);
        verify(providerService).create(captor.capture());
        Provider target = new Provider();
        captor.getValue().applyTo(target);
        assertEquals(null, target.getId());
        assertEquals(null, target.getVersion());
        assertEquals(Boolean.TRUE, target.getActive());
        assertEquals("1234567890", target.getNpi());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdateUsesPathId() throws Exception {
        when(providerService.update(eq(4L), any())).thenReturn(Optional.of(new Provider()));

        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());

        verify(providerService).update(eq(4L), any());
    }
}
