package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterDTO {

    private Long id;
    private String encounterNumber;

    private EncounterPatientDTO patient;
    private EncounterProviderDTO attendingProvider;

    private EncounterType encounterType;
    private EncounterStatus status;
    private EncounterPriority priority;
    private String visitType;

    private LocalDateTime encounterDateTime;
    private LocalDateTime admitDateTime;
    private LocalDateTime dischargeDateTime;

    private String department;
    private String room;
    private String bed;

    private String chiefComplaint;
    private String notes;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<EncounterDiagnosisDTO> diagnoses;

    private Long version;
}
