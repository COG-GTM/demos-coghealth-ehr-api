package com.medchart.ehr.dto;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterResponseDTO {
    private Long id;
    private String encounterNumber;
    private PatientSummary patient;
    private ProviderSummary attendingProvider;
    private EncounterType encounterType;
    private EncounterStatus status;
    private LocalDateTime encounterDateTime;
    private LocalDateTime admitDateTime;
    private LocalDateTime dischargeDateTime;
    private String department;
    private String room;
    private String bed;
    private String chiefComplaint;
    private EncounterPriority priority;
    private String visitType;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PatientSummary {
        private Long id;
        private String mrn;
        private String firstName;
        private String lastName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProviderSummary {
        private Long id;
        private String firstName;
        private String lastName;
        private String credentials;
        private String specialty;
    }

    public static EncounterResponseDTO from(Encounter encounter) {
        return EncounterResponseDTO.builder()
                .id(encounter.getId())
                .encounterNumber(encounter.getEncounterNumber())
                .patient(toPatientSummary(encounter.getPatient()))
                .attendingProvider(toProviderSummary(encounter.getAttendingProvider()))
                .encounterType(encounter.getEncounterType())
                .status(encounter.getStatus())
                .encounterDateTime(encounter.getEncounterDateTime())
                .admitDateTime(encounter.getAdmitDateTime())
                .dischargeDateTime(encounter.getDischargeDateTime())
                .department(encounter.getDepartment())
                .room(encounter.getRoom())
                .bed(encounter.getBed())
                .chiefComplaint(encounter.getChiefComplaint())
                .priority(encounter.getPriority())
                .visitType(encounter.getVisitType())
                .notes(encounter.getNotes())
                .createdAt(encounter.getCreatedAt())
                .updatedAt(encounter.getUpdatedAt())
                .build();
    }

    private static PatientSummary toPatientSummary(Patient patient) {
        if (patient == null) {
            return null;
        }
        return PatientSummary.builder()
                .id(patient.getId())
                .mrn(patient.getMrn())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .build();
    }

    private static ProviderSummary toProviderSummary(Provider provider) {
        if (provider == null) {
            return null;
        }
        return ProviderSummary.builder()
                .id(provider.getId())
                .firstName(provider.getFirstName())
                .lastName(provider.getLastName())
                .credentials(provider.getCredentials())
                .specialty(provider.getSpecialty())
                .build();
    }
}
