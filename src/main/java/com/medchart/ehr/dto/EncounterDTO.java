package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * API representation of an encounter. Patient data is limited to identifying
 * summary fields; the SSN and other demographics are never exposed here.
 * Server-managed fields are read-only and ignored on input. Optional fields
 * sent as an explicit JSON null are recorded so updates can clear them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterDTO {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String encounterNumber;

    @Valid
    private PatientSummary patient;

    @Valid
    private ProviderSummary attendingProvider;

    private EncounterType encounterType;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private EncounterStatus status;

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

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime updatedAt;

    private Long version;

    @JsonIgnore
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private final Set<String> explicitlySet = new HashSet<>();

    public boolean isExplicitlySet(String field) {
        return explicitlySet.contains(field);
    }

    public void setAttendingProvider(ProviderSummary attendingProvider) {
        this.attendingProvider = attendingProvider;
        explicitlySet.add("attendingProvider");
    }

    public void setAdmitDateTime(LocalDateTime admitDateTime) {
        this.admitDateTime = admitDateTime;
        explicitlySet.add("admitDateTime");
    }

    public void setDischargeDateTime(LocalDateTime dischargeDateTime) {
        this.dischargeDateTime = dischargeDateTime;
        explicitlySet.add("dischargeDateTime");
    }

    public void setDepartment(String department) {
        this.department = department;
        explicitlySet.add("department");
    }

    public void setRoom(String room) {
        this.room = room;
        explicitlySet.add("room");
    }

    public void setBed(String bed) {
        this.bed = bed;
        explicitlySet.add("bed");
    }

    public void setChiefComplaint(String chiefComplaint) {
        this.chiefComplaint = chiefComplaint;
        explicitlySet.add("chiefComplaint");
    }

    public void setPriority(EncounterPriority priority) {
        this.priority = priority;
        explicitlySet.add("priority");
    }

    public void setVisitType(String visitType) {
        this.visitType = visitType;
        explicitlySet.add("visitType");
    }

    public void setNotes(String notes) {
        this.notes = notes;
        explicitlySet.add("notes");
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PatientSummary {
        private Long id;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String mrn;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String firstName;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String lastName;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private LocalDate dateOfBirth;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProviderSummary {
        private Long id;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String firstName;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String lastName;

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private String credentials;
    }
}
