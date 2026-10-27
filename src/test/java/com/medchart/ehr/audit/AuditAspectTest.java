package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditAspectTest {

    private AuditService auditService;
    private AuditedTarget proxy;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditedTarget());
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditService));
        proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsAuthenticatedUserAndAuthoritiesInsteadOfSystem() {
        User alice = User.builder()
                .username("alice.nguyen")
                .firstName("Alice")
                .lastName("Nguyen")
                .roles(EnumSet.of(User.Role.PROVIDER, User.Role.ADMIN))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities()));
        bindRequest("10.9.8.7", null);

        proxy.viewPatient(42L);

        AuditEvent event = captured();
        assertThat(event.getUserId()).isEqualTo("alice.nguyen");
        assertThat(event.getUserName()).isEqualTo("Alice Nguyen");
        assertThat(event.getRequestDetails()).isEqualTo("authorities=ROLE_ADMIN,ROLE_PROVIDER");
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getSuccess()).isTrue();
    }

    @Test
    void recordsNonDomainPrincipalByName() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "svc-reporting", null, AuthorityUtils.createAuthorityList("ROLE_STAFF")));
        bindRequest("10.9.8.7", null);

        proxy.viewPatient(42L);

        AuditEvent event = captured();
        assertThat(event.getUserId()).isEqualTo("svc-reporting");
        assertThat(event.getUserName()).isEqualTo("svc-reporting");
        assertThat(event.getRequestDetails()).isEqualTo("authorities=ROLE_STAFF");
    }

    @Test
    void unauthenticatedRequestIsRecordedAsAnonymousNotSystem() {
        bindRequest("203.0.113.9", null);

        proxy.viewPatient(42L);

        AuditEvent event = captured();
        assertThat(event.getUserId()).isEqualTo("anonymous");
        assertThat(event.getUserName()).isEqualTo(AuditAspect.ANONYMOUS_USER_NAME);
        assertThat(event.getIpAddress()).isEqualTo("203.0.113.9");
    }

    @Test
    void anonymousAuthenticationTokenIsRecordedAsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        bindRequest("203.0.113.9", null);

        proxy.viewPatient(42L);

        assertThat(captured().getUserId()).isEqualTo("anonymous");
    }

    @Test
    void callOutsideAnyRequestIsRecordedAsSystem() {
        proxy.viewPatient(42L);

        AuditEvent event = captured();
        assertThat(event.getUserId()).isEqualTo("system");
        assertThat(event.getIpAddress()).isNull();
    }

    @Test
    void ignoresClientSuppliedXForwardedFor() {
        bindRequest("10.9.8.7", "1.2.3.4, 5.6.7.8");

        proxy.viewPatient(42L);

        assertThat(captured().getIpAddress()).isEqualTo("10.9.8.7");
    }

    @Test
    void patientIdComesFromAnnotatedParameterNotFirstLong() {
        proxy.sendAlert(7L, 42L, "MRN0001");

        AuditEvent event = captured();
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getPatientMrn()).isEqualTo("MRN0001");
    }

    @Test
    void unannotatedLongArgumentsAreNotFiledAsPatientId() {
        proxy.viewProvider(7L);

        assertThat(captured().getPatientId()).isNull();
    }

    @Test
    void failedAccessIsStillAttributed() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "bob", null, AuthorityUtils.createAuthorityList("ROLE_STAFF")));
        bindRequest("10.9.8.7", "1.2.3.4");

        assertThatThrownBy(() -> proxy.failingRead(42L)).isInstanceOf(IllegalStateException.class);

        AuditEvent event = captured();
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getUserId()).isEqualTo("bob");
        assertThat(event.getIpAddress()).isEqualTo("10.9.8.7");
        assertThat(event.getPatientId()).isEqualTo(42L);
    }

    @Test
    void oversizedUserAgentIsTruncatedToColumnLength() {
        MockHttpServletRequest request = bindRequest("10.9.8.7", null);
        request.addHeader("User-Agent", "x".repeat(500));

        proxy.viewPatient(42L);

        assertThat(captured().getUserAgent()).hasSize(200);
    }

    @Test
    void oversizedMrnAndErrorMessageAreTruncatedToColumnLength() {
        proxy.sendAlert(7L, 42L, "M".repeat(64));
        assertThat(captured().getPatientMrn()).hasSize(20);

        org.mockito.Mockito.clearInvocations(auditService);
        assertThatThrownBy(() -> proxy.failingReadWithMessage(42L, "e".repeat(2000)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(captured().getErrorMessage()).hasSize(500);
    }

    private MockHttpServletRequest bindRequest(String remoteAddr, String xForwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients/42");
        request.setRemoteAddr(remoteAddr);
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Test
    void mrnLookupIsLinkedToReturnedPatientId() {
        bindRequest("10.9.8.7", null);

        proxy.viewPatientByMrn("MRN-000042");

        AuditEvent event = captured();
        assertThat(event.getPatientMrn()).isEqualTo("MRN-000042");
        assertThat(event.getPatientId()).isEqualTo(42L);
    }

    @Test
    void annotatedPatientIdWinsOverReturnedPatient() {
        bindRequest("10.9.8.7", null);

        proxy.updatePatient(7L);

        AuditEvent event = captured();
        assertThat(event.getPatientId()).isEqualTo(7L);
        assertThat(event.getPatientMrn()).isEqualTo("MRN-000042");
    }

    private AuditEvent captured() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    static class AuditedTarget {

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient")
        public String viewPatient(@PatientId Long patientId) {
            return "ok";
        }

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient")
        public PatientDTO viewPatientByMrn(@PatientMrn String mrn) {
            return patient42();
        }

        @AuditAccess(action = AuditAction.UPDATE, resourceType = "Patient")
        public PatientDTO updatePatient(@PatientId Long patientId) {
            return patient42();
        }

        private static PatientDTO patient42() {
            PatientDTO dto = new PatientDTO();
            dto.setId(42L);
            dto.setMrn("MRN-000042");
            return dto;
        }

        @AuditAccess(action = AuditAction.CREATE, resourceType = "CriticalAlert")
        public void sendAlert(Long providerId, @PatientId Long patientId, @PatientMrn String patientMrn) {
        }

        @AuditAccess(action = AuditAction.READ, resourceType = "Provider")
        public void viewProvider(Long providerId) {
        }

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient")
        public void failingRead(@PatientId Long patientId) {
            throw new IllegalStateException("boom");
        }

        @AuditAccess(action = AuditAction.READ, resourceType = "Patient")
        public void failingReadWithMessage(@PatientId Long patientId, String message) {
            throw new IllegalStateException(message);
        }
    }
}
