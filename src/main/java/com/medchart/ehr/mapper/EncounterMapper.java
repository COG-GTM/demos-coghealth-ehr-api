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

    /** Copies only client-editable fields; null values leave the stored value unchanged. */
    public void applyEditableFields(EncounterDTO dto, Encounter target) {
        if (dto.getEncounterType() != null) target.setEncounterType(dto.getEncounterType());
        if (dto.getEncounterDateTime() != null) target.setEncounterDateTime(dto.getEncounterDateTime());
        if (dto.getAdmitDateTime() != null) target.setAdmitDateTime(dto.getAdmitDateTime());
        if (dto.getDischargeDateTime() != null) target.setDischargeDateTime(dto.getDischargeDateTime());
        if (dto.getDepartment() != null) target.setDepartment(dto.getDepartment());
        if (dto.getRoom() != null) target.setRoom(dto.getRoom());
        if (dto.getBed() != null) target.setBed(dto.getBed());
        if (dto.getChiefComplaint() != null) target.setChiefComplaint(dto.getChiefComplaint());
        if (dto.getPriority() != null) target.setPriority(dto.getPriority());
        if (dto.getVisitType() != null) target.setVisitType(dto.getVisitType());
        if (dto.getNotes() != null) target.setNotes(dto.getNotes());
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
