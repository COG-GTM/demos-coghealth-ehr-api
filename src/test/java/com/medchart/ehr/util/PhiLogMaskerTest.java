package com.medchart.ehr.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhiLogMaskerTest {

    @Test
    void revealsNoPartOfTheMrn() {
        assertThat(PhiLogMasker.maskMrn("MRN1793075723123")).isEqualTo("<redacted>");
        assertThat(PhiLogMasker.maskMrn("MRN12345")).isEqualTo("<redacted>");
        assertThat(PhiLogMasker.maskMrn("MRN123456\r\nFAKE")).isEqualTo("<redacted>");
    }

    @Test
    void handlesMissingValue() {
        assertThat(PhiLogMasker.maskMrn(null)).isEqualTo("<none>");
        assertThat(PhiLogMasker.maskMrn("")).isEqualTo("<none>");
    }
}
