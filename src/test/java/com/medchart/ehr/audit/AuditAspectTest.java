package com.medchart.ehr.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void recordsAnonymousWhenNoCallerIsAuthenticated() throws Throwable {
        assertEquals("anonymous", audit().getUserId());
    }

    @Test
    void encounterKeyedAccessIsAttributedToThePatientFromTheResult() throws Throwable {
        PatientScoped result = () -> 7L;

        AuditEvent event = audit("viewEncounter", Optional.of(result));

        assertEquals(7L, event.getPatientId());
        assertEquals("Encounter", event.getResourceType());
    }

    @Test
    void encounterIdIsNotRecordedAsPatientIdWhenEncounterIsMissing() throws Throwable {
        assertNull(audit("viewEncounter", Optional.empty()).getPatientId());
    }

    private AuditEvent audit() throws Throwable {
        return audit("viewPatient", "ok");
    }

    private AuditEvent audit(String method, Object result) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(Fixture.class.getDeclaredMethod(method, Long.class));
        when(joinPoint.getArgs()).thenReturn(new Object[] {42L});
        when(joinPoint.proceed()).thenReturn(result);

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

        @AuditAccess(action = AuditAction.READ, resourceType = "Encounter", description = "View encounter",
                patientIdFromArgs = false)
        Optional<PatientScoped> viewEncounter(Long encounterId) {
            return Optional.empty();
        }
    }
}
