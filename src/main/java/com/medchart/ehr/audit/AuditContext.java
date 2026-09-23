package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * Resolves the acting user and request origin for audit records.
 *
 * PATTERN: Audit Logging
 * Use together with {@link PatientAccessLogger} in services that access PHI
 * outside the {@link AuditAccess} aspect (e.g. legacy native-SQL exports).
 */
@Component
@Slf4j
public class AuditContext {

    /**
     * Database id of the authenticated user, or null when the caller is not a
     * persisted user (scheduled jobs, unauthenticated access).
     */
    public Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getId();
        }
        return null;
    }

    public String getUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return "SYSTEM";
        }
        String roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining("|"));
        return roles.isEmpty() ? authentication.getName() : authentication.getName() + " [" + roles + "]";
    }

    public String getIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String getSessionId() {
        HttpServletRequest request = currentRequest();
        if (request == null || request.getSession(false) == null) {
            return null;
        }
        return request.getSession(false).getId();
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            log.debug("No request bound to the current thread", e);
            return null;
        }
    }
}
