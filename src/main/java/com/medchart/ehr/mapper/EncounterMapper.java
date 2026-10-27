package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.EncounterDTO;
import org.springframework.stereotype.Component;

@Component
public class EncounterMapper {

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
                .version(encounter.getVersion())
                .build();
    }

    /**
     * Copies only client-editable fields. Omitted fields are left unchanged; optional
     * fields sent as an explicit null are cleared. Required fields are never nulled.
     */
    public void applyEditableFields(EncounterDTO dto, Encounter target) {
        if (dto.getEncounterType() != null) target.setEncounterType(dto.getEncounterType());
        if (dto.getEncounterDateTime() != null) target.setEncounterDateTime(dto.getEncounterDateTime());
        if (shouldApply(dto, "admitDateTime", dto.getAdmitDateTime())) target.setAdmitDateTime(dto.getAdmitDateTime());
        if (shouldApply(dto, "dischargeDateTime", dto.getDischargeDateTime())) target.setDischargeDateTime(dto.getDischargeDateTime());
        if (shouldApply(dto, "department", dto.getDepartment())) target.setDepartment(dto.getDepartment());
        if (shouldApply(dto, "room", dto.getRoom())) target.setRoom(dto.getRoom());
        if (shouldApply(dto, "bed", dto.getBed())) target.setBed(dto.getBed());
        if (shouldApply(dto, "chiefComplaint", dto.getChiefComplaint())) target.setChiefComplaint(dto.getChiefComplaint());
        if (shouldApply(dto, "priority", dto.getPriority())) target.setPriority(dto.getPriority());
        if (shouldApply(dto, "visitType", dto.getVisitType())) target.setVisitType(dto.getVisitType());
        if (shouldApply(dto, "notes", dto.getNotes())) target.setNotes(dto.getNotes());
    }

    private static boolean shouldApply(EncounterDTO dto, String field, Object value) {
        return value != null || dto.isExplicitlySet(field);
    }

    private EncounterDTO.PatientSummary toPatientSummary(Patient patient) {
        if (patient == null) {
            return null;
        }
        return EncounterDTO.PatientSummary.builder()
                .id(patient.getId())
                .mrn(patient.getMrn())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .dateOfBirth(patient.getDateOfBirth())
                .build();
    }

    private EncounterDTO.ProviderSummary toProviderSummary(Provider provider) {
        if (provider == null) {
            return null;
        }
        return EncounterDTO.ProviderSummary.builder()
                .id(provider.getId())
                .firstName(provider.getFirstName())
                .lastName(provider.getLastName())
                .credentials(provider.getCredentials())
                .build();
    }
}
