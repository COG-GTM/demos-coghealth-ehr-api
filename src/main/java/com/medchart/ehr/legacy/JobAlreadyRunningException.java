package com.medchart.ehr.legacy;

public class JobAlreadyRunningException extends RuntimeException {

    public JobAlreadyRunningException(String message) {
        super(message);
    }
}
