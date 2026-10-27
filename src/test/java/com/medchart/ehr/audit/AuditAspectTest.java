package com.medchart.ehr.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditAspectTest {

    public static class Target {
        @AuditAccess(action = AuditAction.UPDATE, resourceType = "Encounter")
        public void cancel(Long id) {
        }

        @AuditAccess(resourceType = "Encounter")
        public void byPatient(Long patientId) {
        }

        @AuditAccess(resourceType = "Patient")
        public void getPatient(Long id) {
        }
    }

    private final AuditService auditService = mock(AuditService.class);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedUserAndDoesNotTreatEncounterIdAsPatientId() {
        User principal = new User("dr.smith", "x", List.of(new SimpleGrantedAuthority("ROLE_PROVIDER")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        proxy().cancel(42L);

        AuditEvent event = captured();
        assertThat(event.getUserId()).isEqualTo("dr.smith");
        assertThat(event.getUserName()).isEqualTo("dr.smith");
        assertThat(event.getPatientId()).isNull();
    }

    @Test
    void resolvesPatientIdFromNamedParameterOrPatientResource() {
        proxy().byPatient(7L);
        assertThat(captured().getPatientId()).isEqualTo(7L);
    }

    @Test
    void patientResourceKeepsFirstLongArgumentAsPatientId() {
        proxy().getPatient(9L);
        assertThat(captured().getPatientId()).isEqualTo(9L);
        assertThat(captured().getUserId()).isEqualTo("system");
    }

    private Target proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditService));
        return factory.getProxy();
    }

    private AuditEvent captured() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
