package com.medchart.ehr.legacy;

import com.medchart.ehr.legacy.InsuranceCache.CachedEligibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsuranceCacheTest {

    private InsuranceCache cache;

    @BeforeEach
    void setUp() {
        cache = new InsuranceCache();
    }

    private void cache(String mrn, String payerId) {
        cache.cacheEligibility(mrn, "000-00-0000", payerId, "MEMBER-" + mrn + "-" + payerId,
                true, "Gold PPO", "25.00", "1000.00");
    }

    @Test
    void cachedEligibilityIsRetrievedByMrnAndPayer() {
        cache.cacheEligibility("MRN001", "111-22-3333", "PAYER1", "MEMBER1",
                true, "Gold PPO", "25.00", "1000.00");

        CachedEligibility cached = cache.getEligibility("MRN001", "PAYER1");

        assertNotNull(cached);
        assertEquals("MRN001", cached.patientMrn);
        assertEquals("PAYER1", cached.payerId);
        assertEquals("MEMBER1", cached.memberId);
        assertTrue(cached.eligible);
        assertEquals("Gold PPO", cached.planName);
        assertEquals("25.00", cached.copay);
        assertEquals("1000.00", cached.deductible);
        assertNotNull(cached.cachedAt);
    }

    @Test
    void getEligibilityReturnsNullForDifferentPayer() {
        cache("MRN001", "PAYER1");

        assertNull(cache.getEligibility("MRN001", "PAYER2"));
    }

    @Test
    void getEligibilityReturnsNullForUnknownMrn() {
        cache("MRN001", "PAYER1");

        assertNull(cache.getEligibility("MRN002", "PAYER1"));
    }

    @Test
    void entriesForDifferentPayersOfSamePatientAreKeptSeparate() {
        cache("MRN001", "PAYER1");
        cache("MRN001", "PAYER2");

        assertEquals(2, cache.getCacheSize());
        assertEquals("MEMBER-MRN001-PAYER1", cache.getEligibility("MRN001", "PAYER1").memberId);
        assertEquals("MEMBER-MRN001-PAYER2", cache.getEligibility("MRN001", "PAYER2").memberId);
    }

    @Test
    void recachingSameMrnAndPayerOverwritesEntry() {
        cache.cacheEligibility("MRN001", "111-22-3333", "PAYER1", "MEMBER1",
                true, "Gold PPO", "25.00", "1000.00");
        cache.cacheEligibility("MRN001", "111-22-3333", "PAYER1", "MEMBER2",
                false, "Bronze HMO", "50.00", "5000.00");

        assertEquals(1, cache.getCacheSize());
        CachedEligibility cached = cache.getEligibility("MRN001", "PAYER1");
        assertEquals("MEMBER2", cached.memberId);
        assertEquals("Bronze HMO", cached.planName);
        assertFalse(cached.eligible);
    }

    @Test
    void clearPatientCacheRemovesOnlyThatPatientsEntries() {
        cache("MRN001", "PAYER1");
        cache("MRN001", "PAYER2");
        cache("MRN002", "PAYER1");

        cache.clearPatientCache("MRN001");

        assertEquals(1, cache.getCacheSize());
        assertNull(cache.getEligibility("MRN001", "PAYER1"));
        assertNull(cache.getEligibility("MRN001", "PAYER2"));
        assertNotNull(cache.getEligibility("MRN002", "PAYER1"));
    }

    @Test
    void clearPatientCacheLeavesPrefixSharingMrnIntact() {
        cache("1", "PAYER1");
        cache("12", "PAYER1");

        cache.clearPatientCache("1");

        assertEquals(1, cache.getCacheSize());
        assertNull(cache.getEligibility("1", "PAYER1"));
        assertNotNull(cache.getEligibility("12", "PAYER1"));
    }

    @Test
    void clearPatientCacheForUnknownMrnLeavesCacheUnchanged() {
        cache("MRN001", "PAYER1");

        cache.clearPatientCache("MRN999");

        assertEquals(1, cache.getCacheSize());
        assertNotNull(cache.getEligibility("MRN001", "PAYER1"));
    }

    @Test
    void clearAllCacheEmptiesTheCache() {
        cache("MRN001", "PAYER1");
        cache("MRN002", "PAYER2");
        assertEquals(2, cache.getCacheSize());

        cache.clearAllCache();

        assertEquals(0, cache.getCacheSize());
        assertNull(cache.getEligibility("MRN001", "PAYER1"));
    }

    @Test
    void cacheSizeStartsAtZero() {
        assertEquals(0, cache.getCacheSize());
    }
}
