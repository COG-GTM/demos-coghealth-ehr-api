package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhiLogMaskerTest {

    @Test
    void keepsOnlyLastFourCharacters() {
        assertEquals("****LICE", PhiLogMasker.maskMrn("MRN-0042-ALICE"));
    }

    @Test
    void revealsSuffixOnlyWhenAtLeastHalfIsHidden() {
        assertEquals("****5678", PhiLogMasker.maskMrn("12345678"));
    }

    @Test
    void fullyMasksShortBlankAndNullValues() {
        assertEquals("****", PhiLogMasker.maskMrn("1234"));
        assertEquals("****", PhiLogMasker.maskMrn("12345"));
        assertEquals("****", PhiLogMasker.maskMrn("1234567"));
        assertEquals("****", PhiLogMasker.maskMrn(""));
        assertEquals("****", PhiLogMasker.maskMrn(null));
    }
}
