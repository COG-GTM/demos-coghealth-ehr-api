package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.clinical.Diagnosis;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.dto.EncounterDiagnosisDTO;
import com.medchart.ehr.dto.EncounterPatientSummaryDTO;
import com.medchart.ehr.dto.EncounterProviderSummaryDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Allowlist mapping for encounter responses so JPA entities (and Patient PHI such as SSN)
 * are never handed to Jackson.
 */
@Component
public class EncounterMapper {

    public EncounterDTO toDetailDto(Encounter encounter) {
        EncounterDTO dto = toDto(encounter);
        if (dto != null && encounter.getDiagnoses() != null) {
            dto.setDiagnoses(encounter.getDiagnoses().stream()
                    .map(this::toDiagnosisDto)
                    .collect(Collectors.toList()));
        }
        return dto;
    }

    public EncounterDTO toDto(Encounter encounter) {
        if (encounter == null) {
            return null;
        }
        return EncounterDTO.builder()
                .id(encounter.getId())
                .encounterNumber(encounter.getEncounterNumber())
                .patient(toPatientSummary(encounter.getPatient()))
                .attendingProvider(toProviderSummary(encounter.getAttendingProvider()))
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

    public List<EncounterDTO> toDtoList(List<Encounter> encounters) {
        return encounters.stream().map(this::toDto).collect(Collectors.toList());
    }

    private EncounterPatientSummaryDTO toPatientSummary(Patient patient) {
        if (patient == null) {
            return null;
        }
        return EncounterPatientSummaryDTO.builder()
                .id(patient.getId())
                .mrn(patient.getMrn())
                .firstName(patient.getFirstName())
                .middleName(patient.getMiddleName())
                .lastName(patient.getLastName())
                .build();
    }

    private EncounterProviderSummaryDTO toProviderSummary(Provider provider) {
        if (provider == null) {
            return null;
        }
        return EncounterProviderSummaryDTO.builder()
                .id(provider.getId())
                .npi(provider.getNpi())
                .firstName(provider.getFirstName())
                .lastName(provider.getLastName())
                .credentials(provider.getCredentials())
                .specialty(provider.getSpecialty())
                .department(provider.getDepartment())
                .build();
    }

    private EncounterDiagnosisDTO toDiagnosisDto(EncounterDiagnosis encounterDiagnosis) {
        Diagnosis diagnosis = encounterDiagnosis.getDiagnosis();
        return EncounterDiagnosisDTO.builder()
                .id(encounterDiagnosis.getId())
                .diagnosisId(diagnosis != null ? diagnosis.getId() : null)
                .icd10Code(diagnosis != null ? diagnosis.getIcd10Code() : null)
                .description(diagnosis != null ? diagnosis.getDescription() : null)
                .role(encounterDiagnosis.getRole())
                .rank(encounterDiagnosis.getRank())
                .primaryDiagnosis(encounterDiagnosis.getPrimaryDiagnosis())
                .build();
    }
}
