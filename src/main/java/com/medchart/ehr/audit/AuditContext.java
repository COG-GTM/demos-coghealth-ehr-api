package com.medchart.ehr.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

/**
 * Resolves the actor and request metadata (user, role, IP, session) that every
 * HIPAA audit event must carry.
 */
@Component
@Slf4j
public class AuditContext {

    public static final String ANONYMOUS_USER = "anonymous";
    public static final String SYSTEM_USER = "system";

    public String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return isRequestScoped() ? ANONYMOUS_USER : SYSTEM_USER;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return authentication.getName() != null ? authentication.getName() : ANONYMOUS_USER;
    }

    public String getUserRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null
                || authentication.getAuthorities().isEmpty()) {
            return isRequestScoped() ? ANONYMOUS_USER : SYSTEM_USER;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }

    public String getIpAddress() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    public String getSessionId() {
        HttpServletRequest request = getRequest();
        if (request == null || request.getSession(false) == null) {
            return null;
        }
        return request.getSession(false).getId();
    }

    private boolean isRequestScoped() {
        return getRequest() != null;
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            log.debug("Could not resolve current request", e);
            return null;
        }
    }
}
