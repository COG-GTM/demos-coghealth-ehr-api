package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterDTO;
import com.medchart.ehr.dto.EncounterPatientDTO;
import com.medchart.ehr.dto.EncounterProviderDTO;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Explicit allowlist mapping so encounter responses never serialize JPA entities
 * (and with them Patient.ssn and other PHI).
 */
@Component
public class EncounterMapper {

    public EncounterDTO toDto(Encounter encounter) {
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
                .build();
    }

    public List<EncounterDTO> toDtoList(List<Encounter> encounters) {
        return encounters.stream().map(this::toDto).collect(Collectors.toList());
    }

    public Page<EncounterDTO> toDtoPage(Page<Encounter> encounters) {
        return encounters.map(this::toDto);
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
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
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
}
