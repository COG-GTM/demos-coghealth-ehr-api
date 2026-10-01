package com.medchart.ehr.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Resolves the actor and request metadata recorded on audit events.
 */
@Component
@Slf4j
public class RequestAuditContext {

    private static final String ANONYMOUS_USER = "anonymous";
    private static final String SYSTEM_USER = "system";

    public String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return SYSTEM_USER;
        }
        String name = authentication.getName();
        if (name == null || name.isEmpty() || !authentication.isAuthenticated()) {
            return ANONYMOUS_USER;
        }
        return name;
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

    public String getUserAgent() {
        HttpServletRequest request = getRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    public String getSessionId() {
        HttpServletRequest request = getRequest();
        if (request == null || request.getSession(false) == null) {
            return null;
        }
        return request.getSession(false).getId();
    }

    private HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            log.debug("Could not resolve current request", e);
            return null;
        }
    }
}
