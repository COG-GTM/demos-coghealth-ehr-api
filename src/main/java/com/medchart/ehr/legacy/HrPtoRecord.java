package com.medchart.ehr.legacy;

public class HrPtoRecord {

    public String employeeId;
    public String lastName;
    public String firstName;
    public String npi;
    public String costCenter;
    public String ptoStart;
    public String ptoEnd;
    public String ptoType;
    public String status;
    public String notes;
    public int lineNumber;

    public boolean hasPto() {
        return ptoStart != null && ptoStart.trim().length() > 0;
    }

    public boolean isApproved() {
        return "Approved".equalsIgnoreCase(status);
    }

    public String displayName() {
        return lastName + ", " + firstName;
    }

    @Override
    public String toString() {
        return employeeId + "|" + lastName + "|" + firstName + "|" + npi + "|" + costCenter + "|" + ptoStart + "|" + ptoEnd
                + "|" + ptoType + "|" + status;
    }
}
