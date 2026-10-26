package com.medchart.ehr.audit;

import com.medchart.ehr.config.JwtTokenProvider;
import com.medchart.ehr.config.SecurityConfig;
import com.medchart.ehr.controller.PatientController;
import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.mapper.PatientMapper;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.service.PatientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PatientController.class)
@Import({SecurityConfig.class, PatientService.class, AuditAspect.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
class PatientAuditAttributionTest {

    private static final String TOKEN = "signed.jwt.token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuditService auditService;
    @MockBean
    private PatientRepository patientRepository;
    @MockBean
    private PatientMapper patientMapper;
    @MockBean
    private JwtTokenProvider tokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        Patient patient = new Patient();
        when(patientRepository.findById(11L)).thenReturn(Optional.of(patient));
        when(patientMapper.toDto(patient)).thenReturn(new PatientDTO());
    }

    @Test
    void auditEventCarriesTheLoggedInUsername() throws Exception {
        User user = User.builder()
                .username("dr.chen")
                .password("x")
                .email("dr.chen@example.test")
                .firstName("Mei")
                .lastName("Chen")
                .roles(Set.of(User.Role.PROVIDER))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
        when(tokenProvider.getUsernameFromToken(TOKEN)).thenReturn("dr.chen");
        when(userDetailsService.loadUserByUsername("dr.chen")).thenReturn(user);
        when(tokenProvider.validateToken(eq(TOKEN), any())).thenReturn(true);

        mockMvc.perform(get("/v1/patients/11").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("dr.chen");
        assertThat(event.getUserName()).isEqualTo("Mei Chen");
        assertThat(event.getRequestDetails()).isEqualTo("roles=ROLE_PROVIDER; request=GET /v1/patients/11");
        assertThat(event.getPatientId()).isEqualTo(11L);
        assertThat(event.getAction()).isEqualTo(AuditAction.READ);
    }

    @Test
    void unauthenticatedAccessIsRecordedAsAnonymousNotSystem() throws Exception {
        mockMvc.perform(get("/v1/patients/11")).andExpect(status().isOk());

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("anonymous");
        assertThat(event.getRequestDetails()).isEqualTo("unauthenticated; request=GET /v1/patients/11");
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
