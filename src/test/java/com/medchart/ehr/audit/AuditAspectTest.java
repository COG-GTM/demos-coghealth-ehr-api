package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.service.EncounterService;
import com.medchart.ehr.service.PatientService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    private AuditService auditService;

    private AuditAspect auditAspect;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        auditAspect = new AuditAspect(auditService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void encounterReadRecordsActorAndEncounterResourceWithoutPatientId() throws Throwable {
        User user = user();
        authenticateAs(user);

        Method method = EncounterService.class.getMethod(
                "findByIdForRequester", Long.class, User.class);
        AuditEvent event = audit(method, parameterNames(method), 54L, user);

        assertEquals(Long.valueOf(54L), event.getResourceId());
        assertNull(event.getPatientId());
        assertEquals(String.valueOf(user.getId()), event.getUserId());
        assertEquals(user.getUsername(), event.getUserName());
    }

    @Test
    void patientEncounterListRecordsNamedPatientId() throws Throwable {
        Method method = EncounterService.class.getMethod(
                "findByPatientId", Long.class, User.class);
        AuditEvent event = audit(method, parameterNames(method), 27L, user());

        assertEquals(27L, event.getPatientId());
        assertNull(event.getResourceId());
    }

    @Test
    void patientReadUsesLongArgumentWhenParameterNamesAreUnavailable() throws Throwable {
        Method method = PatientService.class.getMethod("getPatientById", Long.class);
        AuditEvent event = audit(method, null, 81L);

        assertEquals(81L, event.getPatientId());
        assertEquals(81L, event.getResourceId());
    }

    @Test
    void missingAuthenticationUsesSystemActor() throws Throwable {
        Method method = EncounterService.class.getMethod(
                "findByIdForRequester", Long.class, User.class);
        AuditEvent event = audit(method, parameterNames(method), 54L, user());

        assertEquals("system", event.getUserId());
        assertEquals("System User", event.getUserName());
    }

    @Test
    void authenticatedNonUserPrincipalUsesAuthenticationName() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("service-actor", null, Set.of()));
        Method method = EncounterService.class.getMethod(
                "findByIdForRequester", Long.class, User.class);
        AuditEvent event = audit(method, parameterNames(method), 54L, user());

        assertEquals("service-actor", event.getUserId());
        assertEquals("service-actor", event.getUserName());
    }

    private AuditEvent audit(Method method, String[] parameterNames, Object... arguments) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(parameterNames);
        when(joinPoint.getArgs()).thenReturn(arguments);
        when(joinPoint.proceed()).thenReturn(null);

        auditAspect.auditAccess(joinPoint);

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private String[] parameterNames(Method method) {
        return Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getName())
                .toArray(String[]::new);
    }

    private void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private User user() {
        return User.builder()
                .id(42L)
                .username("audit-user")
                .roles(Set.of(User.Role.PROVIDER))
                .build();
    }
}
