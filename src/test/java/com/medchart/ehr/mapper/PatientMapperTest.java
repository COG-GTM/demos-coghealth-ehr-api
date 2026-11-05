package com.medchart.ehr.mapper;

import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.EmergencyContact;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.IdentifierType;
import com.medchart.ehr.domain.patient.MaritalStatus;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.patient.PatientIdentifier;
import com.medchart.ehr.dto.AddressDTO;
import com.medchart.ehr.dto.EmergencyContactDTO;
import com.medchart.ehr.dto.PatientDTO;
import com.medchart.ehr.dto.PatientIdentifierDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientMapperTest {

    private final PatientMapper mapper = Mappers.getMapper(PatientMapper.class);

    private static Patient fullPatient() {
        Patient p = new Patient();
        p.setId(42L);
        p.setMrn("MRN-TEST-1");
        p.setSsn("000-00-0000");
        p.setFirstName("Test");
        p.setMiddleName("M");
        p.setLastName("Patient");
        p.setDateOfBirth(LocalDate.of(1980, 1, 1));
        p.setGender(Gender.FEMALE);
        p.setMaritalStatus(MaritalStatus.SINGLE);
        p.setEmail("t@example.test");
        p.setPhoneHome("555-0100");
        p.setPhoneMobile("555-0101");
        p.setPhoneWork("555-0102");
        p.setAddress(Address.builder().street1("1 Test St").city("Testville").state("NY").zipCode("00000").build());
        p.setMailingAddress(Address.builder().street1("PO Box 1").build());
        p.setPreferredLanguage("en");
        p.setEthnicity("E");
        p.setRace("R");
        p.setReligion("N");
        PatientIdentifier id = new PatientIdentifier();
        id.setIdentifierType(IdentifierType.values()[0]);
        id.setIdentifierValue("ID-1");
        id.setIssuingAuthority("Auth");
        id.setEffectiveDate(LocalDate.of(2020, 1, 1));
        id.setExpirationDate(LocalDate.of(2030, 1, 1));
        id.setActive(true);
        id.setPatient(p);
        p.setIdentifiers(new ArrayList<>(List.of(id)));
        EmergencyContact ec = new EmergencyContact();
        ec.setFirstName("Contact");
        ec.setLastName("Person");
        ec.setRelationship("Sibling");
        ec.setPhoneMobile("555-0199");
        ec.setPriority(1);
        ec.setAddress(Address.builder().city("Testville").build());
        ec.setPatient(p);
        p.setEmergencyContacts(new ArrayList<>(List.of(ec)));
        p.setDeceased(false);
        p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        p.setUpdatedAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        return p;
    }

    @Test
    @DisplayName("BR-MAP-12: the patient DTO never exposes the SSN")
    void brMap12_dtoHasNoSsn() {
        assertFalse(Arrays.stream(PatientDTO.class.getDeclaredFields()).map(Field::getName)
                .anyMatch(n -> n.toLowerCase().contains("ssn")));
    }

    @Test
    @DisplayName("BR-MAP-12: entity -> DTO copies demographics, contacts, identifiers and emergency contacts")
    void brMap12_toDto() {
        PatientDTO dto = mapper.toDto(fullPatient());
        assertEquals(42L, dto.getId());
        assertEquals("MRN-TEST-1", dto.getMrn());
        assertEquals("Patient", dto.getLastName());
        assertEquals(Gender.FEMALE, dto.getGender());
        assertEquals(MaritalStatus.SINGLE, dto.getMaritalStatus());
        assertEquals("555-0102", dto.getPhoneWork());
        assertEquals("Testville", dto.getAddress().getCity());
        assertEquals("PO Box 1", dto.getMailingAddress().getStreet1());
        assertEquals("ID-1", dto.getIdentifiers().get(0).getIdentifierValue());
        assertEquals("Auth", dto.getIdentifiers().get(0).getIssuingAuthority());
        assertEquals("Sibling", dto.getEmergencyContacts().get(0).getRelationship());
        assertEquals("Testville", dto.getEmergencyContacts().get(0).getAddress().getCity());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), dto.getCreatedAt());
        assertEquals(1, mapper.toDtoList(List.of(fullPatient(), fullPatient())).size() - 1);
    }

    @Test
    @DisplayName("BR-MAP-12: DTO -> entity never trusts client-supplied id, timestamps or version")
    void brMap12_toEntityIgnoresServerFields() {
        PatientDTO dto = mapper.toDto(fullPatient());
        dto.setUpdatedAt(LocalDateTime.now());

        Patient e = mapper.toEntity(dto);

        assertNull(e.getId());
        assertNull(e.getCreatedAt());
        assertNull(e.getUpdatedAt());
        assertNull(e.getVersion());
        assertNull(e.getSsn());
        assertEquals("MRN-TEST-1", e.getMrn());
        assertEquals("NY", e.getAddress().getState());
        assertEquals("PO Box 1", e.getMailingAddress().getStreet1());
        assertEquals("ID-1", e.getIdentifiers().get(0).getIdentifierValue());
        assertNull(e.getIdentifiers().get(0).getPatient());
        assertEquals("Contact", e.getEmergencyContacts().get(0).getFirstName());
        assertNull(e.getEmergencyContacts().get(0).getPatient());
    }

    @Test
    @DisplayName("BR-MAP-13: updates are partial - null DTO properties never overwrite stored values")
    void brMap13_partialUpdate() {
        Patient stored = fullPatient();
        PatientDTO patch = PatientDTO.builder().id(999L).firstName("Renamed").build();

        mapper.updateEntityFromDto(patch, stored);

        assertEquals("Renamed", stored.getFirstName());
        assertEquals(42L, stored.getId());
        assertEquals("Patient", stored.getLastName());
        assertEquals("000-00-0000", stored.getSsn());
        assertEquals("Testville", stored.getAddress().getCity());
        assertEquals(1, stored.getIdentifiers().size());
        assertEquals(1, stored.getEmergencyContacts().size());
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), stored.getCreatedAt());
    }

    @Test
    @DisplayName("BR-MAP-13: a full update replaces addresses and collections from the DTO")
    void brMap13_fullUpdate() {
        Patient stored = fullPatient();
        PatientDTO dto = mapper.toDto(fullPatient());
        dto.setLastName("Updated");
        dto.getAddress().setCity("Newtown");
        dto.setMailingAddress(AddressDTO.builder().street1("PO Box 2").build());
        dto.setIdentifiers(new ArrayList<>(List.of(new PatientIdentifierDTO(), new PatientIdentifierDTO())));
        dto.setEmergencyContacts(new ArrayList<>(List.of(new EmergencyContactDTO(), new EmergencyContactDTO())));

        mapper.updateEntityFromDto(dto, stored);
        assertEquals("Updated", stored.getLastName());
        assertEquals("Newtown", stored.getAddress().getCity());
        assertEquals("PO Box 2", stored.getMailingAddress().getStreet1());
        assertEquals(2, stored.getIdentifiers().size());
        assertEquals(2, stored.getEmergencyContacts().size());

        Patient bare = new Patient();
        bare.setIdentifiers(null);
        bare.setEmergencyContacts(null);
        mapper.updateEntityFromDto(dto, bare);
        assertEquals("Newtown", bare.getAddress().getCity());
        assertEquals(2, bare.getIdentifiers().size());
        assertEquals(2, bare.getEmergencyContacts().size());
    }

    @Test
    @DisplayName("BR-MAP-14: null inputs map to null for every conversion")
    void brMap14_nullsMapToNull() {
        assertNull(mapper.toDto(null));
        assertNull(mapper.toEntity(null));
        assertNull(mapper.toAddressDto(null));
        assertNull(mapper.toAddressEntity(null));
        assertNull(mapper.toIdentifierDto(null));
        assertNull(mapper.toIdentifierEntity(null));
        assertNull(mapper.toEmergencyContactDto(null));
        assertNull(mapper.toEmergencyContactEntity(null));
        assertNull(mapper.toDtoList(null));
        Patient p = new Patient();
        mapper.updateEntityFromDto(null, p);
        assertTrue(p.getActive());
    }

    @Test
    @DisplayName("BR-MAP-14: patients without addresses, identifiers or contacts map to empty/null nested values")
    void brMap14_sparsePatient() {
        Patient p = new Patient();
        p.setIdentifiers(null);
        p.setEmergencyContacts(null);
        PatientDTO dto = mapper.toDto(p);
        assertNull(dto.getAddress());
        assertNull(dto.getIdentifiers());
        assertNull(dto.getEmergencyContacts());
        Patient back = mapper.toEntity(new PatientDTO());
        assertNull(back.getAddress());
    }
}
