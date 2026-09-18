package com.medchart.ehr.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PatientDTOTest {

    @Test
    void getFullNameIncludesMiddleNameWhenPresent() {
        PatientDTO patient = PatientDTO.builder()
                .firstName("Ada")
                .middleName("Marie")
                .lastName("Lovelace")
                .build();

        assertEquals("Ada Marie Lovelace", patient.getFullName());
    }

    @Test
    void getFullNameOmitsMiddleNameWhenNullOrBlank() {
        PatientDTO withoutMiddleName = PatientDTO.builder()
                .firstName("Ada")
                .lastName("Lovelace")
                .build();
        PatientDTO blankMiddleName = PatientDTO.builder()
                .firstName("Ada")
                .middleName("   ")
                .lastName("Lovelace")
                .build();

        assertEquals("Ada Lovelace", withoutMiddleName.getFullName());
        assertEquals("Ada Lovelace", blankMiddleName.getFullName());
    }

    @Test
    void getAgeReturnsNullWhenDateOfBirthMissing() {
        assertNull(PatientDTO.builder().firstName("Ada").lastName("Lovelace").build().getAge());
    }

    @Test
    void getAgeReturnsCompletedYears() {
        LocalDate today = LocalDate.now();
        PatientDTO justTurnedForty = PatientDTO.builder().dateOfBirth(today.minusYears(40)).build();
        PatientDTO almostForty = PatientDTO.builder().dateOfBirth(today.minusYears(40).plusDays(1)).build();

        assertEquals(40, justTurnedForty.getAge());
        assertEquals(39, almostForty.getAge());
    }
}
