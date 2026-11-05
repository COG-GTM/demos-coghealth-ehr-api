package com.medchart.ehr.service.chronic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PharmacyIntegrationServiceTest {

    private final PharmacyIntegrationService service = new PharmacyIntegrationService();

    @Test
    @DisplayName("BR-CHR-04: NCPDP pharmacy operations are unimplemented stubs and never fake a pharmacy response")
    void brChr04_pharmacyStubsThrow() {
        assertThrows(UnsupportedOperationException.class, () -> service.sendPrescription(1L, "MRN-1", "0000000",
                new PharmacyIntegrationService.PrescriptionData()));
        assertThrows(UnsupportedOperationException.class,
                () -> service.checkFormulary("MRN-1", "PAYER-A", "00000-0000-00", 30));
        assertThrows(UnsupportedOperationException.class,
                () -> service.getMedicationHistory("MRN-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));
        assertThrows(UnsupportedOperationException.class, () -> service.requestRefill(1L, "0000000", 30, 30));
        assertThrows(UnsupportedOperationException.class, () -> service.cancelPrescription(1L, "0000000", "Duplicate"));
    }
}
