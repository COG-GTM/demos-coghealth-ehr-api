package com.medchart.ehr.persistence;

import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.PatientRepository;
import com.medchart.ehr.support.AbstractJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PatientRepositoryTest extends AbstractJpaTest {

    @Autowired
    private PatientRepository patients;

    @Autowired
    private TestEntityManager em;

    @Test
    void roundTripsPatientWithEmbeddedAddress() {
        Patient saved = patients.saveAndFlush(Patient.builder()
                .mrn("MRN-RT-000001")
                .firstName("Round")
                .lastName("Tripper")
                .dateOfBirth(LocalDate.of(1980, 6, 15))
                .address(Address.builder().street1("1 Test Way").city("Springfield").state("IL").zipCode("62701").build())
                .build());
        em.clear();

        Patient reloaded = patients.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMrn()).isEqualTo("MRN-RT-000001");
        assertThat(reloaded.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 6, 15));
        assertThat(reloaded.getAddress().getStreet1()).isEqualTo("1 Test Way");
        assertThat(reloaded.getActive()).isTrue();
        assertThat(reloaded.getDeceased()).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isZero();
    }

    @Test
    void findsSeedPatientByMrnAndSsn() {
        assertThat(patients.findByMrn("MRN-2019-00001")).get()
                .extracting(Patient::getLastName).isEqualTo("Smith");
        assertThat(patients.findBySsn("123-45-6789")).get()
                .extracting(Patient::getMrn).isEqualTo("MRN-2019-00001");
        assertThat(patients.findByMrn("MRN-DOES-NOT-EXIST")).isEmpty();
    }

    @Test
    void searchPatientsMatchesLastNameFirstNameCaseInsensitivelyAndMrnFragment() {
        assertThat(patients.searchPatients("SMIT", PageRequest.of(0, 50)).getContent())
                .extracting(Patient::getMrn).contains("MRN-2019-00001");
        assertThat(patients.searchPatients("jessi", PageRequest.of(0, 50)).getContent())
                .extracting(Patient::getMrn).contains("MRN-2021-00008");
        assertThat(patients.searchPatients("2024-0001", PageRequest.of(0, 50)).getContent())
                .extracting(Patient::getMrn).contains("MRN-2024-00014", "MRN-2024-00015");
        assertThat(patients.searchPatients("zzz-no-match", PageRequest.of(0, 50))).isEmpty();
    }

    @Test
    void searchPatientsIsPageable() {
        Page<Patient> firstPage = patients.searchPatients("MRN-20", PageRequest.of(0, 5, Sort.by("mrn")));

        assertThat(firstPage.getContent()).hasSize(5);
        assertThat(firstPage.getTotalElements()).isGreaterThanOrEqualTo(15);
        assertThat(firstPage.getContent().get(0).getMrn()).isEqualTo("MRN-2019-00001");
    }

    @Test
    void derivedQueries() {
        assertThat(patients.findByLastNameContainingIgnoreCase("JOHN", PageRequest.of(0, 10)).getContent())
                .extracting(Patient::getMrn).contains("MRN-2019-00003");
        assertThat(patients.findByDateOfBirth(LocalDate.of(1965, 3, 15)))
                .extracting(Patient::getMrn).contains("MRN-2019-00001");
        assertThat(patients.findByLastNameAndDob("Smith", LocalDate.of(1965, 3, 15)))
                .extracting(Patient::getMrn).contains("MRN-2019-00001");
    }

    @Test
    void activePatientQueriesExcludeInactive() {
        long before = patients.countActivePatients();
        assertThat(patients.findByActiveTrue()).extracting(Patient::getMrn)
                .contains("MRN-2019-00001")
                .doesNotContain("MRN-2019-00013");

        patients.saveAndFlush(Patient.builder().mrn("MRN-RT-000002").firstName("A").lastName("B")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).build());
        patients.saveAndFlush(Patient.builder().mrn("MRN-RT-000003").firstName("C").lastName("D")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).active(false).build());

        assertThat(patients.countActivePatients()).isEqualTo(before + 1);
    }
}
