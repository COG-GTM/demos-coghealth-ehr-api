package com.medchart.ehr.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private AuditService auditService;
    private AuditAspect auditAspect;
    private ProceedingJoinPoint joinPoint;

    static class SampleService {
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient")
        Object getPatient(Long patientId) {
            return patientId;
        }
    }

    @BeforeEach
    void setUp() throws Throwable {
        auditService = mock(AuditService.class);
        auditAspect = new AuditAspect(auditService);

        Method method = SampleService.class.getDeclaredMethod("getPatient", Long.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);

        joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L});
        when(joinPoint.proceed()).thenReturn("ok");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private AuditEvent capturedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    @Test
    void recordsAuthenticatedPrincipalAsActor() throws Throwable {
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                "dr.patel", "", AuthorityUtils.createAuthorityList("ROLE_PROVIDER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        auditAspect.auditAccess(joinPoint);

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("dr.patel");
        assertThat(event.getUserName()).isEqualTo("ROLE_PROVIDER");
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getSuccess()).isTrue();
    }

    @Test
    void recordsAnonymousWhenRequestIsUnauthenticated() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        auditAspect.auditAccess(joinPoint);

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo(AuditAspect.ANONYMOUS_USER_ID);
        assertThat(event.getUserName()).isEqualTo(AuditAspect.ANONYMOUS_USER_NAME);
    }

    @Test
    void fallsBackToSystemWhenNoSecurityContext() throws Throwable {
        auditAspect.auditAccess(joinPoint);

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo(AuditAspect.SYSTEM_USER_ID);
        assertThat(event.getUserName()).isEqualTo(AuditAspect.SYSTEM_USER_NAME);
    }

    @Test
    void recordsActorOnFailedInvocation() throws Throwable {
        UserDetails principal = new org.springframework.security.core.userdetails.User(
                "nurse.lee", "", AuthorityUtils.createAuthorityList("ROLE_STAFF"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        try {
            auditAspect.auditAccess(joinPoint);
        } catch (IllegalStateException expected) {
            // audit event is still recorded for failed access
        }

        AuditEvent event = capturedEvent();
        assertThat(event.getUserId()).isEqualTo("nurse.lee");
        assertThat(event.getSuccess()).isFalse();
        assertThat(event.getErrorMessage()).isEqualTo("boom");
        verify(auditService).saveAuditEventAsync(any(AuditEvent.class));
    }
}
