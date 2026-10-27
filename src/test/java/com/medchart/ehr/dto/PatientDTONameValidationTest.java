package com.medchart.ehr.dto;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Test
    void acceptsOrdinaryNames() {
        assertTrue(violations("Mary-Jane", "Ann", "O'Brien-Smith, Jr.").isEmpty());
    }

    @Test
    void rejectsLineBreaksAndControlCharactersInNames() {
        assertEquals(Set.of("lastName"), violations("Mallory", null, "Doe\n999,Fake"));
        assertEquals(Set.of("firstName"), violations("Mal\rlory", null, "Doe"));
        assertEquals(Set.of("middleName"), violations("Mallory", "A\u0000", "Doe"));
        assertEquals(Set.of("lastName"), violations("Mallory", null, "Doe\u2028Row"));
        assertEquals(Set.of("lastName"), violations("Mallory", null, "\t=1+1"));
    }

    private static Set<String> violations(String first, String middle, String last) {
        PatientDTO dto = PatientDTO.builder().firstName(first).middleName(middle).lastName(last).build();
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}
