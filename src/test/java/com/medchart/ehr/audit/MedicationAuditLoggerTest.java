package com.medchart.ehr.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationAuditLogger")
class MedicationAuditLoggerTest {

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private MedicationAuditLogger medicationAuditLogger;

    @Nested
    @DisplayName("logPrescriptionCreated")
    class LogPrescriptionCreated {

        @Test
        @DisplayName("should log non-controlled prescription")
        void shouldLogNonControlledPrescription() {
            medicationAuditLogger.logPrescriptionCreated(
                    100L, "Physician", 1L, "MRN001",
                    500L, "Amoxicillin", false, null, "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(event.getResourceType()).isEqualTo("MedicationOrder");
            assertThat(event.getResourceId()).isEqualTo(500L);
            assertThat(event.getDescription()).contains("Amoxicillin");
            assertThat(event.getDescription()).doesNotContain("Schedule");
        }

        @Test
        @DisplayName("should log controlled substance prescription with schedule")
        void shouldLogControlledSubstancePrescription() {
            medicationAuditLogger.logPrescriptionCreated(
                    100L, "Physician", 1L, "MRN001",
                    501L, "Oxycodone", true, "II", "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getDescription()).contains("Oxycodone");
            assertThat(event.getDescription()).contains("Schedule II");
            assertThat(event.getPatientMrn()).isEqualTo("MRN001");
        }
    }

    @Nested
    @DisplayName("logPrescriptionModified")
    class LogPrescriptionModified {

        @Test
        @DisplayName("should log prescription modification with change details")
        void shouldLogModificationWithChangeDetails() {
            medicationAuditLogger.logPrescriptionModified(
                    100L, "Physician", 1L, "MRN001",
                    500L, "Lisinopril", "dosage",
                    "10mg", "20mg", "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.UPDATE);
            assertThat(event.getDescription()).contains("Lisinopril");
            assertThat(event.getDescription()).contains("dosage");
            assertThat(event.getDescription()).contains("10mg");
            assertThat(event.getDescription()).contains("20mg");
        }
    }

    @Nested
    @DisplayName("logPrescriptionCancelled")
    class LogPrescriptionCancelled {

        @Test
        @DisplayName("should log prescription cancellation with reason")
        void shouldLogCancellationWithReason() {
            medicationAuditLogger.logPrescriptionCancelled(
                    100L, "Physician", 1L, "MRN001",
                    500L, "Metformin", "Patient allergic reaction",
                    "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.DELETE);
            assertThat(event.getDescription()).contains("Metformin");
            assertThat(event.getDescription()).contains("Patient allergic reaction");
        }
    }

    @Nested
    @DisplayName("logEprescribeTransaction")
    class LogEprescribeTransaction {

        @Test
        @DisplayName("should log successful e-prescribe transaction")
        void shouldLogSuccessfulEprescribe() {
            medicationAuditLogger.logEprescribeTransaction(
                    100L, "Physician", 1L, "MRN001",
                    500L, "NewRx", "NCPDP123", "SUCCESS", "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.EXPORT);
            assertThat(event.getDescription()).contains("NewRx");
            assertThat(event.getDescription()).contains("NCPDP123");
            assertThat(event.getSuccess()).isTrue();
        }

        @Test
        @DisplayName("should log failed e-prescribe transaction")
        void shouldLogFailedEprescribe() {
            medicationAuditLogger.logEprescribeTransaction(
                    100L, "Physician", 1L, "MRN001",
                    500L, "NewRx", "NCPDP123", "REJECTED", "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("logPdmpCheck")
    class LogPdmpCheck {

        @Test
        @DisplayName("should log PDMP check with state")
        void shouldLogPdmpCheckWithState() {
            medicationAuditLogger.logPdmpCheck(
                    100L, "Physician", 1L, "MRN001",
                    "CA", "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.READ);
            assertThat(event.getResourceType()).isEqualTo("Patient");
            assertThat(event.getDescription()).contains("PDMP check");
            assertThat(event.getDescription()).contains("CA");
        }
    }

    @Nested
    @DisplayName("logMedicationHistoryAccess")
    class LogMedicationHistoryAccess {

        @Test
        @DisplayName("should log medication history access with record count")
        void shouldLogMedicationHistoryAccess() {
            medicationAuditLogger.logMedicationHistoryAccess(
                    100L, "Physician", 1L, "MRN001",
                    "SureScripts", 25, "192.168.1.100");

            ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
            verify(auditEventRepository).save(captor.capture());

            AuditEvent event = captor.getValue();
            assertThat(event.getAction()).isEqualTo(AuditAction.READ);
            assertThat(event.getResourceType()).isEqualTo("MedicationHistory");
            assertThat(event.getDescription()).contains("SureScripts");
            assertThat(event.getDescription()).contains("25 records");
        }
    }
}
