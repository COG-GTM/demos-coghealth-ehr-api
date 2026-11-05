package com.medchart.ehr.service.chronic;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import com.medchart.ehr.domain.chronic.ChronicConditionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChronicMedicationServiceTest {

    private final ChronicMedicationService service = new ChronicMedicationService();

    @Test
    @DisplayName("BR-CHR-03: chronic enrollment, condition reads and care-gap reads are audited")
    void brChr03_chronicAccessAudited() throws Exception {
        AuditAccess enroll = ChronicMedicationService.class.getMethod("enrollPatient", Long.class,
                ChronicConditionType.class, String.class, Long.class).getAnnotation(AuditAccess.class);
        AuditAccess read = ChronicMedicationService.class.getMethod("getPatientConditions", Long.class)
                .getAnnotation(AuditAccess.class);
        AuditAccess gaps = ChronicMedicationService.class.getMethod("identifyCareGaps", Long.class, Long.class)
                .getAnnotation(AuditAccess.class);
        assertEquals(AuditAction.CREATE, enroll.action());
        assertEquals("ChronicCondition", enroll.resourceType());
        assertEquals(AuditAction.READ, read.action());
        assertEquals(AuditAction.READ, gaps.action());
        assertEquals("CareGap", gaps.resourceType());
    }

    @Test
    @DisplayName("BR-CHR-04: unimplemented chronic-care operations throw UnsupportedOperationException")
    void brChr04_chronicStubsThrow() {
        ChronicConditionType type = ChronicConditionType.values()[0];
        assertThrows(UnsupportedOperationException.class, () -> service.enrollPatient(1L, type, "E11.9", 2L));
        assertThrows(UnsupportedOperationException.class, () -> service.getPatientConditions(1L));
        assertThrows(UnsupportedOperationException.class, () -> service.identifyCareGaps(1L, 2L));
        assertThrows(UnsupportedOperationException.class,
                () -> service.updateAdherenceFromPharmacy(1L, 2L, new ChronicMedicationService.PharmacyFillData()));
    }

    @Test
    @DisplayName("BR-CHR-10: care gaps carry type, priority (HIGH/MEDIUM/LOW), recommendation and due date")
    void brChr10_careGapCarrier() {
        ChronicMedicationService.CareGap gap = ChronicMedicationService.CareGap.builder()
                .gapType("HBA1C_OVERDUE").description("No HbA1c in 6 months").priority("HIGH")
                .recommendation("Order HbA1c").dueDate(LocalDate.of(2026, 2, 1)).build();
        assertEquals("HIGH", gap.getPriority());
        assertEquals("HBA1C_OVERDUE", gap.getGapType());
        assertEquals(LocalDate.of(2026, 2, 1), gap.getDueDate());

        ChronicMedicationService.PharmacyFillData fill = new ChronicMedicationService.PharmacyFillData();
        fill.setDaysSupply(30);
        fill.setFillDate(LocalDate.of(2026, 1, 1));
        fill.setNdc("00000-0000-00");
        assertEquals(30, fill.getDaysSupply());
        assertEquals("00000-0000-00", fill.getNdc());
    }
}
