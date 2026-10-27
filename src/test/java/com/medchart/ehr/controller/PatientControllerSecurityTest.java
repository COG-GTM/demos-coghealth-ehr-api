package com.medchart.ehr.controller;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class)
@Import(SecurityConfig.class)
class PatientControllerSecurityTest {

    private static final String PATIENT_JSON = "{\"firstName\":\"Alice\",\"lastName\":\"Example\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientService patientService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        registerUser("provider-token", user("dr.smith", User.Role.PROVIDER, true));
        registerUser("staff-token", user("front.desk", User.Role.STAFF, true));
        registerUser("admin-token", user("it.admin", User.Role.ADMIN, true));
        registerUser("disabled-token", user("former.staff", User.Role.STAFF, false));
        when(tokenProvider.getUsernameFromToken("forged-token")).thenThrow(new IllegalArgumentException("bad signature"));
        when(userDetailsService.loadUserByUsername("ghost")).thenThrow(new UsernameNotFoundException("ghost"));
        when(tokenProvider.getUsernameFromToken("unknown-user-token")).thenReturn("ghost");

        PatientDTO dto = new PatientDTO();
        dto.setId(17L);
        dto.setFirstName("Alice");
        dto.setLastName("Example");
        when(patientService.getPatientById(anyLong())).thenReturn(dto);
        when(patientService.getPatientByMrn(anyString())).thenReturn(dto);
        when(patientService.searchPatients(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(dto)));
        when(patientService.createPatient(any(PatientDTO.class))).thenReturn(dto);
        when(patientService.updatePatient(anyLong(), any(PatientDTO.class))).thenReturn(dto);
    }

    @Test
    void anonymousCallersGet401OnEveryPatientEndpoint() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", "").param("size", "2000"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/17")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/patients/mrn/MRN123")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/v1/patients").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/v1/patients/17").contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(patientService);
    }

    @Test
    void forgedUnknownOrDisabledTokensAreRejected() throws Exception {
        for (String token : new String[] {"forged-token", "unknown-user-token", "disabled-token"}) {
            mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(patientService);
    }

    @Test
    void clinicalRolesCanReadPatients() throws Exception {
        for (String token : new String[] {"provider-token", "staff-token", "admin-token"}) {
            mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/v1/patients/mrn/MRN123").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/v1/patients/search").param("q", "ali").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void userWithoutAnyRoleIsForbidden() throws Exception {
        User noRole = user("new.signup", null, true);
        noRole.setRoles(Collections.emptySet());
        registerUser("norole-token", noRole);

        mockMvc.perform(get("/v1/patients/17").header("Authorization", "Bearer norole-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(patientService);
    }

    @Test
    void providersCannotCreateOrRewriteDemographics() throws Exception {
        mockMvc.perform(post("/v1/patients").header("Authorization", "Bearer provider-token")
                        .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/v1/patients/17").header("Authorization", "Bearer provider-token")
                        .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                .andExpect(status().isForbidden());

        verify(patientService, never()).createPatient(any());
        verify(patientService, never()).updatePatient(anyLong(), any());
    }

    @Test
    void frontDeskAndAdminCanCreateAndUpdate() throws Exception {
        for (String token : new String[] {"staff-token", "admin-token"}) {
            mockMvc.perform(post("/v1/patients").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                    .andExpect(status().isCreated());
            mockMvc.perform(put("/v1/patients/17").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(PATIENT_JSON))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void searchRejectsBlankOrOneCharacterTerms() throws Exception {
        for (String q : new String[] {"", "   ", "a"}) {
            mockMvc.perform(get("/v1/patients/search").param("q", q).header("Authorization", "Bearer provider-token"))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(patientService);
    }

    @Test
    void searchPageSizeIsCapped() throws Exception {
        mockMvc.perform(get("/v1/patients/search").param("q", " smi ").param("size", "2000")
                        .header("Authorization", "Bearer provider-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientService).searchPatients(eq("smi"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(PatientController.MAX_PAGE_SIZE);
    }

    private void registerUser(String token, User user) {
        when(tokenProvider.getUsernameFromToken(token)).thenReturn(user.getUsername());
        when(tokenProvider.validateToken(eq(token), any())).thenReturn(true);
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(user);
    }

    private static User user(String username, User.Role role, boolean enabled) {
        return User.builder()
                .username(username)
                .password("x")
                .email(username + "@example.test")
                .firstName("Test")
                .lastName(username)
                .roles(role == null ? Collections.emptySet() : Set.of(role))
                .enabled(enabled)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }
}
