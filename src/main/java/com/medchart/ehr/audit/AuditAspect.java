package com.medchart.ehr.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * Runs outside the audited method's own {@code @Transactional} boundary and wraps it in a
 * transaction that also holds the success audit row, so the operation and its audit row
 * commit or roll back together on one connection. Failures (including commit-time failures)
 * are audited afterwards in a separate transaction. If the caller already has a transaction,
 * the success row is committed independently, because the caller receives the data even if
 * its own transaction later rolls back.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AuditAspect {

    private static final int USER_AGENT_MAX_LENGTH = 200;

    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public AuditAspect(AuditService auditService, PlatformTransactionManager transactionManager) {
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Around("@annotation(com.medchart.ehr.audit.AuditAccess)")
    public Object auditAccess(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditAccess auditAccess = method.getAnnotation(AuditAccess.class);

        Long patientId = extractPatientId(joinPoint.getArgs());
        String userId = getCurrentUserId();

        AuditEvent.AuditEventBuilder eventBuilder = AuditEvent.builder()
                .userId(userId)
                .userName(getCurrentUserName())
                .patientId(patientId)
                .action(auditAccess.action())
                .resourceType(auditAccess.resourceType())
                .description(auditAccess.description())
                .ipAddress(getClientIpAddress())
                .userAgent(getUserAgent());

        boolean enclosingTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        try {
            return transactionTemplate.execute(status -> {
                Object result = proceed(joinPoint);
                eventBuilder.success(true);
                if (enclosingTransaction) {
                    auditService.recordAuditEventInNewTransaction(eventBuilder.build());
                } else {
                    auditService.recordAuditEvent(eventBuilder.build());
                }
                return result;
            });
        } catch (AuditWriteException auditFailure) {
            throw auditFailure;
        } catch (Throwable thrown) {
            Throwable e = thrown instanceof CheckedProceedFailure ? thrown.getCause() : thrown;
            eventBuilder.success(false);
            eventBuilder.errorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
            try {
                auditService.recordAuditEventInNewTransaction(eventBuilder.build());
            } catch (AuditWriteException auditFailure) {
                auditFailure.addSuppressed(e);
                throw auditFailure;
            }
            throw e;
        }
    }

    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new CheckedProceedFailure(e);
        }
    }

    private static final class CheckedProceedFailure extends RuntimeException {
        CheckedProceedFailure(Throwable cause) {
            super(cause);
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
                    String forwarded = AuditFields.ipLiteral(xForwardedFor.split(",", 2)[0]);
                    if (forwarded != null) {
                        return forwarded;
                    }
                }
                String remote = AuditFields.ipLiteral(request.getRemoteAddr());
                return remote != null ? remote : AuditFields.INVALID_IP;
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
                return AuditFields.bounded(attrs.getRequest().getHeader("User-Agent"), USER_AGENT_MAX_LENGTH);
            }
        } catch (Exception e) {
            log.debug("Could not get user agent", e);
        }
        return null;
    }
}
