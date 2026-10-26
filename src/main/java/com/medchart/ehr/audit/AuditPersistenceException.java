package com.medchart.ehr.audit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when an audit event cannot be persisted. Audited operations fail closed:
 * the caller gets an error instead of the protected data.
 */
@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE, reason = "Audit trail unavailable")
public class AuditPersistenceException extends RuntimeException {

    public AuditPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
