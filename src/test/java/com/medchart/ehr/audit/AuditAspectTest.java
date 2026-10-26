package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private AuditService auditService;
    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        aspect = new AuditAspect(auditService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void recordsAuthenticatedUsernameNameAndRoles() throws Throwable {
        User user = clinician("dr.chen", "Mei", "Chen", User.Role.PROVIDER);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        bindRequest("GET", "/api/v1/patients/42");

        AuditEvent event = invokeAudited(42L);

        assertThat(event.getUserId()).isEqualTo("dr.chen");
        assertThat(event.getUserName()).isEqualTo("Mei Chen");
        assertThat(event.getRequestDetails()).isEqualTo("roles=ROLE_PROVIDER; request=GET /api/v1/patients/42");
        assertThat(event.getPatientId()).isEqualTo(42L);
        assertThat(event.getSuccess()).isTrue();
    }

    @Test
    void recordsGenericUserDetailsPrincipal() throws Throwable {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User("front.desk", "x",
                        AuthorityUtils.createAuthorityList("ROLE_STAFF"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        bindRequest("GET", "/api/v1/patients/7");

        AuditEvent event = invokeAudited(7L);

        assertThat(event.getUserId()).isEqualTo("front.desk");
        assertThat(event.getRequestDetails()).startsWith("roles=ROLE_STAFF;");
    }

    @Test
    void anonymousRequestIsRecordedAsAnonymousWithRequestPath() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        bindRequest("PUT", "/api/v1/patients/9");

        AuditEvent event = invokeAudited(9L);

        assertThat(event.getUserId()).isEqualTo(AuditAspect.ANONYMOUS_USER_ID);
        assertThat(event.getUserName()).isEqualTo(AuditAspect.ANONYMOUS_USER_NAME);
        assertThat(event.getRequestDetails()).isEqualTo("unauthenticated; request=PUT /api/v1/patients/9");
    }

    @Test
    void requestWithoutAnyAuthenticationIsRecordedAsAnonymous() throws Throwable {
        bindRequest("GET", "/api/v1/patients/3");

        AuditEvent event = invokeAudited(3L);

        assertThat(event.getUserId()).isEqualTo("anonymous");
        assertThat(event.getRequestDetails()).contains("/api/v1/patients/3");
    }

    @Test
    void backgroundCallWithoutRequestOrAuthenticationIsRecordedAsSystem() throws Throwable {
        AuditEvent event = invokeAudited(5L);

        assertThat(event.getUserId()).isEqualTo(AuditAspect.SYSTEM_USER_ID);
        assertThat(event.getUserName()).isEqualTo(AuditAspect.SYSTEM_USER_NAME);
    }

    @Test
    void failedCallStillAttributesTheUser() throws Throwable {
        User user = clinician("nurse.ray", "Ray", "Ortiz", User.Role.STAFF);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        bindRequest("GET", "/api/v1/patients/404");

        ProceedingJoinPoint joinPoint = joinPoint(404L);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.auditAccess(joinPoint)).isInstanceOf(IllegalStateException.class);

        AuditEvent event = captureEvent();
        assertThat(event.getUserId()).isEqualTo("nurse.ray");
        assertThat(event.getSuccess()).isFalse();
    }

    @Test
    void longUserNamesAreTruncatedToColumnLength() {
        String longName = "u".repeat(150);
        User user = clinician(longName, "A", "B", User.Role.ADMIN);

        AuditAspect.AuditActor actor = AuditAspect.resolveActor(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()), null);

        assertThat(actor.userId).hasSize(100);
        assertThat(actor.details).isEqualTo("roles=ROLE_ADMIN");
    }

    private AuditEvent invokeAudited(Long patientId) throws Throwable {
        ProceedingJoinPoint joinPoint = joinPoint(patientId);
        when(joinPoint.proceed()).thenReturn("ok");
        aspect.auditAccess(joinPoint);
        return captureEvent();
    }

    private ProceedingJoinPoint joinPoint(Long patientId) throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(AuditedTarget.class.getMethod("view", Long.class));
        when(joinPoint.getArgs()).thenReturn(new Object[]{patientId});
        return joinPoint;
    }

    private AuditEvent captureEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }

    private static void bindRequest(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static User clinician(String username, String first, String last, User.Role role) {
        return User.builder()
                .username(username)
                .password("x")
                .email(username + "@example.test")
                .firstName(first)
                .lastName(last)
                .roles(Set.of(role))
                .enabled(true)
                .accountNonExpired(true)
                .accountNonLocked(true)
                .credentialsNonExpired(true)
                .build();
    }

    static class AuditedTarget {
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
        public String view(Long id) {
            return "ok";
        }
    }
}
