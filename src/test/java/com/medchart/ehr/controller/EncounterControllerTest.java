package com.medchart.ehr.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.audit.EncounterAccessAuditor;
import com.medchart.ehr.domain.clinical.Diagnosis;
import com.medchart.ehr.domain.encounter.DiagnosisRole;
import com.medchart.ehr.domain.encounter.Encounter;
import com.medchart.ehr.domain.encounter.EncounterDiagnosis;
import com.medchart.ehr.domain.encounter.EncounterStatus;
import com.medchart.ehr.domain.encounter.EncounterType;
import com.medchart.ehr.domain.patient.Address;
import com.medchart.ehr.domain.patient.Gender;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.domain.provider.ProviderType;
import com.medchart.ehr.mapper.EncounterMapper;
import com.medchart.ehr.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
    private EncounterAccessAuditor accessAuditor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        encounterService = mock(EncounterService.class);
        accessAuditor = mock(EncounterAccessAuditor.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EncounterController(encounterService, new EncounterMapper(), accessAuditor))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void getByIdReturnsPatientSummaryWithoutSsn() throws Exception {
        when(encounterService.findByIdForProvider(eq(1L), isNull())).thenReturn(Optional.of(encounter()));

        assertNoPhiLeak(mockMvc.perform(get("/v1/encounters/1")), "$")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.encounterNumber").value("ENC-2026-000101"))
                .andExpect(jsonPath("$.patient.id").value(42))
                .andExpect(jsonPath("$.patient.mrn").value("MRN0000000001"))
                .andExpect(jsonPath("$.patient.firstName").value("Jane"))
                .andExpect(jsonPath("$.patient.lastName").value("Doe"))
                .andExpect(jsonPath("$.attendingProvider.npi").value("1234567890"))
                .andExpect(jsonPath("$.attendingProvider.email").doesNotExist())
                .andExpect(jsonPath("$.attendingProvider.phoneMobile").doesNotExist())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.diagnoses[0].icd10Code").value("Z00.00"))
                .andExpect(jsonPath("$.diagnoses[0].primaryDiagnosis").value(true))
                .andExpect(jsonPath("$.diagnoses[0].patient").doesNotExist())
                .andExpect(jsonPath("$.diagnoses[0].encounter").doesNotExist());

        verify(accessAuditor).logEncounterAccess(any(Encounter.class), eq(AuditAction.READ), anyString());
    }

    @Test
    void getByIdReturns404WhenMissing() throws Exception {
        when(encounterService.findByIdForProvider(eq(99L), isNull())).thenReturn(Optional.empty());

        mockMvc.perform(get("/v1/encounters/99")).andExpect(status().isNotFound());
    }

    @Test
    void getByNumberDoesNotExposeSsn() throws Exception {
        when(encounterService.findByEncounterNumber("ENC-2026-000101")).thenReturn(Optional.of(encounter()));

        assertNoPhiLeak(mockMvc.perform(get("/v1/encounters/number/ENC-2026-000101")), "$")
                .andExpect(status().isOk());
    }

    @Test
    void listEndpointsDoNotExposeSsn() throws Exception {
        List<Encounter> encounters = List.of(encounter());
        when(encounterService.findByPatientId(42L)).thenReturn(encounters);
        when(encounterService.findByProviderId(7L)).thenReturn(encounters);
        when(encounterService.getProviderSchedule(eq(7L), any(LocalDate.class))).thenReturn(encounters);
        when(encounterService.findByDateRange(any(LocalDate.class), any(LocalDate.class))).thenReturn(encounters);
        when(encounterService.findByStatus(EncounterStatus.SCHEDULED)).thenReturn(encounters);

        String[] urls = {
                "/v1/encounters/patient/42",
                "/v1/encounters/provider/7",
                "/v1/encounters/provider/7/schedule?date=2026-10-26",
                "/v1/encounters/date-range?startDate=2026-10-01&endDate=2026-10-31",
                "/v1/encounters/status/SCHEDULED"
        };
        for (String url : urls) {
            assertNoPhiLeak(mockMvc.perform(get(url)), "$[0]")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].patient.mrn").value("MRN0000000001"))
                    .andExpect(jsonPath("$[0].diagnoses").doesNotExist());
        }
        verify(accessAuditor).logEncounterListAccess(eq(42L), anyList(), anyString());
        verify(accessAuditor).logEncounterListAccess(isNull(), anyList(), eq("List provider encounters"));
    }

    @Test
    void pagedEndpointDoesNotExposeSsn() throws Exception {
        when(encounterService.findByPatientId(eq(42L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(encounter())));

        assertNoPhiLeak(mockMvc.perform(get("/v1/encounters/patient/42/paged")), "$.content[0]")
                .andExpect(status().isOk());
    }

    @Test
    void createResponseDoesNotEchoSsn() throws Exception {
        when(encounterService.create(any(Encounter.class))).thenReturn(encounter());

        assertNoPhiLeak(mockMvc.perform(post("/v1/encounters")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patient\":{\"id\":42},\"encounterType\":\"OFFICE_VISIT\"}")), "$")
                .andExpect(status().isOk());
    }

    private static ResultActions assertNoPhiLeak(ResultActions result, String root) throws Exception {
        return result
                .andExpect(content().string(not(containsString(SSN))))
                .andExpect(jsonPath(root + ".patient.ssn").doesNotExist())
                .andExpect(jsonPath(root + ".patient.dateOfBirth").doesNotExist())
                .andExpect(jsonPath(root + ".patient.gender").doesNotExist())
                .andExpect(jsonPath(root + ".patient.identifiers").doesNotExist())
                .andExpect(jsonPath(root + ".patient.emergencyContacts").doesNotExist())
                .andExpect(jsonPath(root + ".patient.address").doesNotExist())
                .andExpect(jsonPath(root + ".patient.email").doesNotExist())
                .andExpect(jsonPath(root + ".patient.phoneMobile").doesNotExist())
                .andExpect(jsonPath(root + ".patient.race").doesNotExist())
                .andExpect(jsonPath(root + ".patient.religion").doesNotExist());
    }

    private static Encounter encounter() {
        Patient patient = Patient.builder()
                .id(42L)
                .mrn("MRN0000000001")
                .ssn(SSN)
                .firstName("Jane")
                .lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 4, 2))
                .gender(Gender.FEMALE)
                .email("jane.doe@example.com")
                .phoneMobile("555-0100")
                .race("Declined")
                .religion("Declined")
                .address(Address.builder().street1("1 Main St").city("Springfield").state("IL").zipCode("62701").build())
                .build();
        Provider provider = Provider.builder()
                .id(7L)
                .npi("1234567890")
                .firstName("Gregory")
                .lastName("House")
                .credentials("MD")
                .providerType(ProviderType.values()[0])
                .email("house@example.com")
                .phoneMobile("555-0199")
                .build();
        Encounter encounter = Encounter.builder()
                .id(1L)
                .encounterNumber("ENC-2026-000101")
                .patient(patient)
                .attendingProvider(provider)
                .encounterType(EncounterType.values()[0])
                .status(EncounterStatus.SCHEDULED)
                .encounterDateTime(LocalDateTime.of(2026, 10, 26, 9, 30))
                .chiefComplaint("Annual physical")
                .version(3L)
                .build();
        Diagnosis diagnosis = Diagnosis.builder()
                .id(11L)
                .patient(patient)
                .icd10Code("Z00.00")
                .description("General adult medical exam")
                .build();
        encounter.addDiagnosis(EncounterDiagnosis.builder()
                .id(21L)
                .diagnosis(diagnosis)
                .role(DiagnosisRole.values()[0])
                .primaryDiagnosis(true)
                .build());
        return encounter;
    }
}
