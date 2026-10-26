package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AuditAspectUserTest {

    private final AuditAspect aspect = new AuditAspect(mock(AuditService.class));

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedUserInsteadOfSystem() {
        User user = User.builder().id(7L).username("nurse.alice").roles(Set.of(User.Role.STAFF)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));

        assertEquals("7", aspect.getCurrentUserId());
        assertEquals("nurse.alice", aspect.getCurrentUserName());
    }

    @Test
    void recordsAnonymousWhenNoOneIsLoggedIn() {
        assertEquals(AuditAspect.ANONYMOUS_USER, aspect.getCurrentUserId());

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertEquals(AuditAspect.ANONYMOUS_USER, aspect.getCurrentUserId());
        assertEquals(AuditAspect.ANONYMOUS_USER, aspect.getCurrentUserName());
    }
}
