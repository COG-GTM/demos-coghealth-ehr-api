package com.medchart.ehr.controller;

import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.AuditEvent;
import com.medchart.ehr.audit.AuditService;
import com.medchart.ehr.audit.EncounterAccessAuditor;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.EmergencyContact;
import com.medchart.ehr.domain.patient.IdentifierType;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.patient.PatientIdentifier;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.mapper.EncounterMapper;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class EncounterControllerTest {

    private static final String SSN = "123-45-6789";

    private EncounterService encounterService;
    private AuditService auditService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        encounterService = mock(EncounterService.class);
        auditService = mock(AuditService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EncounterController(encounterService, new EncounterMapper(),
                        new EncounterAccessAuditor(auditService)))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getByIdReturnsPatientSummaryWithoutSsnOrDemographics() throws Exception {
        when(encounterService.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter()));

        mockMvc.perform(get("/v1/encounters/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000101"))
                .andExpect(jsonPath("$.patient.id").value(7))
                .andExpect(jsonPath("$.patient.mrn").value("MRN0000007"))
                .andExpect(jsonPath("$.patient.lastName").value("Simpson"))
                .andExpect(jsonPath("$.patient.ssn").doesNotExist())
                .andExpect(jsonPath("$.patient.dateOfBirth").doesNotExist())
                .andExpect(jsonPath("$.patient.address").doesNotExist())
                .andExpect(jsonPath("$.patient.phoneHome").doesNotExist())
                .andExpect(jsonPath("$.patient.race").doesNotExist())
                .andExpect(jsonPath("$.patient.identifiers").doesNotExist())
                .andExpect(jsonPath("$.attendingProvider.npi").value("1234567890"))
                .andExpect(jsonPath("$.attendingProvider.email").doesNotExist())
                .andExpect(jsonPath("$.attendingProvider.phoneMobile").doesNotExist())
                .andExpect(content().string(not(containsString("ssn"))))
                .andExpect(content().string(not(containsString(SSN))))
                .andExpect(content().string(not(containsString("742 Evergreen"))));
    }

    @Test
    void listEndpointsNeverSerializeSsn() throws Exception {
        List<Encounter> encounters = Collections.singletonList(encounter());
        when(encounterService.findByPatientId(7L)).thenReturn(encounters);
        when(encounterService.findByPatientId(eq(7L), any(Pageable.class))).thenReturn(new PageImpl<>(encounters));
        when(encounterService.findByProviderId(3L)).thenReturn(encounters);
        when(encounterService.getProviderSchedule(3L, LocalDate.of(2026, 10, 1))).thenReturn(encounters);
        when(encounterService.findByStatus(EncounterStatus.SCHEDULED)).thenReturn(encounters);
        when(encounterService.findByDateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))).thenReturn(encounters);
        when(encounterService.findByEncounterNumber("ENC-2026-000101")).thenReturn(Optional.of(encounter()));

        String[] urls = {
                "/v1/encounters/number/ENC-2026-000101",
                "/v1/encounters/patient/7",
                "/v1/encounters/patient/7/paged",
                "/v1/encounters/provider/3",
                "/v1/encounters/provider/3/schedule?date=2026-10-01",
                "/v1/encounters/status/SCHEDULED",
                "/v1/encounters/date-range?startDate=2026-10-01&endDate=2026-10-31",
        };
        for (String url : urls) {
            mockMvc.perform(get(url))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("MRN0000007")))
                    .andExpect(content().string(not(containsString("ssn"))))
                    .andExpect(content().string(not(containsString(SSN))));
        }
    }

    @Test
    void readsAreAuditedAgainstTheEncounterPatient() throws Exception {
        when(encounterService.findByIdWithDetails(1L)).thenReturn(Optional.of(encounter()));
        when(encounterService.findByPatientId(7L)).thenReturn(Collections.singletonList(encounter()));
        when(encounterService.findByStatus(EncounterStatus.SCHEDULED)).thenReturn(Collections.singletonList(encounter()));

        mockMvc.perform(get("/v1/encounters/1")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/encounters/patient/7")).andExpect(status().isOk());
        mockMvc.perform(get("/v1/encounters/status/SCHEDULED")).andExpect(status().isOk());

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(3)).saveAuditEventAsync(events.capture());
        AuditEvent detail = events.getAllValues().get(0);
        assertThat(detail.getAction()).isEqualTo(AuditAction.READ);
        assertThat(detail.getResourceType()).isEqualTo("Encounter");
        assertThat(detail.getPatientId()).isEqualTo(7L);
        assertThat(detail.getResourceId()).isEqualTo(1L);
        assertThat(events.getAllValues().get(1).getAction()).isEqualTo(AuditAction.READ);
        assertThat(events.getAllValues().get(1).getPatientId()).isEqualTo(7L);
        AuditEvent search = events.getAllValues().get(2);
        assertThat(search.getAction()).isEqualTo(AuditAction.SEARCH);
        assertThat(search.getPatientId()).isEqualTo(7L);
        assertThat(search.getDescription()).contains("1 of 1 encounters");
        assertThat(events.getAllValues()).allSatisfy(e ->
                assertThat(String.valueOf(e.getDescription())).doesNotContain(SSN));
    }

    @Test
    void crossPatientListsLogOneEventPerPatient() throws Exception {
        Encounter other = encounter();
        other.setId(2L);
        Patient otherPatient = Patient.builder().id(8L).mrn("MRN0000008").firstName("Marge").lastName("Simpson").build();
        other.setPatient(otherPatient);
        when(encounterService.findByStatus(EncounterStatus.SCHEDULED)).thenReturn(Arrays.asList(encounter(), other));

        mockMvc.perform(get("/v1/encounters/status/SCHEDULED")).andExpect(status().isOk());

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(2)).saveAuditEventAsync(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEvent::getPatientId).containsExactly(7L, 8L);
        assertThat(events.getAllValues()).extracting(AuditEvent::getAction).containsOnly(AuditAction.SEARCH);
    }

    @Test
    void statusTransitionsAreAudited() throws Exception {
        when(encounterService.checkIn(1L)).thenReturn(Optional.of(encounter()));
        when(encounterService.markNoShow(1L)).thenReturn(Optional.of(encounter()));
        when(encounterService.cancelEncounter(2L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/v1/encounters/2/cancel")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/encounters/1/check-in")).andExpect(status().isOk());
        mockMvc.perform(post("/v1/encounters/1/no-show")).andExpect(status().isOk());

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditService, times(2)).saveAuditEventAsync(events.capture());
        assertThat(events.getAllValues()).extracting(AuditEvent::getAction).containsOnly(AuditAction.UPDATE);
        assertThat(events.getAllValues()).extracting(AuditEvent::getPatientId).containsOnly(7L);
        assertThat(events.getAllValues()).extracting(AuditEvent::getDescription)
                .containsExactly("Check in encounter", "Mark encounter no-show");
    }

    @Test
    void dateRangeLongerThanNinetyDaysIsRejected() throws Exception {
        mockMvc.perform(get("/v1/encounters/date-range?startDate=1900-01-01&endDate=2100-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/v1/encounters/date-range?startDate=2026-01-01&endDate=2026-04-01"))
                .andExpect(status().isBadRequest());

        verify(encounterService, never()).findByDateRange(any(), any());
    }

    @Test
    void dateRangeWithEndBeforeStartIsRejected() throws Exception {
        mockMvc.perform(get("/v1/encounters/date-range?startDate=2026-10-31&endDate=2026-10-01"))
                .andExpect(status().isBadRequest());

        verify(encounterService, never()).findByDateRange(any(), any());
    }

    @Test
    void dateRangeOfNinetyDaysIsAllowed() throws Exception {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(EncounterController.MAX_DATE_RANGE_DAYS - 1);
        when(encounterService.findByDateRange(start, end)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/v1/encounters/date-range")
                        .param("startDate", start.toString())
                        .param("endDate", end.toString()))
                .andExpect(status().isOk());
    }

    private static Encounter encounter() {
        Patient patient = Patient.builder()
                .id(7L)
                .mrn("MRN0000007")
                .ssn(SSN)
                .firstName("Homer")
                .lastName("Simpson")
                .dateOfBirth(LocalDate.of(1956, 5, 12))
                .phoneHome("555-0100")
                .race("Other")
                .address(Address.builder().street1("742 Evergreen Terrace").city("Springfield").build())
                .build();
        patient.addIdentifier(PatientIdentifier.builder()
                .identifierType(IdentifierType.MRN)
                .identifierValue("DL-998877")
                .build());
        patient.addEmergencyContact(EmergencyContact.builder().firstName("Marge").lastName("Simpson").build());

        Provider provider = Provider.builder()
                .id(3L)
                .npi("1234567890")
                .firstName("Julius")
                .lastName("Hibbert")
                .email("hibbert@example.org")
                .phoneMobile("555-0199")
                .build();

        return Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000101")
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.values()[0])
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 10, 1, 9, 30))
                .chiefComplaint("Follow-up")
                .build();
    }
}
