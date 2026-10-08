package com.medchart.ehr.interop.fhir;

public class ExportedResource {

    private final String resourceType;
    private final String id;
    private final String sourceEmployeeId;
    private final String json;

    public ExportedResource(String resourceType, String id, String sourceEmployeeId, String json) {
        this.resourceType = resourceType;
        this.id = id;
        this.sourceEmployeeId = sourceEmployeeId;
        this.json = json;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getId() {
        return id;
    }

    public String getSourceEmployeeId() {
        return sourceEmployeeId;
    }

    public String getJson() {
        return json;
    }

    public String getReference() {
        return resourceType + "/" + id;
    }
}
