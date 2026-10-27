package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhiMaskingTest {

    @Test
    void masksAllButLastFourDigits() {
        assertEquals("***-**-6789", PhiMasking.maskSsn("123-45-6789"));
        assertEquals("***-**-6789", PhiMasking.maskSsn("123456789"));
    }

    @Test
    void handlesNullAndShortValues() {
        assertEquals("", PhiMasking.maskSsn(null));
        assertEquals("***-**-****", PhiMasking.maskSsn("12"));
    }
}
