package com.medchart.ehr.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.medchart.ehr.audit.PatientScoped;
import com.medchart.ehr.domain.clinical.Diagnosis;
import com.medchart.ehr.domain.encounter.DiagnosisRole;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API view of an encounter. Carries only a minimal patient/provider reference so that
 * demographics, SSN and contact details never leave the service through encounter routes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EncounterResponseDTO implements PatientScoped {

    private Long id;
    private String encounterNumber;
    private PatientRef patient;
    private ProviderRef attendingProvider;
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
    private List<DiagnosisRef> diagnoses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    @Override
    @JsonIgnore
    public Long getPatientId() {
        return patient != null ? patient.getId() : null;
    }

    public static EncounterResponseDTO from(Encounter encounter) {
        return EncounterResponseDTO.builder()
                .id(encounter.getId())
                .encounterNumber(encounter.getEncounterNumber())
                .patient(PatientRef.from(encounter.getPatient()))
                .attendingProvider(ProviderRef.from(encounter.getAttendingProvider()))
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
                .diagnoses(encounter.getDiagnoses() == null ? Collections.emptyList()
                        : encounter.getDiagnoses().stream().map(DiagnosisRef::from).collect(Collectors.toList()))
                .createdAt(encounter.getCreatedAt())
                .updatedAt(encounter.getUpdatedAt())
                .version(encounter.getVersion())
                .build();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PatientRef {
        private Long id;
        private String mrn;
        private String firstName;
        private String lastName;
        private String displayName;

        static PatientRef from(Patient patient) {
            if (patient == null) {
                return null;
            }
            return new PatientRef(patient.getId(), patient.getMrn(), patient.getFirstName(),
                    patient.getLastName(), displayName(patient.getFirstName(), patient.getLastName()));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProviderRef {
        private Long id;
        private String firstName;
        private String lastName;
        private String credentials;
        private String displayName;

        static ProviderRef from(Provider provider) {
            if (provider == null) {
                return null;
            }
            return new ProviderRef(provider.getId(), provider.getFirstName(), provider.getLastName(),
                    provider.getCredentials(), displayName(provider.getFirstName(), provider.getLastName()));
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DiagnosisRef {
        private Long id;
        private String icd10Code;
        private String description;
        private DiagnosisRole role;
        private Integer rank;
        private Boolean primaryDiagnosis;

        static DiagnosisRef from(EncounterDiagnosis link) {
            Diagnosis diagnosis = link.getDiagnosis();
            return new DiagnosisRef(link.getId(),
                    diagnosis != null ? diagnosis.getIcd10Code() : null,
                    diagnosis != null ? diagnosis.getDescription() : null,
                    link.getRole(), link.getRank(), link.getPrimaryDiagnosis());
        }
    }

    private static String displayName(String firstName, String lastName) {
        StringBuilder name = new StringBuilder();
        if (firstName != null) {
            name.append(firstName);
        }
        if (lastName != null) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(lastName);
        }
        return name.toString();
    }
}
