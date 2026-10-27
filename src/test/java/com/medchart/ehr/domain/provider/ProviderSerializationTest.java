package com.medchart.ehr.domain.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.medchart.ehr.domain.clinical.ClinicalNote;
import com.medchart.ehr.domain.clinical.Diagnosis;
import com.medchart.ehr.domain.encounter.Encounter;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderSerializationTest {

    private static final String[] RESTRICTED = {"phoneMobile", "pager", "licenses", "hireDate", "terminationDate",
            "createdAt", "updatedAt", "version"};

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static Provider provider() {
        License license = new License();
        license.setLicenseType("MD");
        license.setLicenseNumber("A123456");
        license.setState("CA");
        return Provider.builder()
                .id(12L)
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Chen")
                .phoneOffice("555-0100")
                .phoneMobile("555-0199")
                .pager("555-0142")
                .hireDate(LocalDate.of(2015, 7, 1))
                .licenses(Set.of(license))
                .version(3L)
                .build();
    }

    @Test
    void nestedProviderOmitsPersonalAndLicenseFieldsOnEveryEntity() {
        Encounter encounter = new Encounter();
        encounter.setAttendingProvider(provider());
        Diagnosis diagnosis = new Diagnosis();
        diagnosis.setDiagnosedBy(provider());
        ClinicalNote note = new ClinicalNote();
        note.setAuthor(provider());

        assertRedacted(mapper.valueToTree(encounter).get("attendingProvider"));
        assertRedacted(mapper.valueToTree(diagnosis).get("diagnosedBy"));
        assertRedacted(mapper.valueToTree(note).get("author"));
    }

    @Test
    void restrictedFieldsAreIgnoredOnDeserialization() throws Exception {
        Provider parsed = mapper.readValue(
                "{\"npi\":\"1234567890\",\"phoneMobile\":\"555-0199\",\"version\":9,"
                        + "\"licenses\":[{\"licenseNumber\":\"X\"}]}", Provider.class);
        assertThat(parsed.getNpi()).isEqualTo("1234567890");
        assertThat(parsed.getPhoneMobile()).isNull();
        assertThat(parsed.getVersion()).isNull();
        assertThat(parsed.getLicenses()).isEmpty();
    }

    private static void assertRedacted(JsonNode node) {
        assertThat(node).isNotNull();
        assertThat(node.get("npi").asText()).isEqualTo("1234567890");
        assertThat(node.get("phoneOffice").asText()).isEqualTo("555-0100");
        for (String field : RESTRICTED) {
            assertThat(node.has(field)).as(field).isFalse();
        }
        assertThat(node.toString()).doesNotContain("A123456", "555-0199", "555-0142");
    }
}
