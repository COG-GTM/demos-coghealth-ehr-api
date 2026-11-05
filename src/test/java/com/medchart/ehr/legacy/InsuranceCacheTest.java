package com.medchart.ehr.legacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsuranceCacheTest {

    private final InsuranceCache cache = new InsuranceCache();

    @Test
    @DisplayName("BR-LEG-01: eligibility is cached per patient+payer with the time it was cached")
    void brLeg01_cachePerPatientAndPayer() {
        Instant before = Instant.now();
        cache.cacheEligibility("MRN-1", null, "PAYER-A", "MEM-1", true, "Gold", "25.00", "500.00");
        cache.cacheEligibility("MRN-1", null, "PAYER-B", "MEM-2", false, "Silver", null, null);

        InsuranceCache.CachedEligibility a = cache.getEligibility("MRN-1", "PAYER-A");
        assertEquals("MEM-1", a.memberId);
        assertTrue(a.eligible);
        assertEquals("Gold", a.planName);
        assertEquals("25.00", a.copay);
        assertEquals("500.00", a.deductible);
        assertEquals("MRN-1", a.patientMrn);
        assertEquals("PAYER-A", a.payerId);
        assertNull(a.patientSsn);
        assertNotNull(a.cachedAt);
        assertFalse(a.cachedAt.isBefore(before));
        assertFalse(cache.getEligibility("MRN-1", "PAYER-B").eligible);
        assertNull(cache.getEligibility("MRN-1", "PAYER-C"));
        assertEquals(2, cache.getCacheSize());
    }

    @Test
    @DisplayName("BR-LEG-01: the cache time is an absolute instant, so TTL age is unaffected by DST transitions")
    void brLeg01_cacheTimeIsInstant() throws Exception {
        assertEquals(Instant.class, InsuranceCache.CachedEligibility.class.getField("cachedAt").getType());
    }

    @Test
    @DisplayName("BR-LEG-01: re-caching the same patient+payer replaces the previous entry")
    void brLeg01_recacheReplaces() {
        cache.cacheEligibility("MRN-1", null, "P", "M", true, "Gold", null, null);
        cache.cacheEligibility("MRN-1", null, "P", "M", false, "Gold", null, null);
        assertEquals(1, cache.getCacheSize());
        assertFalse(cache.getEligibility("MRN-1", "P").eligible);
    }

    @Test
    @DisplayName("BR-LEG-02: clearing a patient's cache removes only that patient's entries (no MRN prefix collisions)")
    void brLeg02_clearPatientOnly() {
        cache.cacheEligibility("MRN-1", null, "P", "M", true, null, null, null);
        cache.cacheEligibility("MRN-1", null, "Q", "M", true, null, null, null);
        cache.cacheEligibility("MRN-10", null, "P", "M", true, null, null, null);

        cache.clearPatientCache("MRN-1");

        assertNull(cache.getEligibility("MRN-1", "P"));
        assertNull(cache.getEligibility("MRN-1", "Q"));
        assertNotNull(cache.getEligibility("MRN-10", "P"));
        cache.clearAllCache();
        assertEquals(0, cache.getCacheSize());
    }
}
