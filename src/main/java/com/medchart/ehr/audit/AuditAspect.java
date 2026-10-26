package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import com.medchart.ehr.dto.PatientDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    static final String ANONYMOUS_USER_ID = "anonymous";
    static final String ANONYMOUS_USER_NAME = "Unauthenticated Request";
    static final String SYSTEM_USER_ID = "system";
    static final String SYSTEM_USER_NAME = "System Process";

    private static final int USER_FIELD_MAX_LENGTH = 100;

    private final AuditService auditService;

    @Around("@annotation(com.medchart.ehr.audit.AuditAccess)")
    public Object auditAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAccess auditAccess = method.getAnnotation(AuditAccess.class);

        Long patientId = extractPatientId(joinPoint.getArgs());
        HttpServletRequest request = getCurrentRequest();
        AuditActor actor = resolveActor(SecurityContextHolder.getContext().getAuthentication(), request);

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .userId(actor.userId)
                .userName(actor.userName)
                .requestDetails(actor.details)
                .patientId(patientId)
                .action(auditAccess.action())
                .resourceType(auditAccess.resourceType())
                .description(auditAccess.description())
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent());

        try {
            Object result = joinPoint.proceed();
            if (patientId == null && result instanceof PatientDTO) {
                eventBuilder.patientId(((PatientDTO) result).getId());
            }
            eventBuilder.success(true);
            auditService.saveAuditEventAsync(eventBuilder.build());
            return result;
        } catch (Exception e) {
            eventBuilder.success(false);
            eventBuilder.errorMessage(e.getMessage());
            auditService.saveAuditEventAsync(eventBuilder.build());
            throw e;
        }
    }

    private Long extractPatientId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        return null;
    }

    static AuditActor resolveActor(Authentication authentication, HttpServletRequest request) {
        String requestLine = describeRequest(request);

        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            if (request == null) {
                return new AuditActor(SYSTEM_USER_ID, SYSTEM_USER_NAME, "no request context");
            }
            return new AuditActor(ANONYMOUS_USER_ID, ANONYMOUS_USER_NAME, "unauthenticated; request=" + requestLine);
        }

        Object principal = authentication.getPrincipal();
        String userId;
        String userName;
        if (principal instanceof User) {
            User user = (User) principal;
            userId = user.getUsername();
            userName = (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName())).trim();
        } else if (principal instanceof UserDetails) {
            userId = ((UserDetails) principal).getUsername();
            userName = userId;
        } else {
            userId = authentication.getName();
            userName = userId;
        }
        if (userId == null || userId.isEmpty()) {
            userId = ANONYMOUS_USER_ID;
        }
        if (userName == null || userName.isEmpty()) {
            userName = userId;
        }

        String roles = authentication.getAuthorities() == null ? "" : authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .sorted()
                .collect(Collectors.joining(","));
        String details = "roles=" + roles + (requestLine != null ? "; request=" + requestLine : "");
        return new AuditActor(truncate(userId), truncate(userName), details);
    }

    // Route template (e.g. /v1/patients/mrn/{mrn}) rather than the raw URI, so identifiers such as MRNs never reach the audit row.
    private static String describeRequest(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? request.getMethod() + " " + pattern : request.getMethod();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value) {
        return value.length() <= USER_FIELD_MAX_LENGTH ? value : value.substring(0, USER_FIELD_MAX_LENGTH);
    }

    static final class AuditActor {
        final String userId;
        final String userName;
        final String details;

        AuditActor(String userId, String userName, String details) {
            this.userId = userId;
            this.userName = userName;
            this.details = details;
        }
    }

    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception e) {
            log.debug("Could not get current request", e);
            return null;
        }
    }

    private String getClientIpAddress() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP", e);
        }
        return null;
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception e) {
            log.debug("Could not get user agent", e);
        }
        return null;
    }
}
