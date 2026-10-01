package com.medchart.ehr.audit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * HIPAA-compliant audit logging for authentication events.
 *
 * PATTERN: Follow PatientAccessLogger pattern
 * Authentication is the security boundary of the EHR, so login success,
 * login failure and account creation must reach the durable audit trail:
 * - Who (user id / attempted username)
 * - When (timestamp)
 * - From where (IP address, user agent)
 * - Outcome (success flag, failure reason)
 *
 * Credentials are never recorded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationAuditLogger {

    static final String RESOURCE_TYPE = "Authentication";
    static final String USER_RESOURCE_TYPE = "User";
    static final String LOGIN_METRIC = "auth.login";
    static final String REGISTRATION_METRIC = "auth.registration";

    private final AuditEventRepository auditEventRepository;
    private final MeterRegistry meterRegistry;

    public void logLoginSuccess(String username) {
        AuditEvent event = baseEvent(username, AuditAction.LOGIN, RESOURCE_TYPE);
        event.setDescription("Successful authentication");
        event.setSuccess(true);

        save(event);
        meterRegistry.counter(LOGIN_METRIC, "outcome", "success").increment();

        log.info("AUDIT: Login success for user {} from {}", username, event.getIpAddress());
    }

    public void logLoginFailure(String attemptedUsername, String failureReason) {
        AuditEvent event = baseEvent(attemptedUsername, AuditAction.ACCESS_DENIED, RESOURCE_TYPE);
        event.setDescription("Failed authentication attempt");
        event.setErrorMessage(failureReason);
        event.setSuccess(false);

        save(event);
        meterRegistry.counter(LOGIN_METRIC, "outcome", "failure").increment();

        log.warn("AUDIT FAILURE: Login denied for user {} from {} - Reason: {}",
                attemptedUsername, event.getIpAddress(), failureReason);
    }

    public void logRegistration(String username, Long userId) {
        AuditEvent event = baseEvent(username, AuditAction.CREATE, USER_RESOURCE_TYPE);
        event.setResourceId(userId);
        event.setDescription("New user account created");
        event.setSuccess(true);

        save(event);
        meterRegistry.counter(REGISTRATION_METRIC, "outcome", "success").increment();

        log.info("AUDIT: Registered new user {} (id {}) from {}", username, userId, event.getIpAddress());
    }

    private AuditEvent baseEvent(String username, AuditAction action, String resourceType) {
        AuditEvent event = new AuditEvent();
        event.setUserId(username == null ? "unknown" : username);
        event.setUserName(username);
        event.setAction(action);
        event.setResourceType(resourceType);
        event.setIpAddress(getClientIpAddress());
        event.setUserAgent(truncate(getUserAgent(), 200));
        event.setTimestamp(LocalDateTime.now());
        return event;
    }

    private void save(AuditEvent event) {
        try {
            auditEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to save authentication audit event", e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String getClientIpAddress() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getUserAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
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
