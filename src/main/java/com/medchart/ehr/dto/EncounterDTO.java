package com.medchart.ehr.dto;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterPriority;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Encounter response body. The patient is only referenced (id, MRN, name); SSN, date of birth,
 * address and contact details stay behind the patient endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncounterDTO {

    private Long id;
    private String encounterNumber;
    private PatientRef patient;
    private ProviderRef attendingProvider;
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
    private Long version;

    public static EncounterDTO from(Encounter encounter) {
        if (encounter == null) {
            return null;
        }
        return EncounterDTO.builder()
                .id(encounter.getId())
                .encounterNumber(encounter.getEncounterNumber())
                .patient(PatientRef.from(encounter.getPatient()))
                .attendingProvider(ProviderRef.from(encounter.getAttendingProvider()))
                .encounterType(encounter.getEncounterType())
                .status(encounter.getStatus())
                .priority(encounter.getPriority())
                .visitType(encounter.getVisitType())
                .encounterDateTime(encounter.getEncounterDateTime())
                .admitDateTime(encounter.getAdmitDateTime())
                .dischargeDateTime(encounter.getDischargeDateTime())
                .department(encounter.getDepartment())
                .room(encounter.getRoom())
                .bed(encounter.getBed())
                .chiefComplaint(encounter.getChiefComplaint())
                .notes(encounter.getNotes())
                .createdAt(encounter.getCreatedAt())
                .updatedAt(encounter.getUpdatedAt())
                .version(encounter.getVersion())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PatientRef {
        private Long id;
        private String mrn;
        private String firstName;
        private String lastName;

        static PatientRef from(Patient patient) {
            if (patient == null) {
                return null;
            }
            return new PatientRef(patient.getId(), patient.getMrn(), patient.getFirstName(), patient.getLastName());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderRef {
        private Long id;
        private String npi;
        private String firstName;
        private String lastName;
        private String credentials;
        private String specialty;
        private String department;

        static ProviderRef from(Provider provider) {
            if (provider == null) {
                return null;
            }
            return new ProviderRef(provider.getId(), provider.getNpi(), provider.getFirstName(),
                    provider.getLastName(), provider.getCredentials(), provider.getSpecialty(),
                    provider.getDepartment());
        }
    }
}
