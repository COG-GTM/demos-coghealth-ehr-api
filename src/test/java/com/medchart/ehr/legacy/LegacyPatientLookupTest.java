package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LegacyPatientLookup.class)
class LegacyPatientLookupTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private LegacyPatientLookup lookup;

    private Patient alice;

    @BeforeEach
    void seedPatients() {
        alice = persistPatient("MRN-1001", "111-22-3333", "Alice", "Andersson");
        persistPatient("MRN-1002", "444-55-6666", "Bob", "Andersson");
    }

    @Test
    void findPatientByMrnReturnsSeededPatient() {
        Patient found = lookup.findPatientByMrn("MRN-1001");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(alice.getId());
        assertThat(found.getLastName()).isEqualTo("Andersson");
    }

    @Test
    void findPatientByMrnReturnsNullForUnknownMrn() {
        assertThat(lookup.findPatientByMrn("MRN-DOES-NOT-EXIST")).isNull();
    }

    @Test
    void findPatientBySsnReturnsSeededPatientAndNullForUnknownSsn() {
        assertThat(lookup.findPatientBySsn("444-55-6666")).isNotNull();
        assertThat(lookup.findPatientBySsn("000-00-0000")).isNull();
    }

    @Test
    void findPatientsByLastNameMatchesSubstringCaseInsensitively() {
        List<Patient> found = lookup.findPatientsByLastName("andersS");

        assertThat(found).hasSize(2);
    }

    @Test
    void getPatientDemographicsMapsEveryColumnToItsOwnKey() {
        Map<String, Object> demographics = lookup.getPatientDemographics(alice.getId());

        assertThat(demographics).isNotNull();
        assertThat(demographics.get("id")).isEqualTo(alice.getId());
        assertThat(demographics.get("mrn")).isEqualTo("MRN-1001");
        assertThat(demographics.get("ssn")).isEqualTo("111-22-3333");
        assertThat(demographics.get("firstName")).isEqualTo("Alice");
        assertThat(demographics.get("lastName")).isEqualTo("Andersson");
        assertThat(demographics.get("dateOfBirth")).hasToString("1980-01-15");
        assertThat(demographics.get("phoneHome")).isEqualTo("555-0100");
        assertThat(demographics.get("phoneMobile")).isEqualTo("555-0101");
        assertThat(demographics.get("email")).isEqualTo("alice@example.com");
        assertThat(demographics.get("street")).isEqualTo("1 Main St");
        assertThat(demographics.get("city")).isEqualTo("Springfield");
        assertThat(demographics.get("state")).isEqualTo("IL");
        assertThat(demographics.get("zipCode")).isEqualTo("62701");
    }

    @Test
    void getPatientDemographicsReturnsNullForUnknownId() {
        assertThat(lookup.getPatientDemographics(-1L)).isNull();
    }

    @Test
    void searchPatientsRawMatchesFirstNameLastNameAndMrn() {
        assertThat(lookup.searchPatientsRaw("alic")).hasSize(1);
        assertThat(lookup.searchPatientsRaw("andersson")).hasSize(2);
        assertThat(lookup.searchPatientsRaw("MRN-1002")).hasSize(1);
        assertThat(lookup.searchPatientsRaw("nobody")).isEmpty();
    }

    private Patient persistPatient(String mrn, String ssn, String firstName, String lastName) {
        Patient patient = Patient.builder()
            .mrn(mrn)
            .ssn(ssn)
            .firstName(firstName)
            .lastName(lastName)
            .dateOfBirth(LocalDate.of(1980, 1, 15))
            .phoneHome("555-0100")
            .phoneMobile("555-0101")
            .email(firstName.toLowerCase() + "@example.com")
            .address(Address.builder()
                .street1("1 Main St")
                .city("Springfield")
                .state("IL")
                .zipCode("62701")
                .country("USA")
                .build())
            .build();
        Patient persisted = testEntityManager.persistAndFlush(patient);
        testEntityManager.clear();
        return persisted;
    }
}
