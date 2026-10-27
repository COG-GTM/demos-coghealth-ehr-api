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
import org.springframework.security.core.userdetails.UserDetails;
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

    static final String ANONYMOUS_USER = "anonymous";
    static final String SYSTEM_USER = "system";

    private static boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private String getCurrentUserId(Authentication authentication) {
        if (isAuthenticatedUser(authentication)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof UserDetails) {
                return ((UserDetails) principal).getUsername();
            }
            return authentication.getName();
        }
        return RequestContextHolder.getRequestAttributes() != null ? ANONYMOUS_USER : SYSTEM_USER;
    }

    private String getCurrentUserName(Authentication authentication) {
        if (isAuthenticatedUser(authentication)) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof User) {
                User user = (User) principal;
                return user.getFirstName() + " " + user.getLastName();
            }
            return authentication.getName();
        }
        return RequestContextHolder.getRequestAttributes() != null ? "Anonymous" : "System User";
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
