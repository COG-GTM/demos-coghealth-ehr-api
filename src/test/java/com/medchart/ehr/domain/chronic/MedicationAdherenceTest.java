package com.medchart.ehr.domain.chronic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MedicationAdherenceTest {

    @Test
    void nullPdcScoreIsUnknown() {
        assertEquals(MedicationAdherence.AdherenceStatus.UNKNOWN, statusFor(null));
    }

    @ParameterizedTest
    @CsvSource({
            "1.0000, ADHERENT",
            "0.8100, ADHERENT",
            "0.80, ADHERENT",
            "0.8000, ADHERENT",
            "0.7999, PARTIALLY_ADHERENT",
            "0.6500, PARTIALLY_ADHERENT",
            "0.50, PARTIALLY_ADHERENT",
            "0.5000, PARTIALLY_ADHERENT",
            "0.4999, NON_ADHERENT",
            "0.0000, NON_ADHERENT"
    })
    void pdcScoreMapsToStatus(String pdcScore, MedicationAdherence.AdherenceStatus expected) {
        assertEquals(expected, statusFor(new BigDecimal(pdcScore)));
    }

    @Test
    void updateRecalculatesStatus() {
        MedicationAdherence adherence = new MedicationAdherence();
        adherence.setPdcScore(new BigDecimal("0.90"));
        adherence.onCreate();
        assertEquals(MedicationAdherence.AdherenceStatus.ADHERENT, adherence.getAdherenceStatus());

        adherence.setPdcScore(new BigDecimal("0.40"));
        adherence.onUpdate();
        assertEquals(MedicationAdherence.AdherenceStatus.NON_ADHERENT, adherence.getAdherenceStatus());
        assertNotNull(adherence.getUpdatedAt());
    }

    private static MedicationAdherence.AdherenceStatus statusFor(BigDecimal pdcScore) {
        MedicationAdherence adherence = new MedicationAdherence();
        adherence.setPdcScore(pdcScore);
        adherence.onCreate();
        return adherence.getAdherenceStatus();
    }
}
