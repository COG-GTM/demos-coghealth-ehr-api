package com.medchart.ehr.audit;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;

/** Transaction manager that records outcomes instead of touching a database. */
public class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    public final List<String> outcomes = new ArrayList<>();
    private boolean active;

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return active;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        active = true;
    }

    @Override
    protected Object doSuspend(Object transaction) {
        active = false;
        return Boolean.TRUE;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        active = true;
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        active = false;
    }

    /** Wraps the service in the same @Transactional proxy Spring would apply. */
    public AuditService transactional(AuditService service) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(this, new AnnotationTransactionAttributeSource()));
        return (AuditService) factory.getProxy();
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        outcomes.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        outcomes.add("rollback");
    }
}
