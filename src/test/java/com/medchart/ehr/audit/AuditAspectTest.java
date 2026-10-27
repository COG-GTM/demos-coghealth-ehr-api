package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private final AuditService auditService = mock(AuditService.class);
    private final AuditAspect aspect = new AuditAspect(auditService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedCallerInsteadOfSystem() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "nurse.johnson", null, AuthorityUtils.createAuthorityList("ROLE_STAFF")));

        AuditEvent event = audit();

        assertEquals("nurse.johnson", event.getUserId());
        assertEquals("nurse.johnson", event.getUserName());
        assertEquals(42L, event.getPatientId());
    }

    @Test
    void truncatesLongDisplayNameButKeepsExactUserId() throws Throwable {
        String longName = "x".repeat(60);
        User user = User.builder().username("u".repeat(50)).password("x").firstName(longName).lastName(longName).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            user, null, AuthorityUtils.createAuthorityList("ROLE_STAFF")));

        AuditEvent event = audit();

        assertEquals("u".repeat(50), event.getUserId());
        assertEquals(100, event.getUserName().length());
    }

    @Test
    void recordsAnonymousWhenNoCallerIsAuthenticated() throws Throwable {
        assertEquals("anonymous", audit().getUserId());
    }

    private AuditEvent audit() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(Fixture.class.getDeclaredMethod("viewPatient", Long.class));
        when(joinPoint.getArgs()).thenReturn(new Object[] {42L});
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.auditAccess(joinPoint);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    static class Fixture {
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient")
        String viewPatient(Long id) {
            return "ok";
        }
    }
}
