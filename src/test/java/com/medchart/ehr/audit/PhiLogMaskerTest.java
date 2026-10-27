package com.medchart.ehr.audit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhiLogMaskerTest {

    @Test
    void keepsOnlyLastFourCharacters() {
        assertEquals("****LICE", PhiLogMasker.maskMrn("MRN-0042-ALICE"));
    }

    @Test
    void fullyMasksShortBlankAndNullValues() {
        assertEquals("****", PhiLogMasker.maskMrn("1234"));
        assertEquals("****", PhiLogMasker.maskMrn(""));
        assertEquals("****", PhiLogMasker.maskMrn(null));
    }
}
