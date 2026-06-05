package com.medchart.ehr.export;

import com.medchart.ehr.audit.PatientAccessLogger;
import com.medchart.ehr.domain.patient.Patient;
import com.medchart.ehr.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchPatientExportServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private DataExportRepository dataExportRepository;
    @Mock
    private PatientAccessLogger patientAccessLogger;

    private ExportEncryptionService encryptionService;

    @InjectMocks
    private BatchPatientExportService service;

    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) i;
        }
        encryptionService = new ExportEncryptionService(Base64.getEncoder().encodeToString(key));

        service = new BatchPatientExportService(
                patientRepository,
                new DeIdentificationService(),
                encryptionService,
                dataExportRepository,
                patientAccessLogger);

        tempDir = Files.createTempDirectory("export-test");
        ReflectionTestUtils.setField(service, "tempDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "ttlHours", 24);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createThenDownload_roundTripsToDecryptedContent() {
        Patient patient = Patient.builder()
                .mrn("MRN001").firstName("John").lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 1)).active(true).build();
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient));

        BatchExportRequest request = new BatchExportRequest();
        request.setPatientIds(List.of(1L));
        request.setFormat(ExportFormat.CSV);
        request.setReason(ExportReason.CLINICAL);

        DataExportDTO dto = service.createExport(request, "alice", "Alice", "127.0.0.1");

        // capture the persisted export so download can find it
        ArgumentCaptor<DataExport> captor = ArgumentCaptor.forClass(DataExport.class);
        verify(dataExportRepository).save(captor.capture());
        DataExport saved = captor.getValue();
        when(dataExportRepository.findByExportReference(dto.getExportReference()))
                .thenReturn(Optional.of(saved));

        ExportDownload download = service.downloadExport(
                dto.getExportReference(), "alice", "Alice", false, "127.0.0.1");

        String content = new String(download.getContent(), StandardCharsets.UTF_8);
        assertTrue(content.contains("MRN001"));
        assertTrue(content.contains("John"));
        assertEquals(ExportFormat.CSV, download.getFormat());
    }

    @Test
    void downloadExport_deniesNonOwnerNonAdmin() {
        DataExport export = DataExport.builder()
                .exportReference("ref-1").userId("owner").patientCount(1)
                .format(ExportFormat.CSV).build();
        when(dataExportRepository.findByExportReference("ref-1"))
                .thenReturn(Optional.of(export));

        assertThrows(ExportAccessDeniedException.class, () ->
                service.downloadExport("ref-1", "intruder", "Intruder", false, "127.0.0.1"));
        verify(patientAccessLogger).logExport(eq("intruder"), eq("Intruder"), any(),
                anyInt(), contains("DENIED"), any(), any(), eq(false));
    }

    @Test
    void downloadExport_allowsAdminForOtherUsersExport() throws IOException {
        byte[] encrypted = encryptionService.encrypt("data".getBytes(StandardCharsets.UTF_8));
        Path file = tempDir.resolve("ref-2.csv.enc");
        Files.write(file, encrypted);

        DataExport export = DataExport.builder()
                .exportReference("ref-2").userId("owner").userName("Owner").patientCount(1)
                .format(ExportFormat.CSV).filePath(file.toString())
                .deleted(false).expiresAt(LocalDate.now().plusDays(1).atStartOfDay())
                .downloadCount(0).build();
        when(dataExportRepository.findByExportReference("ref-2"))
                .thenReturn(Optional.of(export));

        ExportDownload download = service.downloadExport("ref-2", "admin", "Admin", true, "127.0.0.1");

        assertEquals("data", new String(download.getContent(), StandardCharsets.UTF_8));
    }

    @Test
    void createExport_downloadUrlIncludesContextPath() {
        ReflectionTestUtils.setField(service, "contextPath", "/api");
        Patient patient = Patient.builder()
                .mrn("MRN001").firstName("John").lastName("Doe")
                .dateOfBirth(LocalDate.of(1980, 1, 1)).active(true).build();
        when(patientRepository.findAllById(any())).thenReturn(List.of(patient));

        BatchExportRequest request = new BatchExportRequest();
        request.setPatientIds(List.of(1L));
        request.setFormat(ExportFormat.CSV);
        request.setReason(ExportReason.CLINICAL);

        DataExportDTO dto = service.createExport(request, "alice", "ROLE_PROVIDER", "127.0.0.1");

        assertEquals("/api/v1/exports/" + dto.getExportReference() + "/download",
                dto.getDownloadUrl());
    }

    @Test
    void getExportHistory_adminSeesAll_nonAdminSeesOwn() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<DataExport> empty = new PageImpl<>(List.of());
        when(dataExportRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(empty);
        when(dataExportRepository.findByUserIdOrderByCreatedAtDesc("bob", pageable)).thenReturn(empty);

        service.getExportHistory("admin", true, pageable);
        service.getExportHistory("bob", false, pageable);

        verify(dataExportRepository).findAllByOrderByCreatedAtDesc(pageable);
        verify(dataExportRepository).findByUserIdOrderByCreatedAtDesc("bob", pageable);
    }
}
