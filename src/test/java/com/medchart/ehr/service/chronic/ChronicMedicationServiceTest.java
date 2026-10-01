package com.medchart.ehr.service.chronic;

import com.medchart.ehr.domain.chronic.ChronicConditionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ChronicMedicationServiceTest {

    @InjectMocks
    private ChronicMedicationService chronicMedicationService;

    @Test
    void enrollPatient_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> chronicMedicationService.enrollPatient(
                1L, ChronicConditionType.DIABETES_TYPE_2, "E11.9", 10L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not yet implemented");
    }

    @Test
    void getPatientConditions_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> chronicMedicationService.getPatientConditions(1L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not yet implemented");
    }

    @Test
    void identifyCareGaps_throwsUnsupportedOperationException() {
        assertThatThrownBy(() -> chronicMedicationService.identifyCareGaps(1L, 100L))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not yet implemented");
    }

    @Test
    void updateAdherenceFromPharmacy_throwsUnsupportedOperationException() {
        ChronicMedicationService.PharmacyFillData fillData = new ChronicMedicationService.PharmacyFillData();
        fillData.setRxNumber("RX001");

        assertThatThrownBy(() -> chronicMedicationService.updateAdherenceFromPharmacy(1L, 200L, fillData))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Not yet implemented");
    }
}
