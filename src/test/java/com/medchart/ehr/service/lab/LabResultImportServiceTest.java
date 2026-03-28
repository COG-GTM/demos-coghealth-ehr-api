package com.medchart.ehr.service.lab;

import com.medchart.ehr.domain.order.*;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.domain.provider.Provider;
import com.medchart.ehr.dto.LabResultImportResponse;
import com.medchart.ehr.repository.*;
import com.medchart.ehr.service.ProviderNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabResultImportServiceTest {

    @Mock private HL7ResultParser hl7Parser;
    @Mock private CsvResultParser csvParser;
    @Mock private LabOrderRepository labOrderRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private LabResultImportRepository importRepository;
    @Mock private UnmatchedLabResultRepository unmatchedRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private ProviderNotificationService notificationService;

    @InjectMocks
    private LabResultImportService importService;

    private Patient testPatient;
    private Provider testProvider;
    private LabOrder testOrder;

    @BeforeEach
    void setUp() {
        testPatient = Patient.builder()
                .id(1L)
                .mrn("MRN-2019-00001")
                .firstName("John")
                .lastName("Smith")
                .build();

        testProvider = new Provider();
        testProvider.setId(1L);
        testProvider.setEmail("dr.chen@medchart.local");

        testOrder = LabOrder.builder()
                .id(1L)
                .orderNumber("ORD-001")
                .patient(testPatient)
                .orderingProvider(testProvider)
                .testCode("GLU")
                .testName("Glucose")
                .status(OrderStatus.PENDING)
                .build();
    }

    @Test
    void importCsvWithMatchedResults() {
        String csvContent = "test_code,result_status,value\nGLU,FINAL,95\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "results.csv", "text/csv", csvContent.getBytes());

        when(importRepository.save(any(LabResultImport.class)))
                .thenAnswer(inv -> {
                    LabResultImport record = inv.getArgument(0);
                    record.setId(1L);
                    return record;
                });

        com.medchart.ehr.dto.ParsedLabResult parsed = com.medchart.ehr.dto.ParsedLabResult.builder()
                .patientIdentifier("MRN-2019-00001")
                .orderNumber("ORD-001")
                .testCode("GLU")
                .testName("Glucose")
                .value("95")
                .resultStatus("FINAL")
                .build();

        when(csvParser.parse(anyString())).thenReturn(List.of(parsed));
        when(labOrderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(testOrder));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));

        LabResultImportResponse response = importService.importFile(file);

        assertNotNull(response);
        assertEquals(1, response.getTotalResults());
        assertEquals(1, response.getMatchedCount());
        assertEquals(0, response.getUnmatchedCount());
        assertEquals(ImportStatus.COMPLETED, response.getStatus());

        verify(labResultRepository).save(any(LabResult.class));
    }

    @Test
    void importCsvWithUnmatchedResults() {
        String csvContent = "test_code,result_status,value\nXYZ,FINAL,100\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "results.csv", "text/csv", csvContent.getBytes());

        when(importRepository.save(any(LabResultImport.class)))
                .thenAnswer(inv -> {
                    LabResultImport record = inv.getArgument(0);
                    record.setId(1L);
                    return record;
                });

        com.medchart.ehr.dto.ParsedLabResult parsed = com.medchart.ehr.dto.ParsedLabResult.builder()
                .testCode("XYZ")
                .value("100")
                .resultStatus("FINAL")
                .build();

        when(csvParser.parse(anyString())).thenReturn(List.of(parsed));
        when(unmatchedRepository.save(any(UnmatchedLabResult.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        LabResultImportResponse response = importService.importFile(file);

        assertNotNull(response);
        assertEquals(1, response.getTotalResults());
        assertEquals(0, response.getMatchedCount());
        assertEquals(1, response.getUnmatchedCount());

        verify(unmatchedRepository).save(any(UnmatchedLabResult.class));
    }

    @Test
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        assertThrows(IllegalArgumentException.class,
                () -> importService.importFile(file));
    }

    @Test
    void rejectsUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "results.pdf", "application/pdf", "content".getBytes());

        assertThrows(IllegalArgumentException.class,
                () -> importService.importFile(file));
    }

    @Test
    void rejectsOversizedFile() {
        byte[] largeContent = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.csv", "text/csv", largeContent);

        assertThrows(IllegalArgumentException.class,
                () -> importService.importFile(file));
    }

    @Test
    void matchesByMrnAndTestCodeWhenOrderNumberNotFound() {
        String csvContent = "test_code,result_status,value\nGLU,FINAL,95\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "results.csv", "text/csv", csvContent.getBytes());

        when(importRepository.save(any(LabResultImport.class)))
                .thenAnswer(inv -> {
                    LabResultImport record = inv.getArgument(0);
                    record.setId(1L);
                    return record;
                });

        com.medchart.ehr.dto.ParsedLabResult parsed = com.medchart.ehr.dto.ParsedLabResult.builder()
                .patientIdentifier("MRN-2019-00001")
                .orderNumber("NONEXISTENT")
                .testCode("GLU")
                .value("95")
                .resultStatus("FINAL")
                .build();

        when(csvParser.parse(anyString())).thenReturn(List.of(parsed));
        when(labOrderRepository.findByOrderNumber("NONEXISTENT")).thenReturn(Optional.empty());
        when(labOrderRepository.findByPatientMrnAndTestCodeAndStatusIn(
                eq("MRN-2019-00001"), eq("GLU"), anyList()))
                .thenReturn(List.of(testOrder));
        when(labResultRepository.save(any(LabResult.class))).thenAnswer(inv -> inv.getArgument(0));

        LabResultImportResponse response = importService.importFile(file);

        assertEquals(1, response.getMatchedCount());
        verify(labOrderRepository).findByPatientMrnAndTestCodeAndStatusIn(
                eq("MRN-2019-00001"), eq("GLU"), anyList());
    }

    @Test
    void usesHl7ParserForHl7Files() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "results.hl7", "application/hl7", "MSH|content".getBytes());

        when(importRepository.save(any(LabResultImport.class)))
                .thenAnswer(inv -> {
                    LabResultImport record = inv.getArgument(0);
                    record.setId(1L);
                    return record;
                });
        when(hl7Parser.parse(anyString())).thenReturn(List.of());

        LabResultImportResponse response = importService.importFile(file);

        verify(hl7Parser).parse(anyString());
        verify(csvParser, never()).parse(anyString());
        assertEquals(ImportStatus.COMPLETED, response.getStatus());
    }
}
