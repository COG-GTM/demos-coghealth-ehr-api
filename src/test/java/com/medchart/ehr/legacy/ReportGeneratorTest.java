package com.medchart.ehr.legacy;

import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.insurance.CoverageOrder;
import com.medchart.ehr.domain.insurance.CoverageType;
import com.medchart.ehr.domain.insurance.InsuranceCoverage;
import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ReportGenerator.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:report-generator-test;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class ReportGeneratorTest {

    private static final Path REPORT_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "report-generator-test");

    private static final LocalDateTime RANGE_START = LocalDateTime.of(2024, 3, 1, 0, 0);
    private static final LocalDateTime RANGE_END = LocalDateTime.of(2024, 3, 31, 23, 59);

    @DynamicPropertySource
    static void reportProperties(DynamicPropertyRegistry registry) throws IOException {
        Files.createDirectories(REPORT_DIR);
        registry.add("medchart.reports.temp-dir", REPORT_DIR::toString);
    }

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReportGenerator reportGenerator;

    private Provider provider;

    @BeforeEach
    void seedProvider() {
        provider = entityManager.persist(Provider.builder()
                .npi("1234567890")
                .firstName("Alice")
                .lastName("Reyes")
                .providerType(ProviderType.PHYSICIAN)
                .build());
    }

    @Test
    void patientRosterEscapesCommasAndKeepsColumnCount() throws IOException {
        Patient patient = persistPatient("MRN-001", "Ann", "Smith, Jr.");
        entityManager.persist(InsuranceCoverage.builder()
                .patient(patient)
                .payerName("Blue Cross, Inc.")
                .payerId("BCBS")
                .memberId("MEM-001")
                .coverageType(CoverageType.MEDICAL)
                .coverageOrder(CoverageOrder.PRIMARY)
                .effectiveDate(LocalDate.of(2024, 1, 1))
                .active(true)
                .build());
        entityManager.flush();

        List<String> lines = Files.readAllLines(Paths.get(reportGenerator.generatePatientRoster()));

        int columnCount = lines.get(0).split(",", -1).length;
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1).split(",", -1)).hasSize(columnCount);
        assertThat(lines.get(1)).contains("Smith; Jr.").contains("Blue Cross; Inc.");
    }

    @Test
    void patientRosterSkipsInactivePatientsAndInactiveCoverage() throws IOException {
        Patient inactivePatient = persistPatient("MRN-002", "Bob", "Stone");
        inactivePatient.setActive(false);
        Patient activePatient = persistPatient("MRN-003", "Cara", "Lopez");
        entityManager.persist(InsuranceCoverage.builder()
                .patient(activePatient)
                .payerName("Aetna")
                .payerId("AET")
                .memberId("MEM-003")
                .coverageType(CoverageType.MEDICAL)
                .coverageOrder(CoverageOrder.PRIMARY)
                .effectiveDate(LocalDate.of(2024, 1, 1))
                .active(false)
                .build());
        entityManager.flush();

        List<String> lines = Files.readAllLines(Paths.get(reportGenerator.generatePatientRoster()));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).contains("MRN-003").doesNotContain("Aetna", "MEM-003");
    }

    @Test
    void patientRosterFailsWhenTempDirectoryIsNotWritable() {
        ReportGenerator generator = new ReportGenerator();
        ReflectionTestUtils.setField(generator, "entityManager", entityManager.getEntityManager());
        ReflectionTestUtils.setField(generator, "tempDir",
                REPORT_DIR.resolve("missing-subdirectory").toString());

        assertThatThrownBy(generator::generatePatientRoster)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Report generation failed")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void encounterSummaryIncludesBothBetweenBoundariesAndExcludesOutsideRange() throws IOException {
        Patient patient = persistPatient("MRN-004", "Dana", "Quinn");
        persistEncounter(patient, "ENC-START", RANGE_START);
        persistEncounter(patient, "ENC-END", RANGE_END);
        persistEncounter(patient, "ENC-BEFORE", RANGE_START.minusNanos(1_000_000));
        persistEncounter(patient, "ENC-AFTER", RANGE_END.plusNanos(1_000_000));
        entityManager.flush();

        String summary = Files.readString(Paths.get(
                reportGenerator.generateEncounterSummary(RANGE_START, RANGE_END)));

        assertThat(summary).contains("Total Encounters: 2");
        assertThat(summary).contains("Encounter: ENC-START", "Encounter: ENC-END");
        assertThat(summary).doesNotContain("ENC-BEFORE", "ENC-AFTER");
    }

    @Test
    void dailyReportReturnsBytesOfTheRosterFile() throws IOException {
        persistPatient("MRN-005", "Erin", "Vale");
        entityManager.flush();

        byte[] daily = reportGenerator.generateDailyReport();
        byte[] roster = Files.readAllBytes(Paths.get(reportGenerator.generatePatientRoster()));

        assertThat(daily).isEqualTo(roster);
    }

    private Patient persistPatient(String mrn, String firstName, String lastName) {
        return entityManager.persist(Patient.builder()
                .mrn(mrn)
                .ssn("000-00-0000")
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(LocalDate.of(1980, 5, 17))
                .phoneHome("555-0100")
                .phoneMobile("555-0101")
                .email(firstName.toLowerCase() + "@example.test")
                .address(Address.builder()
                        .street1("1 Main St, Apt 2")
                        .city("Springfield")
                        .state("IL")
                        .zipCode("62701")
                        .build())
                .active(true)
                .build());
    }

    private void persistEncounter(Patient patient, String encounterNumber, LocalDateTime dateTime) {
        entityManager.persist(Encounter.builder()
                .encounterNumber(encounterNumber)
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.OFFICE_VISIT)
                .status(EncounterStatus.COMPLETED)
                .encounterDateTime(dateTime)
                .build());
    }
}
