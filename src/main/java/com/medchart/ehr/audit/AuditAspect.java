package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(com.medchart.ehr.audit.AuditAccess)")
    public Object auditAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAccess auditAccess = method.getAnnotation(AuditAccess.class);
        String[] parameterNames = signature.getParameterNames();
        Object[] arguments = joinPoint.getArgs();
        String resourceType = auditAccess.resourceType();

        Long patientId = extractPatientId(parameterNames, arguments, resourceType);
        String userId = getCurrentUserId();

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .userId(userId)
                .userName(getCurrentUserName())
                .patientId(patientId)
                .resourceId(extractResourceId(parameterNames, arguments, resourceType, patientId))
                .action(auditAccess.action())
                .resourceType(resourceType)
                .description(auditAccess.description())
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent());

        try {
            Object result = joinPoint.proceed();
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

    private Long extractPatientId(String[] parameterNames, Object[] arguments, String resourceType) {
        Long patientId = findLongArgument(parameterNames, arguments, "patientId");
        if (patientId != null) {
            return patientId;
        }
        return "Patient".equals(resourceType) ? findFirstLongArgument(arguments) : null;
    }

    private Long extractResourceId(
            String[] parameterNames,
            Object[] arguments,
            String resourceType,
            Long patientId) {
        if ("Patient".equals(resourceType)) {
            return patientId;
        }
        if (parameterNames == null || arguments == null) {
            return null;
        }
        int count = Math.min(parameterNames.length, arguments.length);
        for (int i = 0; i < count; i++) {
            if (arguments[i] instanceof Long
                    && ("id".equals(parameterNames[i]) || "encounterId".equals(parameterNames[i]))) {
                return (Long) arguments[i];
            }
        }
        return null;
    }

    private Long findLongArgument(String[] parameterNames, Object[] arguments, String parameterName) {
        if (parameterNames == null || arguments == null) {
            return null;
        }
        int count = Math.min(parameterNames.length, arguments.length);
        for (int i = 0; i < count; i++) {
            if (parameterName.equals(parameterNames[i]) && arguments[i] instanceof Long) {
                return (Long) arguments[i];
            }
        }
        return null;
    }

    private Long findFirstLongArgument(Object[] arguments) {
        if (arguments == null) {
            return null;
        }
        for (Object argument : arguments) {
            if (argument instanceof Long) {
                return (Long) argument;
            }
        }
        return null;
    }

    private String getCurrentUserId() {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null) {
            return "system";
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof User
                ? String.valueOf(((User) principal).getId())
                : authentication.getName();
    }

    private String getCurrentUserName() {
        Authentication authentication = getCurrentAuthentication();
        if (authentication == null) {
            return "System User";
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof User
                ? ((User) principal).getUsername()
                : authentication.getName();
    }

    private Authentication getCurrentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication;
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
