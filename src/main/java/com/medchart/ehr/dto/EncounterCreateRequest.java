package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Client-writable fields for creating an encounter. Server-owned fields (id, version,
 * encounterNumber, status, createdBy, diagnoses) are rejected rather than bound.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterCreateRequest {

    @NotNull(message = "patientId is required")
    private Long patientId;

    private Long attendingProviderId;

    @NotNull(message = "encounterType is required")
    private EncounterType encounterType;

    @NotNull(message = "encounterDateTime is required")
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

    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Field '" + name + "' cannot be set when creating an encounter");
    }
}
