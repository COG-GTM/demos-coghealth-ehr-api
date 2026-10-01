package com.medchart.ehr.dto;

import com.medchart.ehr.domain.patient.Gender;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PatientDTOTest {

    @Test
    void getFullName_withMiddleName_includesMiddleName() {
        PatientDTO dto = PatientDTO.builder()
                .firstName("John")
                .middleName("Michael")
                .lastName("Doe")
                .build();

        assertEquals("John Michael Doe", dto.getFullName());
    }

    @Test
    void getFullName_withoutMiddleName_excludesMiddleName() {
        PatientDTO dto = PatientDTO.builder()
                .firstName("John")
                .lastName("Doe")
                .build();

        assertEquals("John Doe", dto.getFullName());
    }

    @Test
    void getFullName_withBlankMiddleName_excludesMiddleName() {
        PatientDTO dto = PatientDTO.builder()
                .firstName("John")
                .middleName("   ")
                .lastName("Doe")
                .build();

        assertEquals("John Doe", dto.getFullName());
    }

    @Test
    void getAge_withDateOfBirth_returnsAge() {
        PatientDTO dto = PatientDTO.builder()
                .dateOfBirth(LocalDate.now().minusYears(35))
                .build();

        assertEquals(35, dto.getAge());
    }

    @Test
    void getAge_withNullDateOfBirth_returnsNull() {
        PatientDTO dto = PatientDTO.builder().build();

        assertNull(dto.getAge());
    }

    @Test
    void builder_setsAllFields() {
        PatientDTO dto = PatientDTO.builder()
                .id(1L)
                .mrn("MRN001")
                .firstName("John")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender(Gender.MALE)
                .email("john@example.com")
                .phoneHome("555-1234")
                .phoneMobile("555-5678")
                .active(true)
                .deceased(false)
                .build();

        assertEquals(1L, dto.getId());
        assertEquals("MRN001", dto.getMrn());
        assertEquals("John", dto.getFirstName());
        assertEquals("Doe", dto.getLastName());
        assertEquals(LocalDate.of(1990, 1, 15), dto.getDateOfBirth());
        assertEquals(Gender.MALE, dto.getGender());
        assertEquals("john@example.com", dto.getEmail());
        assertEquals("555-1234", dto.getPhoneHome());
        assertEquals("555-5678", dto.getPhoneMobile());
        assertTrue(dto.getActive());
        assertFalse(dto.getDeceased());
    }

    @Test
    void getAge_childPatient_returnsCorrectAge() {
        PatientDTO dto = PatientDTO.builder()
                .dateOfBirth(LocalDate.now().minusYears(2))
                .build();

        assertEquals(2, dto.getAge());
    }
}
