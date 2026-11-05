package com.medchart.ehr.service.chronic;

import com.medchart.ehr.audit.AuditAccess;
import com.medchart.ehr.audit.AuditAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MedicationAdherenceTrackerTest {

    private final MedicationAdherenceTracker tracker = new MedicationAdherenceTracker();

    @Test
    @DisplayName("BR-CHR-01: PDC over an empty measurement period (same start and end day) is 0")
    void brChr01_pdcZeroForEmptyPeriod() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertEquals(BigDecimal.ZERO, tracker.calculatePdc(1L, 2L, d, d));
    }

    @Test
    @DisplayName("BR-CHR-01: PDC over a reversed measurement period is 0, not negative")
    void brChr01_pdcZeroForReversedPeriod() {
        assertEquals(BigDecimal.ZERO,
                tracker.calculatePdc(1L, 2L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 28)));
    }

    @Test
    @DisplayName("BR-CHR-02: PDC for a positive period is not yet implemented (stub) and must fail loudly")
    void brChr02_pdcPositivePeriodIsStub() {
        assertThrows(UnsupportedOperationException.class,
                () -> tracker.calculatePdc(1L, 2L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2)));
    }

    @Test
    @DisplayName("BR-CHR-03: viewing adherence and at-risk lists is audited as MedicationAdherence READ")
    void brChr03_adherenceAccessAudited() throws Exception {
        for (String name : new String[] {"getPatientAdherence", "getAtRiskPatients"}) {
            AuditAccess a = name.equals("getPatientAdherence")
                    ? MedicationAdherenceTracker.class.getMethod(name, Long.class).getAnnotation(AuditAccess.class)
                    : MedicationAdherenceTracker.class.getMethod(name).getAnnotation(AuditAccess.class);
            assertEquals(AuditAction.READ, a.action());
            assertEquals("MedicationAdherence", a.resourceType());
        }
    }

    @Test
    @DisplayName("BR-CHR-04: unimplemented adherence operations throw UnsupportedOperationException")
    void brChr04_adherenceStubsThrow() {
        assertThrows(UnsupportedOperationException.class, () -> tracker.getPatientAdherence(1L));
        assertThrows(UnsupportedOperationException.class, tracker::checkAdherenceAndAlert);
        assertThrows(UnsupportedOperationException.class,
                () -> tracker.processPharmacyFill("MRN-1", "00000-0000-00", LocalDate.now(), 30, "0000000000"));
        assertThrows(UnsupportedOperationException.class, tracker::getAtRiskPatients);
    }

    @Test
    @DisplayName("BR-CHR-10: at-risk patient records carry PDC, last fill and next fill due")
    void brChr10_atRiskPatientCarrier() {
        MedicationAdherenceTracker.AtRiskPatient p = MedicationAdherenceTracker.AtRiskPatient.builder()
                .patientId(1L).patientMrn("MRN-1").patientName("Test Patient").medicationName("Metformin")
                .currentPdc(new BigDecimal("0.49")).lastFillDate(LocalDate.of(2026, 1, 1))
                .nextFillDue(LocalDate.of(2026, 1, 31)).riskReason("PDC < 50%").build();
        assertEquals(new BigDecimal("0.49"), p.getCurrentPdc());
        assertEquals(LocalDate.of(2026, 1, 31), p.getNextFillDue());
        assertEquals("PDC < 50%", p.getRiskReason());
    }
}
