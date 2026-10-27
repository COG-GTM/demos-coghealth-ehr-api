package com.medchart.ehr.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhiLogMaskerTest {

    @Test
    void keepsOnlyLastFourCharacters() {
        assertThat(PhiLogMasker.maskMrn("MRN1793075723123")).isEqualTo("****3123");
    }

    @Test
    void fullyMasksShortValues() {
        assertThat(PhiLogMasker.maskMrn("MRN123")).isEqualTo("****");
    }

    @Test
    void fullyMasksSuffixWithControlCharacters() {
        assertThat(PhiLogMasker.maskMrn("MRN123456\r\n")).isEqualTo("****");
    }

    @Test
    void handlesMissingValue() {
        assertThat(PhiLogMasker.maskMrn(null)).isEqualTo("<none>");
        assertThat(PhiLogMasker.maskMrn("")).isEqualTo("<none>");
    }
}
