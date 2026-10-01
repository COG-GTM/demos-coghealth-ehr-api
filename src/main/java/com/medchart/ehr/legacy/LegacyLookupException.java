package com.medchart.ehr.legacy;

public class LegacyLookupException extends RuntimeException {

    private final String lookupType;

    public LegacyLookupException(String lookupType, Throwable cause) {
        super("Legacy patient lookup failed for lookup type: " + lookupType, cause);
        this.lookupType = lookupType;
    }

    public String getLookupType() {
        return lookupType;
    }
}
