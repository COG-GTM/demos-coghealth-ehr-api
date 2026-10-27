package com.medchart.ehr.audit;

import com.medchart.ehr.domain.auth.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    static final String ANONYMOUS_USER_ID = "anonymous";
    static final String ANONYMOUS_USER_NAME = "Anonymous (unauthenticated request)";
    static final String SYSTEM_USER_ID = "system";
    static final String SYSTEM_USER_NAME = "System (no request context)";

    private static final int USER_ID_MAX = 100;
    private static final int USER_NAME_MAX = 100;
    private static final int USER_AGENT_MAX = 200;
    private static final int PATIENT_MRN_MAX = 20;
    private static final int ERROR_MESSAGE_MAX = 500;

    private final AuditService auditService;

    @Around("@annotation(com.medchart.ehr.audit.AuditAccess)")
    public Object auditAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = resolveMethod(joinPoint);
        AuditAccess auditAccess = method.getAnnotation(AuditAccess.class);
        Object[] args = joinPoint.getArgs();
        HttpServletRequest request = currentRequest();
        Actor actor = resolveActor(SecurityContextHolder.getContext().getAuthentication(), request != null);

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .userId(truncate(actor.userId, USER_ID_MAX))
                .userName(truncate(actor.userName, USER_NAME_MAX))
                .requestDetails(actor.details)
                .patientId(annotatedArgument(method, args, PatientId.class, Long.class))
                .patientMrn(truncate(annotatedArgument(method, args, PatientMrn.class, String.class), PATIENT_MRN_MAX))
                .action(auditAccess.action())
                .resourceType(auditAccess.resourceType())
                .description(auditAccess.description())
                .ipAddress(request != null ? request.getRemoteAddr() : null)
                .userAgent(request != null ? truncate(request.getHeader("User-Agent"), USER_AGENT_MAX) : null);

        try {
            Object result = joinPoint.proceed();
            eventBuilder.success(true);
            auditService.saveAuditEventAsync(eventBuilder.build());
            return result;
        } catch (Exception e) {
            eventBuilder.success(false);
            eventBuilder.errorMessage(truncate(e.getMessage(), ERROR_MESSAGE_MAX));
            auditService.saveAuditEventAsync(eventBuilder.build());
            throw e;
        }
    }

    private static Method resolveMethod(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return method;
        }
        Method specific = AopUtils.getMostSpecificMethod(method, AopUtils.getTargetClass(target));
        return specific.isAnnotationPresent(AuditAccess.class) ? specific : method;
    }

    static <T> T annotatedArgument(Method method, Object[] args, Class<? extends Annotation> marker, Class<T> type) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length && i < args.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (marker.isInstance(annotation)) {
                    return type.isInstance(args[i]) ? type.cast(args[i]) : null;
                }
            }
        }
        return null;
    }

    static Actor resolveActor(Authentication authentication, boolean inRequest) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            String userId = authentication.getName();
            String userName = userId;
            Object principal = authentication.getPrincipal();
            if (principal instanceof User) {
                User user = (User) principal;
                userId = user.getUsername();
                String fullName = (nullToEmpty(user.getFirstName()) + " " + nullToEmpty(user.getLastName())).trim();
                userName = fullName.isEmpty() ? userId : fullName;
            }
            String authorities = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .sorted()
                    .collect(Collectors.joining(","));
            return new Actor(userId, userName, "authorities=" + authorities);
        }
        if (inRequest) {
            return new Actor(ANONYMOUS_USER_ID, ANONYMOUS_USER_NAME, "authorities=");
        }
        return new Actor(SYSTEM_USER_ID, SYSTEM_USER_NAME, null);
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (!StringUtils.hasLength(value) || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    static final class Actor {
        final String userId;
        final String userName;
        final String details;

        Actor(String userId, String userName, String details) {
            this.userId = userId;
            this.userName = userName;
            this.details = details;
        }
    }
}
