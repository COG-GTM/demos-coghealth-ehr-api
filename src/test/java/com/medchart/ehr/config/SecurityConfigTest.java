package com.medchart.ehr.config;

import com.medchart.ehr.controller.EncounterController;
import com.medchart.ehr.controller.PatientController;
import com.medchart.ehr.controller.ProviderController;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.PatientService;
import com.medchart.ehr.service.ProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PatientController.class, EncounterController.class, ProviderController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "medchart.security.jwt.secret=test-secret-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "medchart.security.jwt.expiration=60000"
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private PatientService patientService;

    @MockBean
    private EncounterService encounterService;

    @MockBean
    private ProviderService providerService;

    @BeforeEach
    void setUp() {
        when(userDetailsService.loadUserByUsername(anyString())).thenAnswer(invocation -> {
            String username = invocation.getArgument(0);
            switch (username) {
                case "dr.alice":
                    return user(username, User.Role.PROVIDER);
                case "admin":
                    return user(username, User.Role.ADMIN);
                default:
                    throw new UsernameNotFoundException("unknown");
            }
        });
        when(patientService.getPatientById(1L)).thenReturn(new PatientDTO());
        when(encounterService.findByIdWithDetails(1L)).thenReturn(Optional.empty());
    }

    @Test
    void anonymousPatientRequestIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/1")).andExpect(status().isUnauthorized());
        verify(patientService, never()).getPatientById(1L);
    }

    @Test
    void anonymousEncounterRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/v1/encounters/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v1/encounters/date-range")
                        .param("startDate", "1900-01-01").param("endDate", "2100-01-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousRegistrationIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForUnknownUserIsRejected() throws Exception {
        String token = tokenProvider.generateTokenFromUsername("mallory");
        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        String token = tokenProvider.generateTokenFromUsername("dr.alice");
        mockMvc.perform(get("/v1/patients/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v1/encounters/1").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void providerDeletionRequiresAdmin() throws Exception {
        String providerToken = tokenProvider.generateTokenFromUsername("dr.alice");
        mockMvc.perform(delete("/v1/providers/1").header("Authorization", "Bearer " + providerToken))
                .andExpect(status().isForbidden());
        verify(providerService, never()).deactivate(1L);

        String adminToken = tokenProvider.generateTokenFromUsername("admin");
        mockMvc.perform(delete("/v1/providers/1").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        verify(providerService).deactivate(1L);
    }

    private static User user(String username, User.Role role) {
        return User.builder()
                .id(1L)
                .username(username)
                .password("x")
                .email(username + "@example.test")
                .firstName("Test")
                .lastName("User")
                .roles(Set.of(role))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }
}
