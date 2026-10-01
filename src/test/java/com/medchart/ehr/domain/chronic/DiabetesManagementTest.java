package com.medchart.ehr.domain.chronic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiabetesManagementTest {

    private static DiabetesManagement withHba1c(BigDecimal value) {
        DiabetesManagement management = new DiabetesManagement();
        management.setLastHba1cValue(value);
        management.onCreate();
        return management;
    }

    @Test
    void nullHba1cIsUnknown() {
        assertEquals(DiabetesManagement.ControlStatus.UNKNOWN, withHba1c(null).getHba1cControlStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "4.0, CONTROLLED",
            "6.9, CONTROLLED",
            "7.0, CONTROLLED",
            "7.00, CONTROLLED",
            "7.1, SUBOPTIMAL",
            "8.5, SUBOPTIMAL",
            "9.0, SUBOPTIMAL",
            "9.00, SUBOPTIMAL",
            "9.1, UNCONTROLLED",
            "14.0, UNCONTROLLED"
    })
    void classifiesHba1cAgainstControlThresholds(String hba1c, DiabetesManagement.ControlStatus expected) {
        assertEquals(expected, withHba1c(new BigDecimal(hba1c)).getHba1cControlStatus());
    }

    @Test
    void updateRecalculatesControlStatus() {
        DiabetesManagement management = withHba1c(new BigDecimal("6.5"));
        assertEquals(DiabetesManagement.ControlStatus.CONTROLLED, management.getHba1cControlStatus());

        management.setLastHba1cValue(new BigDecimal("10.2"));
        management.onUpdate();

        assertEquals(DiabetesManagement.ControlStatus.UNCONTROLLED, management.getHba1cControlStatus());
    }

    @Test
    void eyeExamIsOverdueWhenNeverDoneOrOlderThanOneYear() {
        DiabetesManagement management = new DiabetesManagement();
        assertTrue(management.isEyeExamOverdue());

        management.setLastEyeExamDate(LocalDate.now().minusYears(1).plusDays(1));
        assertFalse(management.isEyeExamOverdue());

        management.setLastEyeExamDate(LocalDate.now().minusYears(1));
        assertFalse(management.isEyeExamOverdue());

        management.setLastEyeExamDate(LocalDate.now().minusYears(1).minusDays(1));
        assertTrue(management.isEyeExamOverdue());
    }

    @Test
    void footExamIsOverdueWhenNeverDoneOrOlderThanThreeMonths() {
        DiabetesManagement management = new DiabetesManagement();
        assertTrue(management.isFootExamOverdue());

        management.setLastFootExamDate(LocalDate.now().minusMonths(3).plusDays(1));
        assertFalse(management.isFootExamOverdue());

        management.setLastFootExamDate(LocalDate.now().minusMonths(3));
        assertFalse(management.isFootExamOverdue());

        management.setLastFootExamDate(LocalDate.now().minusMonths(3).minusDays(1));
        assertTrue(management.isFootExamOverdue());
    }

    @Test
    void nephropathyScreenIsOverdueWhenNeverDoneOrOlderThanOneYear() {
        DiabetesManagement management = new DiabetesManagement();
        assertTrue(management.isNephropathyScreenOverdue());

        management.setLastNephropathyScreenDate(LocalDate.now().minusYears(1).plusDays(1));
        assertFalse(management.isNephropathyScreenOverdue());

        management.setLastNephropathyScreenDate(LocalDate.now().minusYears(1));
        assertFalse(management.isNephropathyScreenOverdue());

        management.setLastNephropathyScreenDate(LocalDate.now().minusYears(1).minusDays(1));
        assertTrue(management.isNephropathyScreenOverdue());
    }
}
