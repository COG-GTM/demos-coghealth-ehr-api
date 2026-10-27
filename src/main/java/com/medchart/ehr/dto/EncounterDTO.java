package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import lombok.*;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * API representation of an encounter. Patient data is limited to identifying
 * summary fields; the SSN and other demographics are never exposed here.
 * Server-managed fields are read-only and ignored on input.
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
