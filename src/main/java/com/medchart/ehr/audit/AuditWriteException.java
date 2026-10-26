package com.medchart.ehr.audit;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when an audit event cannot be persisted. Audited operations fail closed:
 * the caller gets an error instead of an unaudited response.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class AuditWriteException extends RuntimeException {

    public AuditWriteException(String message) {
        super(message);
    }
}
