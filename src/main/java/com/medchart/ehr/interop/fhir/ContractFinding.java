package com.medchart.ehr.interop.fhir;

public class ContractFinding {

    private final String resource;
    private final String severity;
    private final String location;
    private final String message;

    public ContractFinding(String resource, String severity, String location, String message) {
        this.resource = resource;
        this.severity = severity;
        this.location = location;
        this.message = message;
    }

    public String getResource() {
        return resource;
    }

    public String getSeverity() {
        return severity;
    }

    public String getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }

    public boolean isError() {
        return "ERROR".equals(severity) || "FATAL".equals(severity);
    }

    @Override
    public String toString() {
        return severity + " " + resource + " " + location + ": " + message;
    }
}
