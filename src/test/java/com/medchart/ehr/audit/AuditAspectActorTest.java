package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditAspectActorTest {

    private AuditService auditService;
    private AuditedTarget target;

    public static class AuditedTarget {
        @AuditAccess(action = AuditAction.READ, resourceType = "Patient", description = "View patient record")
        public String read(Long id) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        auditService = mock(AuditService.class);
        AspectJProxyFactory factory = new AspectJProxyFactory(new AuditedTarget());
        factory.setProxyTargetClass(true);
        factory.addAspect(new AuditAspect(auditService));
        target = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void auditEventNamesTheAuthenticatedUser() {
        User user = User.builder()
                .username("dr.smith")
                .firstName("Jane")
                .lastName("Smith")
                .roles(Set.of(User.Role.PROVIDER))
                .enabled(true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        target.read(17L);

        AuditEvent event = captureEvent();
        assertThat(event.getUserId()).isEqualTo("dr.smith");
        assertThat(event.getUserName()).isEqualTo("Jane Smith");
        assertThat(event.getPatientId()).isEqualTo(17L);
    }

    @Test
    void anonymousRequestIsNotAttributedToSystem() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));

        target.read(17L);

        assertThat(captureEvent().getUserId()).isEqualTo(AuditAspect.ANONYMOUS_USER);
    }

    @Test
    void backgroundCallsWithoutRequestAreAttributedToSystem() {
        target.read(17L);

        assertThat(captureEvent().getUserId()).isEqualTo(AuditAspect.SYSTEM_USER);
    }

    private AuditEvent captureEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService).saveAuditEventAsync(captor.capture());
        return captor.getValue();
    }
}
