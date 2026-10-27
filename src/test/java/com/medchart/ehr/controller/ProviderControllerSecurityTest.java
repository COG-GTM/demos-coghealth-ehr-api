package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.provider.License;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.dto.ProviderDTO;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    @WithMockUser(roles = "ADMIN")
    void staleVersionUpdateReturnsConflict() throws Exception {
        when(providerService.update(eq(4L), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Provider.class, 4L));

        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void disabledAdminJwtIsNotAuthenticated() throws Exception {
        stubJwtUser(User.withUsername("admin").password("x").roles("ADMIN").disabled(true).build());

        mockMvc.perform(delete("/v1/providers/4").header("Authorization", "Bearer token"))
                .andExpect(status().isUnauthorized());
        verify(providerService, never()).deactivate(anyLong());
    }

    @Test
    void enabledAdminJwtIsAuthenticated() throws Exception {
        stubJwtUser(User.withUsername("admin").password("x").roles("ADMIN").build());
        when(providerService.deactivate(4L)).thenReturn(true);

        mockMvc.perform(delete("/v1/providers/4").header("Authorization", "Bearer token"))
                .andExpect(status().isNoContent());
        verify(providerService).deactivate(4L);
    }

    private void stubJwtUser(UserDetails user) {
        when(jwtTokenProvider.getUsernameFromToken("token")).thenReturn(user.getUsername());
        when(jwtTokenProvider.validateToken(eq("token"), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(user);
    }

    @Test
    void anonymousReadsAreRejected() throws Exception {
        for (String path : List.of("/v1/providers", "/v1/providers/12", "/v1/providers/npi/1234567890",
                "/v1/providers/department/Surgery", "/v1/providers/specialty/Surgery",
                "/v1/providers/departments", "/v1/providers/specialties", "/v1/providers/search?lastName=Chen")) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        verify(providerService, never()).findAll();
        verify(providerService, never()).findById(anyLong());
    }

    @Test
    @WithMockUser(roles = "PROVIDER")
    void nonAdminDirectoryOmitsPersonalAndLicenseFields() throws Exception {
        when(providerService.findAll()).thenReturn(List.of(aliceChen()));
        when(providerService.findById(12L)).thenReturn(Optional.of(aliceChen()));

        mockMvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].npi").value("1234567890"))
                .andExpect(jsonPath("$[0].phoneOffice").value("555-0100"))
                .andExpect(jsonPath("$[0].licenses[0].state").value("CA"))
                .andExpect(jsonPath("$[0].licenses[0].licenseNumber").doesNotExist())
                .andExpect(jsonPath("$[0].phoneMobile").doesNotExist())
                .andExpect(jsonPath("$[0].pager").doesNotExist())
                .andExpect(jsonPath("$[0].hireDate").doesNotExist())
                .andExpect(jsonPath("$[0].terminationDate").doesNotExist())
                .andExpect(jsonPath("$[0].version").doesNotExist());

        mockMvc.perform(get("/v1/providers/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.licenses[0].licenseNumber").doesNotExist())
                .andExpect(jsonPath("$.phoneMobile").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDirectoryIncludesFullRecord() throws Exception {
        when(providerService.findAll()).thenReturn(List.of(aliceChen()));

        mockMvc.perform(get("/v1/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].licenses[0].licenseNumber").value("A123456"))
                .andExpect(jsonPath("$[0].phoneMobile").value("555-0199"))
                .andExpect(jsonPath("$[0].pager").value("555-0142"))
                .andExpect(jsonPath("$[0].hireDate").value("2015-07-01"))
                .andExpect(jsonPath("$[0].version").value(3));
    }

    @Test
    void anonymousDeleteOfProviderReturns401() throws Exception {
        mockMvc.perform(delete("/v1/providers/1")).andExpect(status().isUnauthorized());
        verify(providerService, never()).deactivate(anyLong());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deactivateOfUnknownProviderReturns404() throws Exception {
        when(providerService.deactivate(99L)).thenReturn(false);

        mockMvc.perform(delete("/v1/providers/99")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRequiresMandatoryFields() throws Exception {
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Alice\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/providers").contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("1234567890", "12345")))
                .andExpect(status().isBadRequest());

        verify(providerService, never()).create(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void partialUpdateIsAcceptedAndLicensesAreValidated() throws Exception {
        when(providerService.update(eq(4L), any())).thenReturn(Optional.of(new Provider()));

        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"department\":\"Cardiology\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/v1/providers/4").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"licenses\":[{\"licenseType\":\"MD\"}]}"))
                .andExpect(status().isBadRequest());

        verify(providerService).update(eq(4L), any());
    }

    @Test
    void anonymousRequestsOutsideTheAllowlistAreRejected() throws Exception {
        mockMvc.perform(get("/v1/patients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/export/patients")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    private static Provider aliceChen() {
        Provider provider = Provider.builder()
                .id(12L)
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Chen")
                .providerType(ProviderType.PHYSICIAN)
                .specialty("Surgery")
                .department("Surgery")
                .email("achen@mercy.example")
                .phoneOffice("555-0100")
                .phoneMobile("555-0199")
                .pager("555-0142")
                .hireDate(LocalDate.of(2015, 7, 1))
                .version(3L)
                .build();
        provider.setLicenses(new java.util.HashSet<>(Set.of(License.builder()
                .licenseType("MD").licenseNumber("A123456").state("CA").status("ACTIVE").build())));
        return provider;
    }
}
