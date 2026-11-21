package com.medchart.ehr.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PatientAccessLogger")
class PatientAccessLoggerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private PatientAccessLogger patientAccessLogger;

    @Nested
    @DisplayName("logAccess")
    class LogAccess {

        @Test
        @DisplayName("should log successful access with all required fields")
        void shouldLogAccessWithAllFields() {
            patientAccessLogger.logAccess(
                    100L, "Physician", 1L, "MRN001",
                    AuditAction.READ, "Patient",
                    "Routine checkup", "192.168.1.100", "session123");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getUserId()).isEqualTo("100");
            assertThat(event.getUserName()).isEqualTo("Physician");
            assertThat(event.getPatientId()).isEqualTo(1L);
            assertThat(event.getPatientMrn()).isEqualTo("MRN001");
            assertThat(event.getAction()).isEqualTo(AuditAction.READ);
            assertThat(event.getResourceType()).isEqualTo("Patient");
            assertThat(event.getDescription()).isEqualTo("Routine checkup");
            assertThat(event.getIpAddress()).isEqualTo("192.168.1.100");
            assertThat(event.getSessionId()).isEqualTo("session123");
            assertThat(event.getSuccess()).isTrue();
            assertThat(event.getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("should log access for different resource types")
        void shouldLogAccessForDifferentResourceTypes() {
            patientAccessLogger.logAccess(
                    100L, "Nurse", 1L, "MRN001",
                    AuditAction.READ, "Medication",
                    "View medication list", "10.0.0.1", "session456");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());
            assertThat(captor.getValue().getResourceType()).isEqualTo("Medication");
        }
    }

    @Nested
    @DisplayName("logFailedAccess")
    class LogFailedAccess {

        @Test
        @DisplayName("should log failed access attempt")
        void shouldLogFailedAccess() {
            patientAccessLogger.logFailedAccess(
                    200L, "Intern", 1L,
                    AuditAction.ACCESS_DENIED, "Patient",
                    "No relationship with patient", "192.168.1.200");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getUserId()).isEqualTo("200");
            assertThat(event.getAction()).isEqualTo(AuditAction.ACCESS_DENIED);
            assertThat(event.getSuccess()).isFalse();
            assertThat(event.getDescription()).isEqualTo("No relationship with patient");
        }
    }

    @Nested
    @DisplayName("logBulkAccess")
    class LogBulkAccess {

        @Test
        @DisplayName("should log bulk access with record count")
        void shouldLogBulkAccessWithRecordCount() {
            patientAccessLogger.logBulkAccess(
                    100L, "Admin", AuditAction.EXPORT,
                    "Patient", 500, "Monthly compliance report",
                    "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.EXPORT);
            assertThat(event.getDescription()).contains("BULK: 500 records");
            assertThat(event.getDescription()).contains("Monthly compliance report");
            assertThat(event.getSuccess()).isTrue();
        }
    }

    @Nested
    @DisplayName("generateAccessReport")
    class GenerateAccessReport {

        @Test
        @DisplayName("should generate report with required fields")
        void shouldGenerateReportWithRequiredFields() {
            LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 3, 31, 23, 59);

            Map<String, Object> report = patientAccessLogger.generateAccessReport(1L, start, end);

            assertThat(report).containsEntry("patientId", 1L);
            assertThat(report).containsEntry("startDate", start);
            assertThat(report).containsEntry("endDate", end);
            assertThat(report).containsKey("generatedAt");
            assertThat(report).containsKey("totalAccesses");
            assertThat(report).containsKey("uniqueUsers");
        }
    }
}
