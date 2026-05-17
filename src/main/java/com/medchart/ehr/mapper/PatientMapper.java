package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.*;
import com.medchart.ehr.dto.*;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for converting between {@link Patient} entities and
 * {@link PatientDTO} transfer objects.
 *
 * Null properties in the DTO are ignored during updates to support
 * partial-update semantics.
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface PatientMapper {

    PatientDTO toDto(Patient patient);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Patient toEntity(PatientDTO dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateEntityFromDto(PatientDTO dto, @MappingTarget Patient entity);

    AddressDTO toAddressDto(Address address);
    Address toAddressEntity(AddressDTO dto);

    PatientIdentifierDTO toIdentifierDto(PatientIdentifier identifier);
    
    @Mapping(target = "patient", ignore = true)
    PatientIdentifier toIdentifierEntity(PatientIdentifierDTO dto);

    EmergencyContactDTO toEmergencyContactDto(EmergencyContact contact);
    
    @Mapping(target = "patient", ignore = true)
    EmergencyContact toEmergencyContactEntity(EmergencyContactDTO dto);

    List<PatientDTO> toDtoList(List<Patient> patients);
}
