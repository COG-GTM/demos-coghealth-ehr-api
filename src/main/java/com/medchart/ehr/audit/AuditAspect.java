package com.medchart.ehr.audit;

import com.medchart.ehr.dto.PatientDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    static final int PATIENT_MRN_MAX_LENGTH = 20;

    private final AuditService auditService;

    @Around("@annotation(com.medchart.ehr.audit.AuditAccess)")
    public Object auditAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (joinPoint.getTarget() != null) {
            method = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
        }
        AuditAccess auditAccess = method.getAnnotation(AuditAccess.class);
        if (auditAccess == null) {
            auditAccess = signature.getMethod().getAnnotation(AuditAccess.class);
        }

        Object[] args = joinPoint.getArgs();
        Long patientId = findAnnotatedArg(method, args, AuditPatientId.class, Long.class);
        String patientMrn = truncateMrn(findAnnotatedArg(method, args, AuditPatientMrn.class, String.class));
        String userId = getCurrentUserId();

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .userId(userId)
                .userName(getCurrentUserName())
                .patientId(patientId)
                .patientMrn(patientMrn)
                .action(auditAccess.action())
                .resourceType(auditAccess.resourceType())
                .description(auditAccess.description())
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent());

        try {
            Object result = joinPoint.proceed();
            if (auditAccess.patientFromResult() && result instanceof PatientDTO) {
                PatientDTO patient = (PatientDTO) result;
                if (patientId == null) {
                    eventBuilder.patientId(patient.getId());
                }
                if (patientMrn == null) {
                    eventBuilder.patientMrn(truncateMrn(patient.getMrn()));
                }
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

    private static <T> T findAnnotatedArg(Method method, Object[] args,
                                          Class<? extends Annotation> marker, Class<T> type) {
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        for (int i = 0; i < parameterAnnotations.length && i < args.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (marker.isInstance(annotation) && type.isInstance(args[i])) {
                    return type.cast(args[i]);
                }
            }
        }
        return null;
    }

    private static String truncateMrn(String mrn) {
        if (mrn == null) {
            return null;
        }
        String trimmed = mrn.trim();
        return trimmed.length() > PATIENT_MRN_MAX_LENGTH
                ? trimmed.substring(0, PATIENT_MRN_MAX_LENGTH)
                : trimmed;
    }

    private String getCurrentUserId() {
        return "system";
    }

    private String getCurrentUserName() {
        return "System User";
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
