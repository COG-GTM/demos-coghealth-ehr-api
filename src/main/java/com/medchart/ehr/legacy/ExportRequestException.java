package com.medchart.ehr.legacy;

/**
 * Raised when an export request is outside the bounds allowed for bulk PHI extraction.
 */
public class ExportRequestException extends RuntimeException {

    public ExportRequestException(String message) {
        super(message);
    }
}
