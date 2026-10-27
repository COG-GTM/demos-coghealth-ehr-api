package com.medchart.ehr.audit;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

import java.util.ArrayList;
import java.util.List;

/** Transaction manager that records outcomes instead of touching a database. */
public class RecordingTransactionManager extends AbstractPlatformTransactionManager {

    public final List<String> outcomes = new ArrayList<>();
    public final List<Boolean> readOnlyBegins = new ArrayList<>();
    public boolean failNextCommit;
    public RuntimeException failNextCommitWith;
    public RuntimeException failNextFlush;
    private boolean active;

    @Override
    protected Object doGetTransaction() {
        return new SmartTransactionObject() {
            @Override
            public boolean isRollbackOnly() {
                return false;
            }

            @Override
            public void flush() {
                if (failNextFlush != null) {
                    RuntimeException failure = failNextFlush;
                    failNextFlush = null;
                    outcomes.add("flush-failed");
                    throw failure;
                }
            }
        };
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return active;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        readOnlyBegins.add(definition.isReadOnly());
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
        if (failNextCommitWith != null) {
            RuntimeException failure = failNextCommitWith;
            failNextCommitWith = null;
            outcomes.add("commit-failed");
            throw failure;
        }
        if (failNextCommit) {
            failNextCommit = false;
            outcomes.add("commit-failed");
            throw new TransactionSystemException("connection lost during commit");
        }
        outcomes.add("commit");
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        outcomes.add("rollback");
    }
}
