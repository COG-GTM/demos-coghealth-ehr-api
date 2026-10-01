package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import com.medchart.ehr.domain.auth.User;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    static final String SYSTEM_USER_ID = "system";
    static final String SYSTEM_USER_NAME = "System User";
    static final String ANONYMOUS_USER_ID = "anonymous";
    static final String ANONYMOUS_USER_NAME = "Anonymous";

    private final AuditService auditService;

    @Around("@annotation(com.medchart.ehr.audit.AuditAccess)")
    public Object auditAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAccess auditAccess = method.getAnnotation(AuditAccess.class);

        Long patientId = extractPatientId(joinPoint.getArgs());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .userId(getCurrentUserId(authentication))
                .userName(getCurrentUserName(authentication))
                .patientId(patientId)
                .action(auditAccess.action())
                .resourceType(auditAccess.resourceType())
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

    private Long extractPatientId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof Long) {
                return (Long) arg;
            }
        }
        return null;
    }

    private String getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return SYSTEM_USER_ID;
        }
        if (!authentication.isAuthenticated() || isAnonymous(authentication)) {
            return ANONYMOUS_USER_ID;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        String name = authentication.getName();
        return name != null && !name.isEmpty() ? name : ANONYMOUS_USER_ID;
    }

    private String getCurrentUserName(Authentication authentication) {
        if (authentication == null) {
            return SYSTEM_USER_NAME;
        }
        if (!authentication.isAuthenticated() || isAnonymous(authentication)) {
            return ANONYMOUS_USER_NAME;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            User user = (User) principal;
            String fullName = Stream.of(user.getFirstName(), user.getLastName())
                    .filter(part -> part != null && !part.trim().isEmpty())
                    .map(String::trim)
                    .collect(Collectors.joining(" "));
            if (!fullName.isEmpty()) {
                return fullName;
            }
        }
        return getCurrentUserId(authentication);
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication instanceof AnonymousAuthenticationToken;
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
