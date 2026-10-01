package com.medchart.ehr.audit;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Authentication audit trail and telemetry.
 *
 * PATTERN: Audit Logging (see PatientAccessLogger)
 * Records who authenticated, when, from where, and whether it succeeded, so that
 * HIPAA login monitoring and brute-force / credential-stuffing detection are possible.
 * Credentials and issued tokens are never recorded.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationAuditService {

    private static final String RESOURCE_AUTHENTICATION = "Authentication";
    private static final String RESOURCE_ACCOUNT = "Account";
    private static final String ANONYMOUS = "anonymous";

    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public void recordLoginSuccess(String username) {
        String actor = actor(username);
        save(actor, AuditAction.LOGIN, RESOURCE_AUTHENTICATION, "Successful login", true, null);
        meterRegistry.counter("auth.login.success").increment();
        log.info("AUDIT: successful login for user {} from {}", actor, clientIpAddress());
    }

    public void recordLoginFailure(String username, String reason) {
        String actor = actor(username);
        String ipAddress = clientIpAddress();
        save(actor, AuditAction.ACCESS_DENIED, RESOURCE_AUTHENTICATION, "Failed login", false, reason);
        meterRegistry.counter("auth.login.failures", "reason", reason == null ? "unknown" : reason).increment();
        log.warn("AUDIT FAILURE: failed login for user {} from {} - reason: {}", actor, ipAddress, reason);
    }

    public void recordRegistration(String username) {
        String actor = actor(username);
        save(actor, AuditAction.CREATE, RESOURCE_ACCOUNT, "Provider account created", true, null);
        meterRegistry.counter("auth.registrations").increment();
        log.info("AUDIT: provider account created for user {} from {}", actor, clientIpAddress());
    }

    private void save(String actor, AuditAction action, String resourceType, String description,
                      boolean success, String errorMessage) {
        auditService.saveAuditEventAsync(AuditEvent.builder()
                .userId(actor)
                .userName(actor)
                .action(action)
                .resourceType(resourceType)
                .description(description)
                .ipAddress(clientIpAddress())
                .userAgent(userAgent())
                .success(success)
                .errorMessage(errorMessage)
                .build());
    }

    private String actor(String username) {
        return username == null || username.isEmpty() ? ANONYMOUS : username;
    }

    private String clientIpAddress() {
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

    private String userAgent() {
        HttpServletRequest request = currentRequest();
        return request == null ? null : request.getHeader("User-Agent");
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? null : attrs.getRequest();
        } catch (Exception e) {
            log.debug("Could not resolve current request", e);
            return null;
        }
    }
}
