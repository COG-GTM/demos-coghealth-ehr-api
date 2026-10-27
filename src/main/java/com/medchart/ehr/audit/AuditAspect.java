package com.medchart.ehr.audit;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * Runs outside the audited method's own {@code @Transactional} advice so the audit write is never
 * forced into a read-only service transaction.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class AuditAspect {

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

        if (isAsync(joinPoint, method)) {
            return proceedAfterAuditingDispatch(joinPoint, eventBuilder);
        }

        try {
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                return proceedWithinCallerTransaction(joinPoint, eventBuilder);
            }
            return proceedWithinAuditTransaction(joinPoint, eventBuilder);
        } catch (InvocationFailure failure) {
            throw recordFailure(eventBuilder, failure.getCause());
        }
    }

    /**
     * {@code @Async} methods: proceeding only hands the work to another thread, so the audit record is
     * committed first and nothing is dispatched if it cannot be.
     */
    private Object proceedAfterAuditingDispatch(ProceedingJoinPoint joinPoint,
                                                AuditEvent.AuditEventBuilder eventBuilder) throws Throwable {
        eventBuilder.success(true);
        recordInNewTransaction(eventBuilder.build());
        try {
            return joinPoint.proceed();
        } catch (Throwable dispatchFailure) {
            throw recordFailure(eventBuilder, dispatchFailure);
        }
    }

    private static boolean isAsync(ProceedingJoinPoint joinPoint, Method method) {
        Class<?> targetClass = joinPoint.getTarget() != null
                ? AopUtils.getTargetClass(joinPoint.getTarget()) : method.getDeclaringClass();
        Method specificMethod = AopUtils.getMostSpecificMethod(method, targetClass);
        return AnnotatedElementUtils.hasAnnotation(specificMethod, Async.class)
                || AnnotatedElementUtils.hasAnnotation(targetClass, Async.class);
    }

    /**
     * No caller transaction: the protected call and its audit record share one writable transaction,
     * so the result is only returned (and any writes only committed) once the audit event is committed.
     */
    private Object proceedWithinAuditTransaction(ProceedingJoinPoint joinPoint,
                                                 AuditEvent.AuditEventBuilder eventBuilder) throws Throwable {
        try {
            return transactionTemplate.execute(status -> {
                Object result = proceed(joinPoint);
                flushInvocationWrites(status);
                eventBuilder.success(true);
                auditService.record(eventBuilder.build());
                return result;
            });
        } catch (TransactionException | DataAccessException e) {
            AuditPersistenceException auditFailure =
                    new AuditPersistenceException("Audited transaction could not be committed", e);
            throw recordFailure(eventBuilder, auditFailure);
        }
    }

    /**
     * Inside a caller transaction (which may be read-only or later roll back): the audit record is
     * committed independently before the result is returned, and an audit failure propagates so the
     * caller's transaction rolls back. If the caller later rolls back, a follow-up failure event is
     * recorded so the trail shows the outcome.
     */
    private Object proceedWithinCallerTransaction(ProceedingJoinPoint joinPoint,
                                                  AuditEvent.AuditEventBuilder eventBuilder) {
        Object result = proceed(joinPoint);
        eventBuilder.success(true);
        recordInNewTransaction(eventBuilder.build());
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        recordCallerRollback(eventBuilder);
                    }
                }
            });
        }
        return result;
    }

    private void recordCallerRollback(AuditEvent.AuditEventBuilder eventBuilder) {
        eventBuilder.success(false);
        eventBuilder.errorMessage("Caller transaction rolled back after audited call");
        try {
            recordInNewTransaction(eventBuilder.build());
        } catch (RuntimeException e) {
            log.error("Failed to record rollback of audited call (action={}, resourceType={})",
                    eventBuilder.build().getAction(), eventBuilder.build().getResourceType(), e);
        }
    }

    /**
     * Records a failed call and returns the exception to throw: the original failure, or the audit
     * failure (with the original suppressed) when the failed call could not be audited either.
     */
    private Throwable recordFailure(AuditEvent.AuditEventBuilder eventBuilder, Throwable cause) {
        eventBuilder.success(false);
        eventBuilder.errorMessage(AuditService.truncate(cause.getMessage(), AuditService.ERROR_MESSAGE_MAX_LENGTH));
        try {
            recordInNewTransaction(eventBuilder.build());
            return cause;
        } catch (AuditPersistenceException auditFailure) {
            if (auditFailure != cause) {
                auditFailure.addSuppressed(cause);
            }
            return auditFailure;
        }
    }

    /**
     * Flushes the audited call's pending writes before the audit insert, so a failing domain write is
     * reported as the call's own failure rather than as an audit outage.
     */
    private static void flushInvocationWrites(TransactionStatus status) {
        try {
            status.flush();
        } catch (RuntimeException e) {
            throw new InvocationFailure(e);
        }
    }

    private void recordInNewTransaction(AuditEvent event) {
        try {
            auditService.recordInNewTransaction(event);
        } catch (TransactionException | DataAccessException e) {
            throw new AuditPersistenceException("Audit transaction could not be committed", e);
        }
    }

    private static Object proceed(ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            throw new InvocationFailure(t);
        }
    }

    private static final class InvocationFailure extends RuntimeException {
        InvocationFailure(Throwable cause) {
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
                    return AuditService.truncate(xForwardedFor.split(",")[0].trim(),
                            AuditService.IP_ADDRESS_MAX_LENGTH);
                }
                return AuditService.truncate(request.getRemoteAddr(), AuditService.IP_ADDRESS_MAX_LENGTH);
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
                return AuditService.truncate(attrs.getRequest().getHeader("User-Agent"),
                        AuditService.USER_AGENT_MAX_LENGTH);
            }
        } catch (Exception e) {
            log.debug("Could not get user agent", e);
        }
        return null;
    }
}
