package com.medchart.ehr.dto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatientDTONameValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+cmd", "-2+3", "@SUM(A1)", "Ann\nBob", "Ann\rBob", "Ann\tBob", "Ann\u0000"})
    void rejectsFormulaAndControlCharacterNames(String name) {
        assertEquals(1, validator.validateProperty(patient(name, "Doe", null), "firstName").size());
        assertEquals(1, validator.validateProperty(patient("Ann", name, null), "lastName").size());
        assertEquals(1, validator.validateProperty(patient("Ann", "Doe", name), "middleName").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mary-Jane", "O'Brien", "José", "Smith, Jr.", "D'Angelo-Pérez"})
    void acceptsOrdinaryNames(String name) {
        assertTrue(validator.validate(patient(name, name, name)).isEmpty());
    }

    private static PatientDTO patient(String first, String last, String middle) {
        return PatientDTO.builder().firstName(first).lastName(last).middleName(middle).build();
    }
}
