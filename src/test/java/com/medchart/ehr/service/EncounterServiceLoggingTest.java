package com.medchart.ehr.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.mapper.FhirPatientMapper;
import com.medchart.ehr.repository.EncounterRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EncounterServiceLoggingTest {

    private static final String MRN = "MRN-0042-ALICE";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Logger encounterLogger;
    private Logger mapperLogger;

    @BeforeEach
    void attachAppender() {
        appender.start();
        encounterLogger = (Logger) LoggerFactory.getLogger(EncounterService.class);
        mapperLogger = (Logger) LoggerFactory.getLogger(FhirPatientMapper.class);
        encounterLogger.addAppender(appender);
        mapperLogger.addAppender(appender);
        mapperLogger.setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    @AfterEach
    void detachAppender() {
        encounterLogger.detachAppender(appender);
        mapperLogger.detachAppender(appender);
    }

    @Test
    void createLogsEncounterNumberAndPatientIdButNotMrn() {
        EncounterRepository repository = mock(EncounterRepository.class);
        when(repository.save(any(Encounter.class))).thenAnswer(inv -> inv.getArgument(0));
        EncounterService service = new EncounterService(repository);

        Encounter encounter = new Encounter();
        encounter.setPatient(patient());

        Encounter saved = service.create(encounter);

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.contains(saved.getEncounterNumber()), message);
        assertTrue(message.contains("patient id 7"), message);
        assertNoMrnLogged();
    }

    @Test
    void fhirMapperDoesNotLogMrn() {
        FhirPatientMapper mapper = new FhirPatientMapper();
        Map<String, Object> resource = mapper.toFhirResource(patient());
        mapper.fromFhirResource(resource);

        assertFalse(appender.list.isEmpty());
        assertNoMrnLogged();
    }

    private void assertNoMrnLogged() {
        for (ILoggingEvent event : appender.list) {
            assertFalse(event.getFormattedMessage().contains(MRN), event.getFormattedMessage());
        }
    }

    private static Patient patient() {
        Patient patient = new Patient();
        patient.setId(7L);
        patient.setMrn(MRN);
        patient.setFirstName("Alice");
        patient.setLastName("Example");
        patient.setDateOfBirth(java.time.LocalDate.of(1980, 1, 2));
        patient.setGender(com.medchart.ehr.domain.patient.Gender.FEMALE);
        return patient;
    }
}
