package com.medchart.ehr.export;

public class ExportAccessDeniedException extends RuntimeException {

    public ExportAccessDeniedException(String message) {
        super(message);
    }
}
