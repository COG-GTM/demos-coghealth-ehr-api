package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.clinical.Diagnosis;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.dto.EncounterDiagnosisDTO;
import com.medchart.ehr.dto.EncounterPatientDTO;
import com.medchart.ehr.dto.EncounterProviderDTO;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Explicit allowlist mapping so encounter responses never serialize JPA entities
 * (and with them Patient.ssn and other PHI).
 */
@Component
public class EncounterMapper {

    public EncounterDTO toDto(Encounter encounter) {
        EncounterDTO dto = toSummaryDto(encounter);
        if (dto != null) {
            dto.setDiagnoses(toDiagnosisDtos(encounter.getDiagnoses()));
        }
        return dto;
    }

    /** List views skip the lazy diagnoses collection to avoid per-row queries. */
    public EncounterDTO toSummaryDto(Encounter encounter) {
        if (encounter == null) {
            return null;
        }
        return EncounterDTO.builder()
                .id(encounter.getId())
                .encounterNumber(encounter.getEncounterNumber())
                .patient(toPatientDto(encounter.getPatient()))
                .attendingProvider(toProviderDto(encounter.getAttendingProvider()))
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
        return encounters.stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    public Page<EncounterDTO> toDtoPage(Page<Encounter> encounters) {
        return encounters.map(this::toSummaryDto);
    }

    EncounterPatientDTO toPatientDto(Patient patient) {
        if (patient == null) {
            return null;
        }
        return EncounterPatientDTO.builder()
                .id(patient.getId())
                .mrn(patient.getMrn())
                .firstName(patient.getFirstName())
                .middleName(patient.getMiddleName())
                .lastName(patient.getLastName())
                .build();
    }

    EncounterProviderDTO toProviderDto(Provider provider) {
        if (provider == null) {
            return null;
        }
        return EncounterProviderDTO.builder()
                .id(provider.getId())
                .npi(provider.getNpi())
                .firstName(provider.getFirstName())
                .lastName(provider.getLastName())
                .credentials(provider.getCredentials())
                .specialty(provider.getSpecialty())
                .department(provider.getDepartment())
                .build();
    }

    List<EncounterDiagnosisDTO> toDiagnosisDtos(List<EncounterDiagnosis> diagnoses) {
        if (diagnoses == null) {
            return Collections.emptyList();
        }
        return diagnoses.stream().map(this::toDiagnosisDto).collect(Collectors.toList());
    }

    EncounterDiagnosisDTO toDiagnosisDto(EncounterDiagnosis encounterDiagnosis) {
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
