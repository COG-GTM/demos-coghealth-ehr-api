package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterType;
import lombok.Getter;

import javax.validation.constraints.AssertTrue;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Client-editable fields of an existing encounter. Only fields present in the request body
 * are applied; omitted fields keep their stored value and an explicit {@code null} clears the
 * field. Patient, status, encounterNumber, createdBy, version and diagnoses are not editable.
 */
@Getter
public class EncounterUpdateRequest {

    public static final String ATTENDING_PROVIDER_ID = "attendingProviderId";
    public static final String ENCOUNTER_TYPE = "encounterType";
    public static final String ENCOUNTER_DATE_TIME = "encounterDateTime";
    public static final String ADMIT_DATE_TIME = "admitDateTime";
    public static final String DISCHARGE_DATE_TIME = "dischargeDateTime";
    public static final String DEPARTMENT = "department";
    public static final String ROOM = "room";
    public static final String BED = "bed";
    public static final String CHIEF_COMPLAINT = "chiefComplaint";
    public static final String PRIORITY = "priority";
    public static final String VISIT_TYPE = "visitType";
    public static final String NOTES = "notes";

    @JsonIgnore
    private final Set<String> providedFields = new HashSet<>();

    private Long attendingProviderId;

    private EncounterType encounterType;

    private LocalDateTime encounterDateTime;

    private LocalDateTime admitDateTime;

    private LocalDateTime dischargeDateTime;

    @Size(max = 50)
    private String department;

    @Size(max = 20)
    private String room;

    @Size(max = 20)
    private String bed;

    @Size(max = 500)
    private String chiefComplaint;

    private EncounterPriority priority;

    @Size(max = 20)
    private String visitType;

    @Size(max = 1000)
    private String notes;

    public boolean isProvided(String field) {
        return providedFields.contains(field);
    }

    @JsonIgnore
    @AssertTrue(message = "encounterType cannot be cleared")
    public boolean isEncounterTypeRetained() {
        return !isProvided(ENCOUNTER_TYPE) || encounterType != null;
    }

    @JsonIgnore
    @AssertTrue(message = "encounterDateTime cannot be cleared")
    public boolean isEncounterDateTimeRetained() {
        return !isProvided(ENCOUNTER_DATE_TIME) || encounterDateTime != null;
    }

    public void setAttendingProviderId(Long attendingProviderId) {
        this.attendingProviderId = attendingProviderId;
        providedFields.add(ATTENDING_PROVIDER_ID);
    }

    public void setEncounterType(EncounterType encounterType) {
        this.encounterType = encounterType;
        providedFields.add(ENCOUNTER_TYPE);
    }

    public void setEncounterDateTime(LocalDateTime encounterDateTime) {
        this.encounterDateTime = encounterDateTime;
        providedFields.add(ENCOUNTER_DATE_TIME);
    }

    public void setAdmitDateTime(LocalDateTime admitDateTime) {
        this.admitDateTime = admitDateTime;
        providedFields.add(ADMIT_DATE_TIME);
    }

    public void setDischargeDateTime(LocalDateTime dischargeDateTime) {
        this.dischargeDateTime = dischargeDateTime;
        providedFields.add(DISCHARGE_DATE_TIME);
    }

    public void setDepartment(String department) {
        this.department = department;
        providedFields.add(DEPARTMENT);
    }

    public void setRoom(String room) {
        this.room = room;
        providedFields.add(ROOM);
    }

    public void setBed(String bed) {
        this.bed = bed;
        providedFields.add(BED);
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
        providedFields.add(CHIEF_COMPLAINT);
    }

    public void setPriority(EncounterPriority priority) {
        this.priority = priority;
        providedFields.add(PRIORITY);
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
        providedFields.add(VISIT_TYPE);
    }

    public void setNotes(String notes) {
        this.notes = notes;
        providedFields.add(NOTES);
    }

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Field '" + name + "' cannot be set when updating an encounter");
    }
}
