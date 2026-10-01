package com.medchart.ehr.export;

import org.springframework.http.HttpStatus;

public class ExportException extends RuntimeException {

    private final HttpStatus status;

    /** Client error (400 Bad Request). */
    public ExportException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public ExportException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /** Server-side failure (500 Internal Server Error). */
    public ExportException(String message, Throwable cause) {
        super(message, cause);
        this.status = HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
